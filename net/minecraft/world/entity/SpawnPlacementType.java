package net.minecraft.world.entity;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;

public interface SpawnPlacementType {
    boolean isSpawnPositionOk(LevelReader p_321641_, BlockPos p_321773_, @Nullable EntityType<?> p_321744_);

    default BlockPos adjustSpawnPosition(LevelReader p_321714_, BlockPos p_321542_) {
        return p_321542_;
    }
}
