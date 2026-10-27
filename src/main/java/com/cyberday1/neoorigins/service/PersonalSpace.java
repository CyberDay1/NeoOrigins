package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.PersonalDimensionPower;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The {@code neoorigins:personal_space} dimension behind the
 * {@code personal_dimension} power: where each player's box sits, how it is
 * built and grown, and which mob spawns the dimension refuses.
 *
 * <p>Boxes sit on a grid, one cell per player, {@link #SPACING} blocks apart
 * and {@link #COLUMNS} cells to a row. The usable interior is
 * {@code size × size} wide and {@code size} tall; the bedrock floor, barrier
 * walls and barrier ceiling sit outside it.
 */
public final class PersonalSpace {

    private PersonalSpace() {}

    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "personal_space"));

    public static final int MIN_SIZE = 1;
    public static final int MAX_SIZE = 64;
    public static final int DEFAULT_SIZE = 12;
    public static final int SPACING = 1024;
    public static final int COLUMNS = 64;
    /** Y of the bedrock floor; the interior starts one block above. */
    public static final int FLOOR_Y = 64;

    /** Spawn reasons refused in the dimension: everything the world does on its own. */
    private static final Set<MobSpawnType> DENIED_SPAWNS = EnumSet.of(
        MobSpawnType.NATURAL, MobSpawnType.CHUNK_GENERATION, MobSpawnType.STRUCTURE,
        MobSpawnType.PATROL, MobSpawnType.REINFORCEMENT, MobSpawnType.EVENT);

    public static boolean isPersonalSpace(Level level) {
        return level != null && level.dimension() == DIMENSION;
    }

    public static boolean deniesSpawn(MobSpawnType type) {
        return DENIED_SPAWNS.contains(type);
    }

    // ── Geometry ────────────────────────────────────────────────────────────

    public static int cellCentreX(int cell) { return (cell % COLUMNS) * SPACING; }
    public static int cellCentreZ(int cell) { return (cell / COLUMNS) * SPACING; }

    /** World X (or Z) of the first interior column for a box of {@code size} centred on {@code centre}. */
    public static int interiorStart(int centre, int size) { return centre - size / 2; }

    /** Centre of the box floor, where the player arrives. */
    public static Vec3 arrivalPoint(int cell, int size) {
        return new Vec3(interiorStart(cellCentreX(cell), size) + size / 2.0, FLOOR_Y + 1,
            interiorStart(cellCentreZ(cell), size) + size / 2.0);
    }

    /** Visits every shell position, relative to the interior's low corner (floor at dy = 0). */
    public interface ShellVisitor { void visit(int dx, int dy, int dz); }

    public static void forEachShell(int size, ShellVisitor v) {
        for (int dy = 0; dy <= size + 1; dy++) {
            boolean plane = dy == 0 || dy == size + 1;
            for (int dx = -1; dx <= size; dx++) {
                for (int dz = -1; dz <= size; dz++) {
                    if (plane || dx == -1 || dx == size || dz == -1 || dz == size) v.visit(dx, dy, dz);
                }
            }
        }
    }

    /** True if the relative position lies in the usable interior of a box of {@code size}. */
    public static boolean inInterior(int dx, int dy, int dz, int size) {
        return dx >= 0 && dx < size && dz >= 0 && dz < size && dy >= 1 && dy <= size;
    }

    /**
     * Builds, grows or repairs a box. When {@code newSize} exceeds
     * {@code builtSize}, the old walls and ceiling that now fall inside the
     * interior are cleared (only where they are still barrier), and the
     * contents are left alone. Then every shell block that is missing or wrong
     * is placed, which also repairs anything another mod removed.
     */
    public static void buildBox(ServerLevel level, int cell, int builtSize, int newSize) {
        int cx = cellCentreX(cell);
        int cz = cellCentreZ(cell);
        // Flags 2 | 16: notify clients, no neighbour or shape updates, so nothing
        // attached to the old walls pops off when they are cleared.
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (builtSize > 0 && newSize > builtSize) {
            int ox = interiorStart(cx, builtSize), oz = interiorStart(cz, builtSize);
            int nx = interiorStart(cx, newSize), nz = interiorStart(cz, newSize);
            forEachShell(builtSize, (dx, dy, dz) -> {
                if (dy == 0) return; // old floor is part of the new floor
                int wx = ox + dx, wz = oz + dz;
                if (!inInterior(wx - nx, dy, wz - nz, newSize)) return;
                pos.set(wx, FLOOR_Y + dy, wz);
                if (level.getBlockState(pos).is(Blocks.BARRIER)) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                }
            });
        }
        int sx = interiorStart(cx, newSize), sz = interiorStart(cz, newSize);
        BlockState bedrock = Blocks.BEDROCK.defaultBlockState();
        BlockState barrier = Blocks.BARRIER.defaultBlockState();
        forEachShell(newSize, (dx, dy, dz) -> {
            pos.set(sx + dx, FLOOR_Y + dy, sz + dz);
            BlockState want = dy == 0 ? bedrock : barrier;
            if (!level.getBlockState(pos).is(want.getBlock())) level.setBlock(pos, want, flags);
        });
    }

    // ── Losing the power while inside ───────────────────────────────────────

    private static final Set<UUID> PENDING_REVOKE_CHECKS = new HashSet<>();

    public static void scheduleRevokeCheck(ServerPlayer player) {
        PENDING_REVOKE_CHECKS.add(player.getUUID());
    }

    /** Runs once per server tick: anyone in the dimension who lost their last personal_dimension power is sent out. */
    public static void tick(MinecraftServer server) {
        if (PENDING_REVOKE_CHECKS.isEmpty()) return;
        Set<UUID> due = new HashSet<>(PENDING_REVOKE_CHECKS);
        PENDING_REVOKE_CHECKS.clear();
        for (UUID id : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !isPersonalSpace(player.level())) continue;
            if (!hasPower(player)) PersonalDimensionPower.leave(player);
        }
    }

    private static boolean hasPower(ServerPlayer player) {
        for (PowerHolder<?> holder : ActiveOriginService.allPowers(player)) {
            if (holder.type() instanceof PersonalDimensionPower) return true;
        }
        return false;
    }
}
