package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.attachment.PlacedLogs;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * Per-chunk player-placed-log bookkeeping for the
 * {@link com.cyberday1.neoorigins.power.builtin.CropHarvestBonusPower}
 * exclusion (GitHub #91). Three operations:
 *
 * <ul>
 *   <li>{@link #markPlaced} — called from
 *       {@code WorldPowerEvents.onPlayerPlaceLog} when a player places a
 *       log block.</li>
 *   <li>{@link #isPlaced} — called from the CropHarvestBonus block-break
 *       branch to gate the bonus.</li>
 *   <li>{@link #clear} — called after a break to free the slot; the block
 *       is gone so there's nothing left to gate.</li>
 * </ul>
 *
 * <p>Backed by a {@link PlacedLogs} attachment on each {@link ChunkAccess};
 * NeoForge handles serialization with the chunk so marks survive a world
 * reload. Memory cost is 8 bytes per tracked position per loaded chunk —
 * a 500-log player build in 4 loaded chunks is ~4&nbsp;KB, well below the
 * noise floor of vanilla chunk data.
 */
public final class PlayerPlacedLogTracker {

    private PlayerPlacedLogTracker() {}

    public static void markPlaced(LevelAccessor level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        PlacedLogs data = chunk.getData(EntityAttachments.placedLogs());
        if (data.positions().add(pos.asLong())) {
            chunk.markUnsaved();
        }
    }

    public static boolean isPlaced(LevelAccessor level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        PlacedLogs data = chunk.getData(EntityAttachments.placedLogs());
        return data.positions().contains(pos.asLong());
    }

    public static void clear(LevelAccessor level, BlockPos pos) {
        ChunkAccess chunk = level.getChunk(pos);
        PlacedLogs data = chunk.getData(EntityAttachments.placedLogs());
        if (data.positions().remove(pos.asLong())) {
            chunk.markUnsaved();
        }
    }
}
