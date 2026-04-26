package net.minecraft.world.level.pathfinder;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;

public class PathfindingContext {
    private final CollisionGetter level;
    @Nullable
    private final PathTypeCache cache;
    private final BlockPos mobPosition;
    private final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

    public PathfindingContext(CollisionGetter p_331783_, Mob p_331698_) {
        this.level = p_331783_;
        if (p_331698_.level() instanceof ServerLevel serverlevel) {
            this.cache = serverlevel.getPathTypeCache();
        } else {
            this.cache = null;
        }

        this.mobPosition = p_331698_.blockPosition();
    }

    public PathType getPathTypeFromState(int p_331972_, int p_330358_, int p_330334_) {
        BlockPos blockpos = this.mutablePos.set(p_331972_, p_330358_, p_330334_);
        return this.cache == null ? WalkNodeEvaluator.getPathTypeFromState(this.level, blockpos) : this.cache.getOrCompute(this.level, blockpos);
    }

    public BlockState getBlockState(BlockPos p_330575_) {
        return this.level.getBlockState(p_330575_);
    }

    public CollisionGetter level() {
        return this.level;
    }

    public BlockPos mobPosition() {
        return this.mobPosition;
    }

    BlockPos currentEvalPos() {
        return this.mutablePos;
    }
}
