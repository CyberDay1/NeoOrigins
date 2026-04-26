package net.minecraft.world.level.block;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

public interface ChangeOverTimeBlock<T extends Enum<T>> {
    int SCAN_DISTANCE = 4;

    Optional<BlockState> getNext(BlockState p_153040_);

    float getChanceModifier();

    default void changeOverTime(BlockState p_309154_, ServerLevel p_309115_, BlockPos p_308999_, RandomSource p_308868_) {
        float f = 0.05688889F;
        if (p_308868_.nextFloat() < 0.05688889F) {
            this.getNextState(p_309154_, p_309115_, p_308999_, p_308868_).ifPresent(p_153039_ -> p_309115_.setBlockAndUpdate(p_308999_, p_153039_));
        }
    }

    T getAge();

    default Optional<BlockState> getNextState(BlockState p_309150_, ServerLevel p_309039_, BlockPos p_309042_, RandomSource p_308890_) {
        int i = this.getAge().ordinal();
        int j = 0;
        int k = 0;

        for (BlockPos blockpos : BlockPos.withinManhattan(p_309042_, 4, 4, 4)) {
            int l = blockpos.distManhattan(p_309042_);
            if (l > 4) {
                break;
            }

            if (!blockpos.equals(p_309042_) && p_309039_.getBlockState(blockpos).getBlock() instanceof ChangeOverTimeBlock<?> changeovertimeblock) {
                Enum<?> oenum = changeovertimeblock.getAge();
                if (this.getAge().getClass() == oenum.getClass()) {
                    int i1 = oenum.ordinal();
                    if (i1 < i) {
                        return Optional.empty();
                    }

                    if (i1 > i) {
                        k++;
                    } else {
                        j++;
                    }
                }
            }
        }

        float f = (float)(k + 1) / (float)(k + j + 1);
        float f1 = f * f * this.getChanceModifier();
        return p_308890_.nextFloat() < f1 ? this.getNext(p_309150_) : Optional.empty();
    }
}
