package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TallGrassBlock extends BushBlock implements BonemealableBlock, net.neoforged.neoforge.common.IShearable {
    public static final MapCodec<TallGrassBlock> CODEC = simpleCodec(TallGrassBlock::new);
    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 13.0, 14.0);

    @Override
    public MapCodec<TallGrassBlock> codec() {
        return CODEC;
    }

    public TallGrassBlock(BlockBehaviour.Properties p_57318_) {
        super(p_57318_);
    }

    @Override
    protected VoxelShape getShape(BlockState p_57336_, BlockGetter p_57337_, BlockPos p_57338_, CollisionContext p_57339_) {
        return SHAPE;
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader p_255692_, BlockPos p_57326_, BlockState p_57327_) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level p_222583_, RandomSource p_222584_, BlockPos p_222585_, BlockState p_222586_) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel p_222578_, RandomSource p_222579_, BlockPos p_222580_, BlockState p_222581_) {
        DoublePlantBlock doubleplantblock = (DoublePlantBlock)(p_222581_.is(Blocks.FERN) ? Blocks.LARGE_FERN : Blocks.TALL_GRASS);
        if (doubleplantblock.defaultBlockState().canSurvive(p_222578_, p_222580_) && p_222578_.isEmptyBlock(p_222580_.above())) {
            DoublePlantBlock.placeAt(p_222578_, doubleplantblock.defaultBlockState(), p_222580_, 2);
        }
    }
}
