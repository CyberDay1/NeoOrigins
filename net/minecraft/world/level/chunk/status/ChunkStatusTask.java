package net.minecraft.world.level.chunk.status;

import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.chunk.ChunkAccess;

@FunctionalInterface
public interface ChunkStatusTask {
    CompletableFuture<ChunkAccess> doWork(WorldGenContext p_347520_, ChunkStep p_347546_, StaticCache2D<GenerationChunkHolder> p_347485_, ChunkAccess p_347617_);
}
