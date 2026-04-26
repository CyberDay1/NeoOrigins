package net.minecraft.world.level.block;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class CandleCakeBlock extends AbstractCandleBlock {
    public static final MapCodec<CandleCakeBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_344652_ -> p_344652_.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("candle").forGetter(p_316072_ -> p_316072_.candleBlock), propertiesCodec())
                .apply(p_344652_, CandleCakeBlock::new)
    );
    public static final BooleanProperty LIT = AbstractCandleBlock.LIT;
    protected static final float AABB_OFFSET = 1.0F;
    protected static final VoxelShape CAKE_SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 8.0, 15.0);
    protected static final VoxelShape CANDLE_SHAPE = Block.box(7.0, 8.0, 7.0, 9.0, 14.0, 9.0);
    protected static final VoxelShape SHAPE = Shapes.or(CAKE_SHAPE, CANDLE_SHAPE);
    private static final Map<CandleBlock, CandleCakeBlock> BY_CANDLE = Maps.newHashMap();
    private static final Iterable<Vec3> PARTICLE_OFFSETS = ImmutableList.of(new Vec3(0.5, 1.0, 0.5));
    private final CandleBlock candleBlock;

    @Override
    public MapCodec<CandleCakeBlock> codec() {
        return CODEC;
    }

    public CandleCakeBlock(Block p_152859_, BlockBehaviour.Properties p_152860_) {
        super(p_152860_);
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.valueOf(false)));
        if (p_152859_ instanceof CandleBlock candleblock) {
            BY_CANDLE.put(candleblock, this);
            this.candleBlock = candleblock;
        } else {
            throw new IllegalArgumentException("Expected block to be of " + CandleBlock.class + " was " + p_152859_.getClass());
        }
    }

    @Override
    protected Iterable<Vec3> getParticleOffsets(BlockState p_152868_) {
        return PARTICLE_OFFSETS;
    }

    @Override
    protected VoxelShape getShape(BlockState p_152875_, BlockGetter p_152876_, BlockPos p_152877_, CollisionContext p_152878_) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316571_, BlockState p_316514_, Level p_316171_, BlockPos p_316112_, Player p_316172_, InteractionHand p_316257_, BlockHitResult p_316286_
    ) {
        if (p_316571_.is(Items.FLINT_AND_STEEL) || p_316571_.is(Items.FIRE_CHARGE)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        } else if (candleHit(p_316286_) && p_316571_.isEmpty() && p_316514_.getValue(LIT)) {
            extinguish(p_316172_, p_316514_, p_316171_, p_316112_);
            return ItemInteractionResult.sidedSuccess(p_316171_.isClientSide);
        } else {
            return super.useItemOn(p_316571_, p_316514_, p_316171_, p_316112_, p_316172_, p_316257_, p_316286_);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316519_, Level p_316226_, BlockPos p_316122_, Player p_316438_, BlockHitResult p_316849_) {
        InteractionResult interactionresult = CakeBlock.eat(p_316226_, p_316122_, Blocks.CAKE.defaultBlockState(), p_316438_);
        if (interactionresult.consumesAction()) {
            dropResources(p_316519_, p_316226_, p_316122_);
        }

        return interactionresult;
    }

    private static boolean candleHit(BlockHitResult p_152907_) {
        return p_152907_.getLocation().y - (double)p_152907_.getBlockPos().getY() > 0.5;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_152905_) {
        p_152905_.add(LIT);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304662_, BlockPos p_152863_, BlockState p_152864_) {
        return new ItemStack(Blocks.CAKE);
    }

    @Override
    protected BlockState updateShape(
        BlockState p_152898_, Direction p_152899_, BlockState p_152900_, LevelAccessor p_152901_, BlockPos p_152902_, BlockPos p_152903_
    ) {
        return p_152899_ == Direction.DOWN && !p_152898_.canSurvive(p_152901_, p_152902_)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(p_152898_, p_152899_, p_152900_, p_152901_, p_152902_, p_152903_);
    }

    @Override
    protected boolean canSurvive(BlockState p_152891_, LevelReader p_152892_, BlockPos p_152893_) {
        return p_152892_.getBlockState(p_152893_.below()).isSolid();
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_152880_, Level p_152881_, BlockPos p_152882_) {
        return CakeBlock.FULL_CAKE_SIGNAL;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_152909_) {
        return true;
    }

    @Override
    protected boolean isPathfindable(BlockState p_152870_, PathComputationType p_152873_) {
        return false;
    }

    public static BlockState byCandle(CandleBlock p_316552_) {
        return BY_CANDLE.get(p_316552_).defaultBlockState();
    }

    public static boolean canLight(BlockState p_152911_) {
        return p_152911_.is(BlockTags.CANDLE_CAKES, p_152896_ -> p_152896_.hasProperty(LIT) && !p_152911_.getValue(LIT));
    }
}
