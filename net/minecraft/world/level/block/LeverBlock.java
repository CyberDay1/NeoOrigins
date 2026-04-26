package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class LeverBlock extends FaceAttachedHorizontalDirectionalBlock {
    public static final MapCodec<LeverBlock> CODEC = simpleCodec(LeverBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    protected static final int DEPTH = 6;
    protected static final int WIDTH = 6;
    protected static final int HEIGHT = 8;
    protected static final VoxelShape NORTH_AABB = Block.box(5.0, 4.0, 10.0, 11.0, 12.0, 16.0);
    protected static final VoxelShape SOUTH_AABB = Block.box(5.0, 4.0, 0.0, 11.0, 12.0, 6.0);
    protected static final VoxelShape WEST_AABB = Block.box(10.0, 4.0, 5.0, 16.0, 12.0, 11.0);
    protected static final VoxelShape EAST_AABB = Block.box(0.0, 4.0, 5.0, 6.0, 12.0, 11.0);
    protected static final VoxelShape UP_AABB_Z = Block.box(5.0, 0.0, 4.0, 11.0, 6.0, 12.0);
    protected static final VoxelShape UP_AABB_X = Block.box(4.0, 0.0, 5.0, 12.0, 6.0, 11.0);
    protected static final VoxelShape DOWN_AABB_Z = Block.box(5.0, 10.0, 4.0, 11.0, 16.0, 12.0);
    protected static final VoxelShape DOWN_AABB_X = Block.box(4.0, 10.0, 5.0, 12.0, 16.0, 11.0);

    @Override
    public MapCodec<LeverBlock> codec() {
        return CODEC;
    }

    public LeverBlock(BlockBehaviour.Properties p_54633_) {
        super(p_54633_);
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, Boolean.valueOf(false)).setValue(FACE, AttachFace.WALL)
        );
    }

    @Override
    protected VoxelShape getShape(BlockState p_54665_, BlockGetter p_54666_, BlockPos p_54667_, CollisionContext p_54668_) {
        switch ((AttachFace)p_54665_.getValue(FACE)) {
            case FLOOR:
                switch (p_54665_.getValue(FACING).getAxis()) {
                    case X:
                        return UP_AABB_X;
                    case Z:
                    default:
                        return UP_AABB_Z;
                }
            case WALL:
                switch ((Direction)p_54665_.getValue(FACING)) {
                    case EAST:
                        return EAST_AABB;
                    case WEST:
                        return WEST_AABB;
                    case SOUTH:
                        return SOUTH_AABB;
                    case NORTH:
                    default:
                        return NORTH_AABB;
                }
            case CEILING:
            default:
                switch (p_54665_.getValue(FACING).getAxis()) {
                    case X:
                        return DOWN_AABB_X;
                    case Z:
                    default:
                        return DOWN_AABB_Z;
                }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_54640_, Level p_54641_, BlockPos p_54642_, Player p_54643_, BlockHitResult p_54645_) {
        if (p_54641_.isClientSide) {
            BlockState blockstate = p_54640_.cycle(POWERED);
            if (blockstate.getValue(POWERED)) {
                makeParticle(blockstate, p_54641_, p_54642_, 1.0F);
            }

            return InteractionResult.SUCCESS;
        } else {
            this.pull(p_54640_, p_54641_, p_54642_, null);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected void onExplosionHit(BlockState p_312405_, Level p_312603_, BlockPos p_311795_, Explosion p_312090_, BiConsumer<ItemStack, BlockPos> p_312313_) {
        if (p_312090_.canTriggerBlocks()) {
            this.pull(p_312405_, p_312603_, p_311795_, null);
        }

        super.onExplosionHit(p_312405_, p_312603_, p_311795_, p_312090_, p_312313_);
    }

    public void pull(BlockState p_54677_, Level p_54678_, BlockPos p_54679_, @Nullable Player p_344918_) {
        p_54677_ = p_54677_.cycle(POWERED);
        p_54678_.setBlock(p_54679_, p_54677_, 3);
        this.updateNeighbours(p_54677_, p_54678_, p_54679_);
        playSound(p_344918_, p_54678_, p_54679_, p_54677_);
        p_54678_.gameEvent(p_344918_, p_54677_.getValue(POWERED) ? GameEvent.BLOCK_ACTIVATE : GameEvent.BLOCK_DEACTIVATE, p_54679_);
    }

    protected static void playSound(@Nullable Player p_346119_, LevelAccessor p_346020_, BlockPos p_345585_, BlockState p_346002_) {
        float f = p_346002_.getValue(POWERED) ? 0.6F : 0.5F;
        p_346020_.playSound(p_346119_, p_345585_, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.3F, f);
    }

    private static void makeParticle(BlockState p_54658_, LevelAccessor p_54659_, BlockPos p_54660_, float p_54661_) {
        Direction direction = p_54658_.getValue(FACING).getOpposite();
        Direction direction1 = getConnectedDirection(p_54658_).getOpposite();
        double d0 = (double)p_54660_.getX() + 0.5 + 0.1 * (double)direction.getStepX() + 0.2 * (double)direction1.getStepX();
        double d1 = (double)p_54660_.getY() + 0.5 + 0.1 * (double)direction.getStepY() + 0.2 * (double)direction1.getStepY();
        double d2 = (double)p_54660_.getZ() + 0.5 + 0.1 * (double)direction.getStepZ() + 0.2 * (double)direction1.getStepZ();
        p_54659_.addParticle(new DustParticleOptions(DustParticleOptions.REDSTONE_PARTICLE_COLOR, p_54661_), d0, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    public void animateTick(BlockState p_221395_, Level p_221396_, BlockPos p_221397_, RandomSource p_221398_) {
        if (p_221395_.getValue(POWERED) && p_221398_.nextFloat() < 0.25F) {
            makeParticle(p_221395_, p_221396_, p_221397_, 0.5F);
        }
    }

    @Override
    protected void onRemove(BlockState p_54647_, Level p_54648_, BlockPos p_54649_, BlockState p_54650_, boolean p_54651_) {
        if (!p_54651_ && !p_54647_.is(p_54650_.getBlock())) {
            if (p_54647_.getValue(POWERED)) {
                this.updateNeighbours(p_54647_, p_54648_, p_54649_);
            }

            super.onRemove(p_54647_, p_54648_, p_54649_, p_54650_, p_54651_);
        }
    }

    @Override
    protected int getSignal(BlockState p_54635_, BlockGetter p_54636_, BlockPos p_54637_, Direction p_54638_) {
        return p_54635_.getValue(POWERED) ? 15 : 0;
    }

    @Override
    protected int getDirectSignal(BlockState p_54670_, BlockGetter p_54671_, BlockPos p_54672_, Direction p_54673_) {
        return p_54670_.getValue(POWERED) && getConnectedDirection(p_54670_) == p_54673_ ? 15 : 0;
    }

    @Override
    protected boolean isSignalSource(BlockState p_54675_) {
        return true;
    }

    private void updateNeighbours(BlockState p_54681_, Level p_54682_, BlockPos p_54683_) {
        p_54682_.updateNeighborsAt(p_54683_, this);
        p_54682_.updateNeighborsAt(p_54683_.relative(getConnectedDirection(p_54681_).getOpposite()), this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_54663_) {
        p_54663_.add(FACE, FACING, POWERED);
    }
}
