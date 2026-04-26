package net.minecraft.world.level.pathfinder;

import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;

public class PathTypeCache {
    private static final int SIZE = 4096;
    private static final int MASK = 4095;
    private final long[] positions = new long[4096];
    private final PathType[] pathTypes = new PathType[4096];

    public PathType getOrCompute(BlockGetter p_330930_, BlockPos p_331162_) {
        long i = p_331162_.asLong();
        int j = index(i);
        PathType pathtype = this.get(j, i);
        return pathtype != null ? pathtype : this.compute(p_330930_, p_331162_, j, i);
    }

    @Nullable
    private PathType get(int p_330588_, long p_331771_) {
        return this.positions[p_330588_] == p_331771_ ? this.pathTypes[p_330588_] : null;
    }

    private PathType compute(BlockGetter p_330773_, BlockPos p_330311_, int p_330671_, long p_332065_) {
        PathType pathtype = WalkNodeEvaluator.getPathTypeFromState(p_330773_, p_330311_);
        this.positions[p_330671_] = p_332065_;
        this.pathTypes[p_330671_] = pathtype;
        return pathtype;
    }

    public void invalidate(BlockPos p_331507_) {
        long i = p_331507_.asLong();
        int j = index(i);
        if (this.positions[j] == i) {
            this.pathTypes[j] = null;
        }
    }

    private static int index(long p_332203_) {
        return (int)HashCommon.mix(p_332203_) & 4095;
    }
}
