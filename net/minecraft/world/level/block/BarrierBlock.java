package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public class BarrierBlock extends Block implements SimpleWaterloggedBlock {
    public static final MapCodec<BarrierBlock> CODEC = simpleCodec(BarrierBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @Override
    public MapCodec<BarrierBlock> codec() {
        return CODEC;
    }

    public BarrierBlock(BlockBehaviour.Properties p_49092_) {
        super(p_49092_);
        this.registerDefaultState(this.defaultBlockState().setValue(WATERLOGGED, Boolean.valueOf(false)));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState p_49100_, BlockGetter p_49101_, BlockPos p_49102_) {
        return p_49100_.getFluidState().isEmpty();
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_49098_) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected float getShadeBrightness(BlockState p_49094_, BlockGetter p_49095_, BlockPos p_49096_) {
        return 1.0F;
    }

    @Override
    protected BlockState updateShape(
        BlockState p_296123_, Direction p_294509_, BlockState p_296367_, LevelAccessor p_294373_, BlockPos p_294499_, BlockPos p_295044_
    ) {
        if (p_296123_.getValue(WATERLOGGED)) {
            p_294373_.scheduleTick(p_294499_, Fluids.WATER, Fluids.WATER.getTickDelay(p_294373_));
        }

        return super.updateShape(p_296123_, p_294509_, p_296367_, p_294373_, p_294499_, p_295044_);
    }

    @Override
    protected FluidState getFluidState(BlockState p_296372_) {
        return p_296372_.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(p_296372_);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_295385_) {
        return this.defaultBlockState()
            .setValue(WATERLOGGED, Boolean.valueOf(p_295385_.getLevel().getFluidState(p_295385_.getClickedPos()).getType() == Fluids.WATER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_296107_) {
        p_296107_.add(WATERLOGGED);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player p_295338_, LevelAccessor p_295817_, BlockPos p_295857_, BlockState p_294189_) {
        return p_295338_ != null && p_295338_.isCreative()
            ? SimpleWaterloggedBlock.super.pickupBlock(p_295338_, p_295817_, p_295857_, p_294189_)
            : ItemStack.EMPTY;
    }

    @Override
    public boolean canPlaceLiquid(@Nullable Player p_296463_, BlockGetter p_295048_, BlockPos p_295143_, BlockState p_294224_, Fluid p_294851_) {
        return p_296463_ != null && p_296463_.isCreative()
            ? SimpleWaterloggedBlock.super.canPlaceLiquid(p_296463_, p_295048_, p_295143_, p_294224_, p_294851_)
            : false;
    }
}
