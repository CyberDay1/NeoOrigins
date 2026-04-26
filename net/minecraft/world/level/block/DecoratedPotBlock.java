package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DecoratedPotBlockEntity;
import net.minecraft.world.level.block.entity.PotDecorations;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class DecoratedPotBlock extends BaseEntityBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<DecoratedPotBlock> CODEC = simpleCodec(DecoratedPotBlock::new);
    public static final ResourceLocation SHERDS_DYNAMIC_DROP_ID = ResourceLocation.withDefaultNamespace("sherds");
    private static final VoxelShape BOUNDING_BOX = Block.box(1.0, 0.0, 1.0, 15.0, 16.0, 15.0);
    private static final DirectionProperty HORIZONTAL_FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CRACKED = BlockStateProperties.CRACKED;
    private static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    @Override
    public MapCodec<DecoratedPotBlock> codec() {
        return CODEC;
    }

    public DecoratedPotBlock(BlockBehaviour.Properties p_273064_) {
        super(p_273064_);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(HORIZONTAL_FACING, Direction.NORTH)
                .setValue(WATERLOGGED, Boolean.valueOf(false))
                .setValue(CRACKED, Boolean.valueOf(false))
        );
    }

    @Override
    protected BlockState updateShape(
        BlockState p_276307_, Direction p_276322_, BlockState p_276280_, LevelAccessor p_276320_, BlockPos p_276270_, BlockPos p_276312_
    ) {
        if (p_276307_.getValue(WATERLOGGED)) {
            p_276320_.scheduleTick(p_276270_, Fluids.WATER, Fluids.WATER.getTickDelay(p_276320_));
        }

        return super.updateShape(p_276307_, p_276322_, p_276280_, p_276320_, p_276270_, p_276312_);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_272711_) {
        FluidState fluidstate = p_272711_.getLevel().getFluidState(p_272711_.getClickedPos());
        return this.defaultBlockState()
            .setValue(HORIZONTAL_FACING, p_272711_.getHorizontalDirection())
            .setValue(WATERLOGGED, Boolean.valueOf(fluidstate.getType() == Fluids.WATER))
            .setValue(CRACKED, Boolean.valueOf(false));
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316569_, BlockState p_316562_, Level p_316177_, BlockPos p_316898_, Player p_316632_, InteractionHand p_316424_, BlockHitResult p_316345_
    ) {
        if (p_316177_.getBlockEntity(p_316898_) instanceof DecoratedPotBlockEntity decoratedpotblockentity) {
            if (p_316177_.isClientSide) {
                return ItemInteractionResult.CONSUME;
            } else {
                ItemStack itemstack1 = decoratedpotblockentity.getTheItem();
                if (!p_316569_.isEmpty()
                    && (
                        itemstack1.isEmpty()
                            || ItemStack.isSameItemSameComponents(itemstack1, p_316569_) && itemstack1.getCount() < itemstack1.getMaxStackSize()
                    )) {
                    decoratedpotblockentity.wobble(DecoratedPotBlockEntity.WobbleStyle.POSITIVE);
                    p_316632_.awardStat(Stats.ITEM_USED.get(p_316569_.getItem()));
                    ItemStack itemstack = p_316569_.consumeAndReturn(1, p_316632_);
                    float f;
                    if (decoratedpotblockentity.isEmpty()) {
                        decoratedpotblockentity.setTheItem(itemstack);
                        f = (float)itemstack.getCount() / (float)itemstack.getMaxStackSize();
                    } else {
                        itemstack1.grow(1);
                        f = (float)itemstack1.getCount() / (float)itemstack1.getMaxStackSize();
                    }

                    p_316177_.playSound(null, p_316898_, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, 1.0F, 0.7F + 0.5F * f);
                    if (p_316177_ instanceof ServerLevel serverlevel) {
                        serverlevel.sendParticles(
                            ParticleTypes.DUST_PLUME,
                            (double)p_316898_.getX() + 0.5,
                            (double)p_316898_.getY() + 1.2,
                            (double)p_316898_.getZ() + 0.5,
                            7,
                            0.0,
                            0.0,
                            0.0,
                            0.0
                        );
                    }

                    decoratedpotblockentity.setChanged();
                    p_316177_.gameEvent(p_316632_, GameEvent.BLOCK_CHANGE, p_316898_);
                    return ItemInteractionResult.SUCCESS;
                } else {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        } else {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316866_, Level p_316544_, BlockPos p_316541_, Player p_316732_, BlockHitResult p_316860_) {
        if (p_316544_.getBlockEntity(p_316541_) instanceof DecoratedPotBlockEntity decoratedpotblockentity) {
            p_316544_.playSound(null, p_316541_, SoundEvents.DECORATED_POT_INSERT_FAIL, SoundSource.BLOCKS, 1.0F, 1.0F);
            decoratedpotblockentity.wobble(DecoratedPotBlockEntity.WobbleStyle.NEGATIVE);
            p_316544_.gameEvent(p_316732_, GameEvent.BLOCK_CHANGE, p_316541_);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.PASS;
        }
    }

    @Override
    protected boolean isPathfindable(BlockState p_276295_, PathComputationType p_276303_) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState p_273112_, BlockGetter p_273055_, BlockPos p_273137_, CollisionContext p_273151_) {
        return BOUNDING_BOX;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_273169_) {
        p_273169_.add(HORIZONTAL_FACING, WATERLOGGED, CRACKED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_273396_, BlockState p_272674_) {
        return new DecoratedPotBlockEntity(p_273396_, p_272674_);
    }

    @Override
    protected void onRemove(BlockState p_305821_, Level p_306245_, BlockPos p_305894_, BlockState p_306294_, boolean p_306159_) {
        Containers.dropContentsOnDestroy(p_305821_, p_306294_, p_306245_, p_305894_);
        super.onRemove(p_305821_, p_306245_, p_305894_, p_306294_, p_306159_);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState p_287683_, LootParams.Builder p_287582_) {
        BlockEntity blockentity = p_287582_.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockentity instanceof DecoratedPotBlockEntity decoratedpotblockentity) {
            p_287582_.withDynamicDrop(SHERDS_DYNAMIC_DROP_ID, p_330132_ -> {
                for (Item item : decoratedpotblockentity.getDecorations().ordered()) {
                    p_330132_.accept(item.getDefaultInstance());
                }
            });
        }

        return super.getDrops(p_287683_, p_287582_);
    }

    @Override
    public BlockState playerWillDestroy(Level p_273590_, BlockPos p_273343_, BlockState p_272869_, Player p_273002_) {
        ItemStack itemstack = p_273002_.getMainHandItem();
        BlockState blockstate = p_272869_;
        if (itemstack.is(ItemTags.BREAKS_DECORATED_POTS) && !EnchantmentHelper.hasTag(itemstack, EnchantmentTags.PREVENTS_DECORATED_POT_SHATTERING)) {
            blockstate = p_272869_.setValue(CRACKED, Boolean.valueOf(true));
            p_273590_.setBlock(p_273343_, blockstate, 4);
        }

        return super.playerWillDestroy(p_273590_, p_273343_, blockstate, p_273002_);
    }

    @Override
    protected FluidState getFluidState(BlockState p_272593_) {
        return p_272593_.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(p_272593_);
    }

    @Override
    protected SoundType getSoundType(BlockState p_277561_) {
        return p_277561_.getValue(CRACKED) ? SoundType.DECORATED_POT_CRACKED : SoundType.DECORATED_POT;
    }

    @Override
    public void appendHoverText(ItemStack p_285238_, Item.TooltipContext p_339662_, List<Component> p_285448_, TooltipFlag p_284997_) {
        super.appendHoverText(p_285238_, p_339662_, p_285448_, p_284997_);
        PotDecorations potdecorations = p_285238_.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        if (!potdecorations.equals(PotDecorations.EMPTY)) {
            p_285448_.add(CommonComponents.EMPTY);
            Stream.of(potdecorations.front(), potdecorations.left(), potdecorations.right(), potdecorations.back())
                .forEach(p_330130_ -> p_285448_.add(new ItemStack(p_330130_.orElse(Items.BRICK), 1).getHoverName().plainCopy().withStyle(ChatFormatting.GRAY)));
        }
    }

    @Override
    protected void onProjectileHit(Level p_306322_, BlockState p_306005_, BlockHitResult p_306105_, Projectile p_305851_) {
        BlockPos blockpos = p_306105_.getBlockPos();
        if (!p_306322_.isClientSide && p_305851_.mayInteract(p_306322_, blockpos) && p_305851_.mayBreak(p_306322_)) {
            p_306322_.setBlock(blockpos, p_306005_.setValue(CRACKED, Boolean.valueOf(true)), 4);
            p_306322_.destroyBlock(blockpos, true, p_305851_);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304622_, BlockPos p_294412_, BlockState p_294723_) {
        return p_304622_.getBlockEntity(p_294412_) instanceof DecoratedPotBlockEntity decoratedpotblockentity
            ? decoratedpotblockentity.getPotAsItem()
            : super.getCloneItemStack(p_304622_, p_294412_, p_294723_);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_305995_) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_306206_, Level p_306113_, BlockPos p_306305_) {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(p_306113_.getBlockEntity(p_306305_));
    }

    @Override
    protected BlockState rotate(BlockState p_333895_, Rotation p_333806_) {
        return p_333895_.setValue(HORIZONTAL_FACING, p_333806_.rotate(p_333895_.getValue(HORIZONTAL_FACING)));
    }

    @Override
    protected BlockState mirror(BlockState p_334078_, Mirror p_333905_) {
        return p_334078_.rotate(p_333905_.getRotation(p_334078_.getValue(HORIZONTAL_FACING)));
    }
}
