package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.condition.LocationCondition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

/**
 * Resolves an origin {@code spawn_location} without stalling the server thread.
 *
 * <p><b>The problem.</b> {@code LocationCondition#locateSpawn}'s biome branch
 * used to drive vanilla's unbounded {@code findClosestBiome3d} spiral with
 * radius 12800 / step 16 — up to ~30.7 million climate samples in one
 * synchronous call. With a third-party biome mod that pushes (say) oceans far
 * from world spawn, that overruns the 60-second watchdog and the server dies.
 * Five shipped origins (abyssal, kraken, merling, siren, sea_dragon) sit on
 * {@code "biome_tag": "minecraft:is_ocean"} and are exposed to exactly this.
 *
 * <p><b>The split.</b> A spawn resolution has three phases, and only the middle
 * one is safe to move:
 * <ol>
 *   <li><i>Resolve the target level and search centre</i> — trivial, server thread.</li>
 *   <li><i>Find a matching biome</i> — expensive, and genuinely thread-safe
 *       (worldgen samples biomes from worker threads constantly). This is what
 *       moves off-thread, via {@link BiomeSpawnSearch} / {@link BoundedBiomeSpiral}.</li>
 *   <li><i>Refine that centre into a standable position and teleport</i> —
 *       reads block states and mutates the player, so it stays on the server
 *       thread. But it first needs the destination chunks <i>generated</i>, and
 *       doing that inline blocks the server for as long as generation takes —
 *       20-30 s on a heavy worldgen pack, which is what a 2026-08-01 report
 *       actually hit. {@link SpawnChunkLoader} now gets them generated on the
 *       chunk system's own workers and calls back when they land, so only the
 *       cheap block reads run on the server thread.</li>
 * </ol>
 * The <b>structure</b> branch never leaves the server thread either: structure
 * lookup can trigger chunk generation and touches structure-manager state.
 *
 * <p><b>Nothing mutable crosses the boundary.</b> The worker gets a
 * {@link BiomeSpawnSearch.Query} of immutable values only — no
 * {@code ServerPlayer}, no {@code ServerLevel}. When the search lands, the
 * player is re-resolved by UUID and the level by {@link ResourceKey} on the
 * server thread, and the caller re-validates that the teleport is still wanted
 * (still online, still that origin, config still allows it) before applying it.
 *
 * <p><b>Bounded concurrency.</b> At most one search per player is in flight; a
 * new request for the same player cancels the old one. Work runs on this mod's
 * own two-thread daemon pool rather than {@code Util.backgroundExecutor()},
 * which is shared with Minecraft's own chunk/IO work. The pool is torn down on
 * server stop and lazily recreated, so a singleplayer world reload does not
 * leak threads.
 */
public final class AsyncSpawnLocator {

    private AsyncSpawnLocator() {}

    /**
     * Two threads: enough that a handful of players finishing the picker at the
     * same moment do not serialise behind each other, small enough that a rush
     * of joins cannot starve the rest of the machine. Each search is budgeted at
     * {@link BoundedBiomeSpiral#BUDGET_MILLIS} anyway, so the queue drains.
     */
    private static final int POOL_SIZE = 2;

    private static final Object LOCK = new Object();

    /** Lazily created, torn down on server stop. Guarded by {@link #LOCK}. */
    private static ExecutorService executor;

    /**
     * False between {@link #shutdown()} and the next {@link #serverStarting()}.
     * Without it, a stray {@code locate} racing server shutdown would spin the
     * pool back up after we had just torn it down.
     */
    private static volatile boolean accepting = true;

    /** Per-player cancellation flags for in-flight searches. */
    private static final Map<UUID, AtomicBoolean> IN_FLIGHT = new ConcurrentHashMap<>();

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "NeoOrigins Spawn Search #" + counter.incrementAndGet());
            t.setDaemon(true);
            // Below normal: a spawn teleport arriving 200ms later is invisible;
            // stealing cycles from the server thread is not.
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }
    };

    /**
     * Resolves {@code spec} for {@code player} and hands the result to
     * {@code onResolved} <b>on the server thread</b>.
     *
     * <p>Call on the server thread. {@code onResolved} may be invoked
     * re-entrantly before this method returns (when the spec needs no biome
     * search), or on a later tick (when it does). It is invoked with the
     * freshly re-resolved {@link ServerPlayer}, which the callback must
     * re-validate before acting on — the player may have died, changed origin
     * or been teleported elsewhere in the meantime.
     *
     * <p>{@code onResolved} is <b>not</b> invoked at all if the player has
     * logged out, the target dimension has unloaded, or the search was
     * cancelled or superseded.
     */
    public static void locate(ServerPlayer player,
                              LocationCondition spec,
                              BiConsumer<ServerPlayer, Optional<LocationCondition.SpawnTarget>> onResolved) {
        if (spec.isEmpty()) {
            onResolved.accept(player, Optional.empty());
            return;
        }

        Optional<ServerLevel> resolved = spec.resolveTargetLevel(player);
        if (resolved.isEmpty()) {
            onResolved.accept(player, Optional.empty());
            return;
        }
        ServerLevel target = resolved.get();
        BlockPos searchOrigin = target.getSharedSpawnPos();

        // Structure-driven and dimension-only specs skip the biome search (it
        // is either irrelevant or superseded), but they still have to refine a
        // centre into a standable position, and that is the phase that
        // force-loads chunks. Route them through the same preload.
        if (!spec.usesBiomeSearch()) {
            BlockPos center;
            if (spec.usesStructureSearch()) {
                Optional<BlockPos> structureCenter = spec.locateStructureCenter(target, searchOrigin);
                if (structureCenter.isEmpty()) {
                    onResolved.accept(player, Optional.empty());
                    return;
                }
                center = structureCenter.get();
            } else {
                center = searchOrigin;
            }
            refineWhenLoaded(player.server, player.getUUID(), target.dimension(), target,
                spec, center, NEVER_ABANDONED, NO_OP, onResolved);
            return;
        }

        // Snapshot everything the search needs while still on the server thread.
        Optional<BiomeSpawnSearch.Query> query =
            BiomeSpawnSearch.capture(target, searchOrigin, spec.biomeMatcher());
        if (query.isEmpty()) {
            // No biome in this dimension's palette can match — vanilla's own
            // zero-cost miss. Report it immediately rather than queueing a
            // worker that would find nothing.
            onResolved.accept(player, Optional.empty());
            return;
        }

        final MinecraftServer server = player.server;
        final UUID uuid = player.getUUID();
        final ResourceKey<Level> levelKey = target.dimension();
        final AtomicBoolean cancelled = new AtomicBoolean(false);

        // One search per player: a newer request wins, the older one aborts.
        AtomicBoolean previous = IN_FLIGHT.put(uuid, cancelled);
        if (previous != null) previous.set(true);

        ExecutorService pool = pool();
        if (pool == null) {
            // Server is stopping — do not start new work, and do not silently
            // hold the player hostage: fall through with no result.
            IN_FLIGHT.remove(uuid, cancelled);
            return;
        }

        try {
            pool.execute(() -> {
                BoundedBiomeSpiral.Result result;
                try {
                    result = BiomeSpawnSearch.run(query.get(), cancelled);
                } catch (Throwable t) {
                    NeoOrigins.LOGGER.error("Biome spawn search failed for player {}", uuid, t);
                    IN_FLIGHT.remove(uuid, cancelled);
                    return;
                }

                if (cancelled.get()) {
                    IN_FLIGHT.remove(uuid, cancelled);
                    return;
                }

                if (result.outcome() == BoundedBiomeSpiral.Outcome.TIMED_OUT) {
                    NeoOrigins.LOGGER.warn(
                        "Biome spawn search for player {} gave up after {}ms and {} samples — spec: {}",
                        uuid, BoundedBiomeSpiral.BUDGET_MILLIS, result.samples(), spec.formatSummary());
                }

                final BoundedBiomeSpiral.Result finished = result;
                try {
                    server.execute(() -> {
                        if (cancelled.get()) { IN_FLIGHT.remove(uuid, cancelled); return; }
                        ServerPlayer live = server.getPlayerList().getPlayer(uuid);
                        ServerLevel liveLevel = server.getLevel(levelKey);
                        // logged out, or dimension unloaded, mid-search
                        if (live == null || liveLevel == null) {
                            IN_FLIGHT.remove(uuid, cancelled);
                            return;
                        }
                        if (!finished.found()) {
                            IN_FLIGHT.remove(uuid, cancelled);
                            onResolved.accept(live, Optional.empty());
                            return;
                        }
                        // The in-flight registration deliberately outlives the
                        // biome search: the chunk wait below is the long pole
                        // now, and a re-pick during it must still supersede us.
                        refineWhenLoaded(server, uuid, levelKey, liveLevel, spec,
                            new BlockPos(finished.x(), finished.y(), finished.z()),
                            cancelled::get, () -> IN_FLIGHT.remove(uuid, cancelled), onResolved);
                    });
                } catch (RejectedExecutionException stopping) {
                    IN_FLIGHT.remove(uuid, cancelled);
                }
            });
        } catch (RejectedExecutionException stopping) {
            IN_FLIGHT.remove(uuid, cancelled);
        }
    }

    private static final BooleanSupplier NEVER_ABANDONED = () -> false;
    private static final Runnable NO_OP = () -> {};

    /**
     * Third phase: get the destination chunks generated off the server thread,
     * then refine the centre into a standable position and hand it to
     * {@code onResolved} — still on the server thread, but a few ticks later.
     *
     * <p>This is the phase that used to stall. {@code refineSpawn} force-loads
     * up to nine chunks around the destination, and
     * {@code ServerChunkCache#getChunk} generates them by blocking the server
     * thread until they are done — 20-30 s on a Tectonic + Terralith pack, with
     * the biome search contributing essentially none of it.
     * {@link SpawnChunkLoader} takes a region ticket instead and lets the chunk
     * system's own workers do it.
     *
     * <p>Because the wait spans ticks, the same re-validation the biome search
     * needs applies again on the far side: the player is re-resolved by UUID and
     * the level by key, and callers re-check origin/config/dimension in their
     * own callback. {@code onSettled} runs first and unconditionally — it is how
     * the in-flight registration is released whichever way the wait ended.
     */
    private static void refineWhenLoaded(MinecraftServer server,
                                         UUID uuid,
                                         ResourceKey<Level> levelKey,
                                         ServerLevel target,
                                         LocationCondition spec,
                                         BlockPos centre,
                                         BooleanSupplier abandoned,
                                         Runnable onSettled,
                                         BiConsumer<ServerPlayer, Optional<LocationCondition.SpawnTarget>> onResolved) {
        SpawnChunkLoader.whenReady(target, centre, abandoned, outcome -> {
            onSettled.run();
            if (outcome == SpawnChunkLoader.Outcome.ABANDONED) return;
            ServerPlayer live = server.getPlayerList().getPlayer(uuid);
            if (live == null) return; // logged out while chunks generated
            ServerLevel liveLevel = server.getLevel(levelKey);
            if (liveLevel == null) return; // dimension unloaded while chunks generated
            if (outcome == SpawnChunkLoader.Outcome.TIMED_OUT) {
                NeoOrigins.LOGGER.warn(
                    "Spawn destination chunks near {} did not finish generating in time for player {} — "
                        + "refining over what is loaded; spec: {}",
                    centre, uuid, spec.formatSummary());
            }
            onResolved.accept(live, spec.refineSpawnPreloaded(liveLevel, centre));
        });
    }

    /** Aborts any in-flight search for this player (logout, or an explicit override). */
    public static void cancel(UUID uuid) {
        AtomicBoolean flag = IN_FLIGHT.remove(uuid);
        if (flag != null) flag.set(true);
    }

    /** Re-arms the locator for a freshly started server (singleplayer world reload included). */
    public static void serverStarting() {
        accepting = true;
    }

    /**
     * Cancels every in-flight search and tears the pool down. Called on
     * {@code ServerStoppingEvent}; the pool is recreated lazily if another
     * world starts (singleplayer reload).
     */
    public static void shutdown() {
        accepting = false;
        for (AtomicBoolean flag : IN_FLIGHT.values()) flag.set(true);
        IN_FLIGHT.clear();
        // Drop any chunk waits too, and release their tickets — the levels they
        // reference are about to go away. Called from ServerStoppingEvent, i.e.
        // on the server thread, which is where PENDING lives.
        SpawnChunkLoader.shutdown();
        ExecutorService pool;
        synchronized (LOCK) {
            pool = executor;
            executor = null;
        }
        if (pool != null) pool.shutdownNow();
    }

    private static ExecutorService pool() {
        if (!accepting) return null;
        synchronized (LOCK) {
            if (!accepting) return null;
            if (executor == null || executor.isShutdown()) {
                executor = Executors.newFixedThreadPool(POOL_SIZE, THREAD_FACTORY);
            }
            return executor;
        }
    }
}
