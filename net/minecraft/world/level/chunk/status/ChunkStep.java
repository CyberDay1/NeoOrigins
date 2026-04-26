package net.minecraft.world.level.chunk.status;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.profiling.jfr.JvmProfiler;
import net.minecraft.util.profiling.jfr.callback.ProfiledDuration;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ProtoChunk;

public record ChunkStep(
    ChunkStatus targetStatus, ChunkDependencies directDependencies, ChunkDependencies accumulatedDependencies, int blockStateWriteRadius, ChunkStatusTask task
) {
    public int getAccumulatedRadiusOf(ChunkStatus p_347454_) {
        return p_347454_ == this.targetStatus ? 0 : this.accumulatedDependencies.getRadiusOf(p_347454_);
    }

    public CompletableFuture<ChunkAccess> apply(WorldGenContext p_347561_, StaticCache2D<GenerationChunkHolder> p_347614_, ChunkAccess p_347449_) {
        if (p_347449_.getPersistedStatus().isBefore(this.targetStatus)) {
            ProfiledDuration profiledduration = JvmProfiler.INSTANCE
                .onChunkGenerate(p_347449_.getPos(), p_347561_.level().dimension(), this.targetStatus.getName());
            return this.task.doWork(p_347561_, this, p_347614_, p_347449_).thenApply(p_347625_ -> this.completeChunkGeneration(p_347625_, profiledduration));
        } else {
            return this.task.doWork(p_347561_, this, p_347614_, p_347449_);
        }
    }

    private ChunkAccess completeChunkGeneration(ChunkAccess p_347705_, @Nullable ProfiledDuration p_347691_) {
        if (p_347705_ instanceof ProtoChunk protochunk && protochunk.getPersistedStatus().isBefore(this.targetStatus)) {
            protochunk.setPersistedStatus(this.targetStatus);
        }

        if (p_347691_ != null) {
            p_347691_.finish();
        }

        return p_347705_;
    }

    public static class Builder {
        private final ChunkStatus status;
        @Nullable
        private final ChunkStep parent;
        private ChunkStatus[] directDependenciesByRadius;
        private int blockStateWriteRadius = -1;
        private ChunkStatusTask task = ChunkStatusTasks::passThrough;

        protected Builder(ChunkStatus p_347618_) {
            if (p_347618_.getParent() != p_347618_) {
                throw new IllegalArgumentException("Not starting with the first status: " + p_347618_);
            } else {
                this.status = p_347618_;
                this.parent = null;
                this.directDependenciesByRadius = new ChunkStatus[0];
            }
        }

        protected Builder(ChunkStatus p_347600_, ChunkStep p_347655_) {
            if (p_347655_.targetStatus.getIndex() != p_347600_.getIndex() - 1) {
                throw new IllegalArgumentException("Out of order status: " + p_347600_);
            } else {
                this.status = p_347600_;
                this.parent = p_347655_;
                this.directDependenciesByRadius = new ChunkStatus[]{p_347655_.targetStatus};
            }
        }

        public ChunkStep.Builder addRequirement(ChunkStatus p_347610_, int p_347602_) {
            if (p_347610_.isOrAfter(this.status)) {
                throw new IllegalArgumentException("Status " + p_347610_ + " can not be required by " + this.status);
            } else {
                ChunkStatus[] achunkstatus = this.directDependenciesByRadius;
                int i = p_347602_ + 1;
                if (i > achunkstatus.length) {
                    this.directDependenciesByRadius = new ChunkStatus[i];
                    Arrays.fill(this.directDependenciesByRadius, p_347610_);
                }

                for (int j = 0; j < Math.min(i, achunkstatus.length); j++) {
                    this.directDependenciesByRadius[j] = ChunkStatus.max(achunkstatus[j], p_347610_);
                }

                return this;
            }
        }

        public ChunkStep.Builder blockStateWriteRadius(int p_347450_) {
            this.blockStateWriteRadius = p_347450_;
            return this;
        }

        public ChunkStep.Builder setTask(ChunkStatusTask p_347502_) {
            this.task = p_347502_;
            return this;
        }

        public ChunkStep build() {
            return new ChunkStep(
                this.status,
                new ChunkDependencies(ImmutableList.copyOf(this.directDependenciesByRadius)),
                new ChunkDependencies(ImmutableList.copyOf(this.buildAccumulatedDependencies())),
                this.blockStateWriteRadius,
                this.task
            );
        }

        private ChunkStatus[] buildAccumulatedDependencies() {
            if (this.parent == null) {
                return this.directDependenciesByRadius;
            } else {
                int i = this.getRadiusOfParent(this.parent.targetStatus);
                ChunkDependencies chunkdependencies = this.parent.accumulatedDependencies;
                ChunkStatus[] achunkstatus = new ChunkStatus[Math.max(i + chunkdependencies.size(), this.directDependenciesByRadius.length)];

                for (int j = 0; j < achunkstatus.length; j++) {
                    int k = j - i;
                    if (k < 0 || k >= chunkdependencies.size()) {
                        achunkstatus[j] = this.directDependenciesByRadius[j];
                    } else if (j >= this.directDependenciesByRadius.length) {
                        achunkstatus[j] = chunkdependencies.get(k);
                    } else {
                        achunkstatus[j] = ChunkStatus.max(this.directDependenciesByRadius[j], chunkdependencies.get(k));
                    }
                }

                return achunkstatus;
            }
        }

        private int getRadiusOfParent(ChunkStatus p_347567_) {
            for (int i = this.directDependenciesByRadius.length - 1; i >= 0; i--) {
                if (this.directDependenciesByRadius[i].isOrAfter(p_347567_)) {
                    return i;
                }
            }

            return 0;
        }
    }
}
