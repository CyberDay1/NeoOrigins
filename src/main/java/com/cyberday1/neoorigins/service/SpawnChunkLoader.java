package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Third phase of a {@code spawn_location} resolve: get the destination chunks
 * generated <b>without blocking the server thread</b>, then hand control back.
 *
 * <p><b>The problem.</b> {@code LocationCondition#refineSpawn} spirals up to 16
 * blocks around the located centre and reads block states. Every one of those
 * reads goes through {@code ServerChunkCache#getChunk(x, z, status, true)},
 * which — when called on the server thread — does
 * {@code mainThreadProcessor.managedBlock(future::isDone)}: it drives its own
 * task queue until the chunk has finished generating. Generating one chunk to
 * {@code FULL} cascades into the whole neighbour pyramid, so on a heavy
 * worldgen pack (Tectonic + Terralith) that is seconds per chunk. A user report
 * on 2026-08-01 showed a 9-second "Can't keep up!" with the teleport packet
 * landing 1 ms later, at a destination only ~545 blocks from spawn — the biome
 * spiral was trivially short, the cost was all chunk generation.
 *
 * <p><b>The fix.</b> Ask for the chunks the way the chunk system wants to be
 * asked: add a ticket and let it generate them on its own workers, then poll
 * cheaply once per server tick until they are present. The server keeps ticking
 * throughout; the player's teleport simply lands a few ticks (or a few seconds,
 * on a pathological pack) later.
 *
 * <p><b>Why not {@code getChunkFuture}?</b> Because it is not actually
 * asynchronous from the server thread. {@code ServerChunkCache#getChunkFuture}
 * branches on {@code Thread.currentThread() == this.mainThread} and, on the
 * main-thread branch, runs the same {@code managedBlock} spin as
 * {@code getChunk}. Calling it from the server thread would have bought us
 * nothing. Only the off-thread branch is non-blocking, and hopping off-thread
 * purely to request chunks is a worse trade than a ticket plus a tick poll.
 *
 * <p><b>Ticket choice.</b> A private {@link TicketType} rather than a borrowed
 * vanilla one: vanilla's own {@code SPAWN_SEARCH} and {@code UNKNOWN} time out
 * after a single tick (vanilla gets away with it only because
 * {@code getChunkFuture} immediately blocks), and {@code PORTAL} /
 * {@code ENDER_PEARL} carry unrelated semantics that another mod may reasonably
 * reason about. Ours carries {@code FLAG_LOADING} and nothing else: the chunks
 * must generate, but they must not start ticking. It is added at radius 0 on
 * each chunk we need, which {@code TicketStorage#addTicketWithRadius} turns into
 * ticket level {@code ChunkLevel.byStatus(FullChunkStatus.FULL) - 0 == 33} —
 * enough to generate and keep the chunk, not enough to make it tick. The
 * generous tick timeout is purely a leak backstop; the normal path removes the
 * ticket explicitly once the refine has run.
 *
 * <p><b>26.1 note.</b> {@code TicketType} is a plain {@code record} here, not
 * the generic value-carrying class it was on 1.21.1, and it lives in a real
 * registry ({@code BuiltInRegistries.TICKET_TYPE}). Vanilla registers its own
 * during bootstrap, which is long frozen by the time mod code runs, so ours goes
 * through a {@link DeferredRegister} wired up in the mod constructor like every
 * other registry entry in this mod.
 */
public final class SpawnChunkLoader {

    private SpawnChunkLoader() {}

    /** How a wait ended. The continuation is always invoked exactly once, on the server thread. */
    public enum Outcome {
        /** Every destination chunk is generated and present. */
        READY,
        /** Gave up waiting. The refine runs anyway, over whatever chunks are present. */
        TIMED_OUT,
        /** Caller cancelled, or the server is stopping. Do nothing. */
        ABANDONED
    }

    /**
     * Half-width, in chunks, of the destination window.
     *
     * <p>{@code findColumn} spirals 16 blocks from the centre and its land test
     * reads one block further out, so the block footprint is
     * {@code centre ± 17}. Blocks {@code centre ± 16} always land inside the 3x3
     * chunk window regardless of where in its chunk the centre sits; only the
     * outermost shell's neighbour reads can escape it, and the preloaded refine
     * skips those rather than force-loading a fourth chunk column.
     */
    private static final int WINDOW_CHUNKS =
        (com.cyberday1.neoorigins.api.condition.LocationCondition.SEARCH_RADIUS + 15) / 16;

    /**
     * Ticks we are willing to wait. 30 s at 20 TPS — long enough that a heavy
     * modpack generating nine fresh chunks finishes comfortably, short enough
     * that a player is never left staring at the world forever. This is a
     * safety net, not the expected path: generation runs in parallel on the
     * chunk system's workers, so the usual wait is a handful of ticks.
     */
    private static final int WAIT_TIMEOUT_TICKS = 600;

    /**
     * Backstop timeout baked into the ticket itself, in ticks. If our own
     * release ever fails to run (an exception path we did not foresee, a level
     * torn down underneath us) the chunks still stop being pinned a minute
     * later instead of leaking for the lifetime of the server.
     */
    private static final int TICKET_BACKSTOP_TICKS = 1200;

    /**
     * Registry plumbing, held one class down so that touching {@link #window}
     * does not drag {@code BuiltInRegistries} in behind it. The window geometry
     * is unit-tested and that test has no Minecraft bootstrap; on 1.21.1 the
     * ticket type was a free-standing object and the question never came up.
     */
    private static final class Registration {
        private Registration() {}

        static final DeferredRegister<TicketType> TICKET_TYPES =
            DeferredRegister.create(BuiltInRegistries.TICKET_TYPE, NeoOrigins.MOD_ID);

        /**
         * Our chunk ticket. {@code FLAG_LOADING} on its own is the whole intent:
         * generate and keep, do not simulate. Deliberately not
         * {@code FLAG_PERSIST} (a spawn search must never outlive the session
         * that started it) and not {@code FLAG_CAN_EXPIRE_IF_UNLOADED} (that
         * would let the backstop fire before the chunk it is waiting on has
         * finished loading at all).
         */
        static final DeferredHolder<TicketType, TicketType> SPAWN_SEARCH =
            TICKET_TYPES.register("spawn_search",
                () -> new TicketType(TICKET_BACKSTOP_TICKS, TicketType.FLAG_LOADING));
    }

    /** {@link com.cyberday1.neoorigins.NeoOrigins} calls this during mod construction. */
    public static void register(IEventBus modEventBus) {
        Registration.TICKET_TYPES.register(modEventBus);
    }

    /** Waits in progress. Server thread only — never touched from a worker. */
    private static final List<Pending> PENDING = new ArrayList<>();

    private record Pending(ServerLevel level,
                           long[] chunks,
                           ChunkPos centre,
                           int deadlineTick,
                           BooleanSupplier abandoned,
                           Consumer<Outcome> continuation) {}

    /**
     * Ensures the chunks {@code LocationCondition#refineSpawnPreloaded} will
     * read around {@code centre} are generated, then runs {@code continuation}
     * on the server thread.
     *
     * <p>Call on the server thread. When the chunks are already present the
     * continuation runs inline, before this method returns, and no ticket is
     * taken — that keeps the common "spawn chunks, already loaded" case exactly
     * as cheap as it was before.
     *
     * @param abandoned polled each tick; once true the wait is dropped and the
     *                  continuation is invoked with {@link Outcome#ABANDONED}
     */
    public static void whenReady(ServerLevel level, BlockPos centre,
                                 BooleanSupplier abandoned, Consumer<Outcome> continuation) {
        long[] chunks = window(centre);
        if (allPresent(level, chunks)) {
            continuation.accept(Outcome.READY);
            return;
        }

        ChunkPos centreChunk = ChunkPos.containing(centre);
        TicketType type = Registration.SPAWN_SEARCH.get();
        for (long packed : chunks) {
            // Radius 0 => ticket level 33 => FullChunkStatus.FULL. The chunk
            // gets generated and kept; it deliberately does not tick.
            level.getChunkSource().addTicketWithRadius(type, ChunkPos.unpack(packed), 0);
        }

        PENDING.add(new Pending(level, chunks, centreChunk,
            level.getServer().getTickCount() + WAIT_TIMEOUT_TICKS, abandoned, continuation));
    }

    /** Polls every wait in progress. Hooked to {@code ServerTickEvent.Post}. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        int now = server.getTickCount();
        // Index walk rather than an iterator: a continuation can teleport the
        // player, which can start another spawn resolve and append to PENDING.
        for (int i = 0; i < PENDING.size(); i++) {
            Pending pending = PENDING.get(i);
            Outcome outcome;
            if (pending.abandoned().getAsBoolean()) {
                outcome = Outcome.ABANDONED;
            } else if (allPresent(pending.level(), pending.chunks())) {
                outcome = Outcome.READY;
            } else if (now >= pending.deadlineTick()) {
                outcome = Outcome.TIMED_OUT;
            } else {
                continue;
            }
            PENDING.remove(i--);
            settle(pending, outcome);
        }
    }

    /** Drops every wait and releases every ticket. Called on server stop. */
    public static void shutdown() {
        List<Pending> dropped = new ArrayList<>(PENDING);
        PENDING.clear();
        for (Pending pending : dropped) settle(pending, Outcome.ABANDONED);
    }

    private static void settle(Pending pending, Outcome outcome) {
        if (outcome == Outcome.TIMED_OUT) {
            NeoOrigins.LOGGER.warn(
                "Gave up waiting {} ticks for spawn destination chunks around {} in {} — "
                    + "refining over whatever generated in time",
                WAIT_TIMEOUT_TICKS, pending.centre(), pending.level().dimension().identifier());
        }
        try {
            // The continuation reads those chunks, so release only afterwards.
            pending.continuation().accept(outcome);
        } catch (Throwable t) {
            NeoOrigins.LOGGER.error("Spawn destination continuation failed near {}", pending.centre(), t);
        } finally {
            release(pending);
        }
    }

    private static void release(Pending pending) {
        TicketType type = Registration.SPAWN_SEARCH.get();
        for (long packed : pending.chunks()) {
            pending.level().getChunkSource().removeTicketWithRadius(type, ChunkPos.unpack(packed), 0);
        }
    }

    /**
     * The chunk window the refine may read: {@code WINDOW_CHUNKS} either side of
     * the chunk containing {@code centre}. Package-private and free of level
     * state so it can be unit-tested.
     */
    static long[] window(BlockPos centre) {
        return window(centre.getX() >> 4, centre.getZ() >> 4);
    }

    static long[] window(int chunkX, int chunkZ) {
        int side = WINDOW_CHUNKS * 2 + 1;
        long[] out = new long[side * side];
        int n = 0;
        for (int dx = -WINDOW_CHUNKS; dx <= WINDOW_CHUNKS; dx++) {
            for (int dz = -WINDOW_CHUNKS; dz <= WINDOW_CHUNKS; dz++) {
                out[n++] = ChunkPos.pack(chunkX + dx, chunkZ + dz);
            }
        }
        return out;
    }

    private static boolean allPresent(ServerLevel level, long[] chunks) {
        for (long packed : chunks) {
            if (level.getChunkSource().getChunkNow(ChunkPos.getX(packed), ChunkPos.getZ(packed)) == null) {
                return false;
            }
        }
        return true;
    }

    /** Test seam — how many waits are outstanding. */
    static int pendingCount() {
        return PENDING.size();
    }
}
