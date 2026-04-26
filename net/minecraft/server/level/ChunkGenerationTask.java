package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkDependencies;
import net.minecraft.world.level.chunk.status.ChunkPyramid;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class ChunkGenerationTask {
    private final GeneratingChunkMap chunkMap;
    private final ChunkPos pos;
    @Nullable
    private ChunkStatus scheduledStatus = null;
    public final ChunkStatus targetStatus;
    private volatile boolean markedForCancellation;
    private final List<CompletableFuture<ChunkResult<ChunkAccess>>> scheduledLayer = new ArrayList<>();
    private final StaticCache2D<GenerationChunkHolder> cache;
    private boolean needsGeneration;

    private ChunkGenerationTask(GeneratingChunkMap p_347493_, ChunkStatus p_347718_, ChunkPos p_347615_, StaticCache2D<GenerationChunkHolder> p_347529_) {
        this.chunkMap = p_347493_;
        this.targetStatus = p_347718_;
        this.pos = p_347615_;
        this.cache = p_347529_;
    }

    public static ChunkGenerationTask create(GeneratingChunkMap p_347575_, ChunkStatus p_347556_, ChunkPos p_347630_) {
        int i = ChunkPyramid.GENERATION_PYRAMID.getStepTo(p_347556_).getAccumulatedRadiusOf(ChunkStatus.EMPTY);
        StaticCache2D<GenerationChunkHolder> staticcache2d = StaticCache2D.create(
            p_347630_.x, p_347630_.z, i, (p_347569_, p_347704_) -> p_347575_.acquireGeneration(ChunkPos.asLong(p_347569_, p_347704_))
        );
        return new ChunkGenerationTask(p_347575_, p_347556_, p_347630_, staticcache2d);
    }

    @Nullable
    public CompletableFuture<?> runUntilWait() {
        while (true) {
            CompletableFuture<?> completablefuture = this.waitForScheduledLayer();
            if (completablefuture != null) {
                return completablefuture;
            }

            if (this.markedForCancellation || this.scheduledStatus == this.targetStatus) {
                this.releaseClaim();
                return null;
            }

            this.scheduleNextLayer();
        }
    }

    private void scheduleNextLayer() {
        ChunkStatus chunkstatus;
        if (this.scheduledStatus == null) {
            chunkstatus = ChunkStatus.EMPTY;
        } else if (!this.needsGeneration && this.scheduledStatus == ChunkStatus.EMPTY && !this.canLoadWithoutGeneration()) {
            this.needsGeneration = true;
            chunkstatus = ChunkStatus.EMPTY;
        } else {
            chunkstatus = ChunkStatus.getStatusList().get(this.scheduledStatus.getIndex() + 1);
        }

        this.scheduleLayer(chunkstatus, this.needsGeneration);
        this.scheduledStatus = chunkstatus;
    }

    public void markForCancellation() {
        this.markedForCancellation = true;
    }

    private void releaseClaim() {
        GenerationChunkHolder generationchunkholder = this.cache.get(this.pos.x, this.pos.z);
        generationchunkholder.removeTask(this);
        this.cache.forEach(this.chunkMap::releaseGeneration);
    }

    private boolean canLoadWithoutGeneration() {
        if (this.targetStatus == ChunkStatus.EMPTY) {
            return true;
        } else {
            ChunkStatus chunkstatus = this.cache.get(this.pos.x, this.pos.z).getPersistedStatus();
            if (chunkstatus != null && !chunkstatus.isBefore(this.targetStatus)) {
                ChunkDependencies chunkdependencies = ChunkPyramid.LOADING_PYRAMID.getStepTo(this.targetStatus).accumulatedDependencies();
                int i = chunkdependencies.getRadius();

                for (int j = this.pos.x - i; j <= this.pos.x + i; j++) {
                    for (int k = this.pos.z - i; k <= this.pos.z + i; k++) {
                        int l = this.pos.getChessboardDistance(j, k);
                        ChunkStatus chunkstatus1 = chunkdependencies.get(l);
                        ChunkStatus chunkstatus2 = this.cache.get(j, k).getPersistedStatus();
                        if (chunkstatus2 == null || chunkstatus2.isBefore(chunkstatus1)) {
                            return false;
                        }
                    }
                }

                return true;
            } else {
                return false;
            }
        }
    }

    public GenerationChunkHolder getCenter() {
        return this.cache.get(this.pos.x, this.pos.z);
    }

    private void scheduleLayer(ChunkStatus p_347611_, boolean p_347592_) {
        int i = this.getRadiusForLayer(p_347611_, p_347592_);

        for (int j = this.pos.x - i; j <= this.pos.x + i; j++) {
            for (int k = this.pos.z - i; k <= this.pos.z + i; k++) {
                GenerationChunkHolder generationchunkholder = this.cache.get(j, k);
                if (this.markedForCancellation || !this.scheduleChunkInLayer(p_347611_, p_347592_, generationchunkholder)) {
                    return;
                }
            }
        }
    }

    private int getRadiusForLayer(ChunkStatus p_347511_, boolean p_347717_) {
        ChunkPyramid chunkpyramid = p_347717_ ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        return chunkpyramid.getStepTo(this.targetStatus).getAccumulatedRadiusOf(p_347511_);
    }

    private boolean scheduleChunkInLayer(ChunkStatus p_347483_, boolean p_347471_, GenerationChunkHolder p_347684_) {
        ChunkStatus chunkstatus = p_347684_.getPersistedStatus();
        boolean flag = chunkstatus != null && p_347483_.isAfter(chunkstatus);
        ChunkPyramid chunkpyramid = flag ? ChunkPyramid.GENERATION_PYRAMID : ChunkPyramid.LOADING_PYRAMID;
        if (flag && !p_347471_) {
            throw new IllegalStateException("Can't load chunk, but didn't expect to need to generate");
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = p_347684_.applyStep(chunkpyramid.getStepTo(p_347483_), this.chunkMap, this.cache);
            ChunkResult<ChunkAccess> chunkresult = completablefuture.getNow(null);
            if (chunkresult == null) {
                this.scheduledLayer.add(completablefuture);
                return true;
            } else if (chunkresult.isSuccess()) {
                return true;
            } else {
                this.markForCancellation();
                return false;
            }
        }
    }

    @Nullable
    private CompletableFuture<?> waitForScheduledLayer() {
        while (!this.scheduledLayer.isEmpty()) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.scheduledLayer.getLast();
            ChunkResult<ChunkAccess> chunkresult = completablefuture.getNow(null);
            if (chunkresult == null) {
                return completablefuture;
            }

            this.scheduledLayer.removeLast();
            if (!chunkresult.isSuccess()) {
                this.markForCancellation();
            }
        }

        return null;
    }
}
