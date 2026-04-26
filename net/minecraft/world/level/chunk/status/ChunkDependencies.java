package net.minecraft.world.level.chunk.status;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.util.Locale;

public final class ChunkDependencies {
    private final ImmutableList<ChunkStatus> dependencyByRadius;
    private final int[] radiusByDependency;

    public ChunkDependencies(ImmutableList<ChunkStatus> p_347595_) {
        this.dependencyByRadius = p_347595_;
        int i = p_347595_.isEmpty() ? 0 : p_347595_.getFirst().getIndex() + 1;
        this.radiusByDependency = new int[i];

        for (int j = 0; j < p_347595_.size(); j++) {
            ChunkStatus chunkstatus = p_347595_.get(j);
            int k = chunkstatus.getIndex();

            for (int l = 0; l <= k; l++) {
                this.radiusByDependency[l] = j;
            }
        }
    }

    @VisibleForTesting
    public ImmutableList<ChunkStatus> asList() {
        return this.dependencyByRadius;
    }

    public int size() {
        return this.dependencyByRadius.size();
    }

    public int getRadiusOf(ChunkStatus p_347685_) {
        int i = p_347685_.getIndex();
        if (i >= this.radiusByDependency.length) {
            throw new IllegalArgumentException(
                String.format(Locale.ROOT, "Requesting a ChunkStatus(%s) outside of dependency range(%s)", p_347685_, this.dependencyByRadius)
            );
        } else {
            return this.radiusByDependency[i];
        }
    }

    public int getRadius() {
        return Math.max(0, this.dependencyByRadius.size() - 1);
    }

    public ChunkStatus get(int p_347623_) {
        return this.dependencyByRadius.get(p_347623_);
    }

    @Override
    public String toString() {
        return this.dependencyByRadius.toString();
    }
}
