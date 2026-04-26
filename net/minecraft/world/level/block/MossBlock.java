package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.CaveFeatures;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

public class MossBlock extends Block implements BonemealableBlock {
    public static final MapCodec<MossBlock> CODEC = simpleCodec(MossBlock::new);

    @Override
    public MapCodec<MossBlock> codec() {
        return CODEC;
    }

    public MossBlock(BlockBehaviour.Properties p_153790_) {
        super(p_153790_);
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader p_256507_, BlockPos p_256224_, BlockState p_256628_) {
        return p_256507_.getBlockState(p_256224_.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level p_221538_, RandomSource p_221539_, BlockPos p_221540_, BlockState p_221541_) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel p_221533_, RandomSource p_221534_, BlockPos p_221535_, BlockState p_221536_) {
        p_221533_.registryAccess()
            .registry(Registries.CONFIGURED_FEATURE)
            .flatMap(p_258973_ -> p_258973_.getHolder(CaveFeatures.MOSS_PATCH_BONEMEAL))
            .ifPresent(p_255669_ -> p_255669_.value().place(p_221533_, p_221533_.getChunkSource().getGenerator(), p_221534_, p_221535_.above()));
    }

    @Override
    public BonemealableBlock.Type getType() {
        return BonemealableBlock.Type.NEIGHBOR_SPREADER;
    }
}
