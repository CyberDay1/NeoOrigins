package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CakeBlock extends Block {
    public static final MapCodec<CakeBlock> CODEC = simpleCodec(CakeBlock::new);
    public static final int MAX_BITES = 6;
    public static final IntegerProperty BITES = BlockStateProperties.BITES;
    public static final int FULL_CAKE_SIGNAL = getOutputSignal(0);
    protected static final float AABB_OFFSET = 1.0F;
    protected static final float AABB_SIZE_PER_BITE = 2.0F;
    protected static final VoxelShape[] SHAPE_BY_BITE = new VoxelShape[]{
        Block.box(1.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(3.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(5.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(7.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(9.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(11.0, 0.0, 1.0, 15.0, 8.0, 15.0),
        Block.box(13.0, 0.0, 1.0, 15.0, 8.0, 15.0)
    };

    @Override
    public MapCodec<CakeBlock> codec() {
        return CODEC;
    }

    public CakeBlock(BlockBehaviour.Properties p_51184_) {
        super(p_51184_);
        this.registerDefaultState(this.stateDefinition.any().setValue(BITES, Integer.valueOf(0)));
    }

    @Override
    protected VoxelShape getShape(BlockState p_51222_, BlockGetter p_51223_, BlockPos p_51224_, CollisionContext p_51225_) {
        return SHAPE_BY_BITE[p_51222_.getValue(BITES)];
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316238_, BlockState p_316837_, Level p_316766_, BlockPos p_316227_, Player p_316853_, InteractionHand p_316422_, BlockHitResult p_316869_
    ) {
        Item item = p_316238_.getItem();
        if (p_316238_.is(ItemTags.CANDLES) && p_316837_.getValue(BITES) == 0 && Block.byItem(item) instanceof CandleBlock candleblock) {
            p_316238_.consume(1, p_316853_);
            p_316766_.playSound(null, p_316227_, SoundEvents.CAKE_ADD_CANDLE, SoundSource.BLOCKS, 1.0F, 1.0F);
            p_316766_.setBlockAndUpdate(p_316227_, CandleCakeBlock.byCandle(candleblock));
            p_316766_.gameEvent(p_316853_, GameEvent.BLOCK_CHANGE, p_316227_);
            p_316853_.awardStat(Stats.ITEM_USED.get(item));
            return ItemInteractionResult.SUCCESS;
        } else {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316481_, Level p_316406_, BlockPos p_316218_, Player p_316212_, BlockHitResult p_316525_) {
        if (p_316406_.isClientSide) {
            if (eat(p_316406_, p_316218_, p_316481_, p_316212_).consumesAction()) {
                return InteractionResult.SUCCESS;
            }

            if (p_316212_.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
                return InteractionResult.CONSUME;
            }
        }

        return eat(p_316406_, p_316218_, p_316481_, p_316212_);
    }

    protected static InteractionResult eat(LevelAccessor p_51186_, BlockPos p_51187_, BlockState p_51188_, Player p_51189_) {
        if (!p_51189_.canEat(false)) {
            return InteractionResult.PASS;
        } else {
            p_51189_.awardStat(Stats.EAT_CAKE_SLICE);
            p_51189_.getFoodData().eat(2, 0.1F);
            int i = p_51188_.getValue(BITES);
            p_51186_.gameEvent(p_51189_, GameEvent.EAT, p_51187_);
            if (i < 6) {
                p_51186_.setBlock(p_51187_, p_51188_.setValue(BITES, Integer.valueOf(i + 1)), 3);
            } else {
                p_51186_.removeBlock(p_51187_, false);
                p_51186_.gameEvent(p_51189_, GameEvent.BLOCK_DESTROY, p_51187_);
            }

            return InteractionResult.SUCCESS;
        }
    }

    @Override
    protected BlockState updateShape(BlockState p_51213_, Direction p_51214_, BlockState p_51215_, LevelAccessor p_51216_, BlockPos p_51217_, BlockPos p_51218_) {
        return p_51214_ == Direction.DOWN && !p_51213_.canSurvive(p_51216_, p_51217_)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(p_51213_, p_51214_, p_51215_, p_51216_, p_51217_, p_51218_);
    }

    @Override
    protected boolean canSurvive(BlockState p_51209_, LevelReader p_51210_, BlockPos p_51211_) {
        return p_51210_.getBlockState(p_51211_.below()).isSolid();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51220_) {
        p_51220_.add(BITES);
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_51198_, Level p_51199_, BlockPos p_51200_) {
        return getOutputSignal(p_51198_.getValue(BITES));
    }

    public static int getOutputSignal(int p_152747_) {
        return (7 - p_152747_) * 2;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_51191_) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState p_51193_, PathComputationType p_51196_) {
        return false;
    }
}
