package net.minecraft.util.profiling.jfr.stats;

import jdk.jfr.consumer.RecordedEvent;

public record ChunkIdentification(String level, String dimension, int x, int z) {
    public static ChunkIdentification from(RecordedEvent p_326190_) {
        return new ChunkIdentification(
            p_326190_.getString("level"), p_326190_.getString("dimension"), p_326190_.getInt("chunkPosX"), p_326190_.getInt("chunkPosZ")
        );
    }
}
