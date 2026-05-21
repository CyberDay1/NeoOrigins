package com.cyberday1.neoorigins.attachment;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * Per-chunk set of {@link net.minecraft.core.BlockPos}-packed longs marking
 * log positions that were placed by a player (as opposed to growing from a
 * sapling or being part of world-generation).
 *
 * <p>Drives the player-placed-log exclusion in
 * {@link com.cyberday1.neoorigins.power.builtin.CropHarvestBonusPower} so
 * builders placing log walls don't get duplicate drops when they break them
 * back down. Natural and sapling-grown trees are never marked — they go
 * through {@code TreeFeature}/{@code FeaturePlaceContext}, not the player
 * {@code BlockEvent.EntityPlaceEvent} path.
 *
 * <p>Storage is a {@code LongOpenHashSet} (fastutil) — typical player builds
 * place hundreds of logs total, so the per-chunk set rarely exceeds a few
 * dozen entries. Serializes as a {@code long[]} via the chunk attachment.
 *
 * <p>This record is intentionally mutable on its internal set — callers add
 * / remove / check against the same instance returned by
 * {@code chunk.getData(EntityAttachments.placedLogs())}. The chunk needs an
 * {@code setUnsaved(true)} call after mutation so the attachment is persisted
 * with the chunk; helpers in {@link com.cyberday1.neoorigins.service.PlayerPlacedLogTracker}
 * handle that.
 */
public record PlacedLogs(LongOpenHashSet positions) {

    public static final Codec<PlacedLogs> CODEC = Codec.LONG_STREAM.xmap(
        stream -> new PlacedLogs(new LongOpenHashSet(stream.toArray())),
        plogs -> java.util.stream.LongStream.of(plogs.positions.toLongArray())
    );

    public static PlacedLogs empty() {
        return new PlacedLogs(new LongOpenHashSet());
    }
}
