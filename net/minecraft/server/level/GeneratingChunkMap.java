package net.minecraft.server.level;

import java.util.concurrent.CompletableFuture;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public interface GeneratingChunkMap {
    GenerationChunkHolder acquireGeneration(long p_347510_);

    void releaseGeneration(GenerationChunkHolder p_347643_);

    CompletableFuture<ChunkAccess> applyStep(GenerationChunkHolder p_347633_, ChunkStep p_347487_, StaticCache2D<GenerationChunkHolder> p_347587_);

    ChunkGenerationTask scheduleGenerationTask(ChunkStatus p_347524_, ChunkPos p_347713_);

    void runGenerationTasks();
}
