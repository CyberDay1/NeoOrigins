package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;

public abstract class AbstractSkullBlock extends BaseEntityBlock implements Equipable {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    private final SkullBlock.Type type;

    public AbstractSkullBlock(SkullBlock.Type p_48745_, BlockBehaviour.Properties p_48746_) {
        super(p_48746_);
        this.type = p_48745_;
        this.registerDefaultState(this.stateDefinition.any().setValue(POWERED, Boolean.valueOf(false)));
    }

    @Override
    protected abstract MapCodec<? extends AbstractSkullBlock> codec();

    @Override
    public BlockEntity newBlockEntity(BlockPos p_151996_, BlockState p_151997_) {
        return new SkullBlockEntity(p_151996_, p_151997_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_151992_, BlockState p_151993_, BlockEntityType<T> p_151994_) {
        if (p_151992_.isClientSide) {
            boolean flag = p_151993_.is(Blocks.DRAGON_HEAD)
                || p_151993_.is(Blocks.DRAGON_WALL_HEAD)
                || p_151993_.is(Blocks.PIGLIN_HEAD)
                || p_151993_.is(Blocks.PIGLIN_WALL_HEAD);
            if (flag) {
                return createTickerHelper(p_151994_, BlockEntityType.SKULL, SkullBlockEntity::animation);
            }
        }

        return null;
    }

    public SkullBlock.Type getType() {
        return this.type;
    }

    @Override
    protected boolean isPathfindable(BlockState p_48750_, PathComputationType p_48753_) {
        return false;
    }

    @Override
    public EquipmentSlot getEquipmentSlot() {
        return EquipmentSlot.HEAD;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_300993_) {
        p_300993_.add(POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_300939_) {
        return this.defaultBlockState().setValue(POWERED, Boolean.valueOf(p_300939_.getLevel().hasNeighborSignal(p_300939_.getClickedPos())));
    }

    @Override
    protected void neighborChanged(BlockState p_301179_, Level p_301325_, BlockPos p_301085_, Block p_301182_, BlockPos p_301255_, boolean p_300890_) {
        if (!p_301325_.isClientSide) {
            boolean flag = p_301325_.hasNeighborSignal(p_301085_);
            if (flag != p_301179_.getValue(POWERED)) {
                p_301325_.setBlock(p_301085_, p_301179_.setValue(POWERED, Boolean.valueOf(flag)), 2);
            }
        }
    }
}
