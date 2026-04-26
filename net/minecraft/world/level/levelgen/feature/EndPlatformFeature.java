package net.minecraft.world.level.levelgen.feature;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

public class EndPlatformFeature extends Feature<NoneFeatureConfiguration> {
    public EndPlatformFeature(Codec<NoneFeatureConfiguration> p_352966_) {
        super(p_352966_);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> p_352935_) {
        createEndPlatform(p_352935_.level(), p_352935_.origin(), false);
        return true;
    }

    public static void createEndPlatform(ServerLevelAccessor p_352905_, BlockPos p_352961_, boolean p_352931_) {
        BlockPos.MutableBlockPos blockpos$mutableblockpos = p_352961_.mutable();

        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                for (int k = -1; k < 3; k++) {
                    BlockPos blockpos = blockpos$mutableblockpos.set(p_352961_).move(j, k, i);
                    Block block = k == -1 ? Blocks.OBSIDIAN : Blocks.AIR;
                    if (!p_352905_.getBlockState(blockpos).is(block)) {
                        if (p_352931_) {
                            p_352905_.destroyBlock(blockpos, true, null);
                        }

                        p_352905_.setBlock(blockpos, block.defaultBlockState(), 3);
                    }
                }
            }
        }
    }
}
