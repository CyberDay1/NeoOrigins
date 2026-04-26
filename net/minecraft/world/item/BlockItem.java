package net.minecraft.world.item;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;

public class BlockItem extends Item {
    @Deprecated
    private final Block block;

    public BlockItem(Block p_40565_, Item.Properties p_40566_) {
        super(p_40566_);
        this.block = p_40565_;
    }

    @Override
    public InteractionResult useOn(UseOnContext p_40581_) {
        InteractionResult interactionresult = this.place(new BlockPlaceContext(p_40581_));
        if (!interactionresult.consumesAction() && p_40581_.getItemInHand().has(DataComponents.FOOD)) {
            InteractionResult interactionresult1 = super.use(p_40581_.getLevel(), p_40581_.getPlayer(), p_40581_.getHand()).getResult();
            return interactionresult1 == InteractionResult.CONSUME ? InteractionResult.CONSUME_PARTIAL : interactionresult1;
        } else {
            return interactionresult;
        }
    }

    public InteractionResult place(BlockPlaceContext p_40577_) {
        if (!this.getBlock().isEnabled(p_40577_.getLevel().enabledFeatures())) {
            return InteractionResult.FAIL;
        } else if (!p_40577_.canPlace()) {
            return InteractionResult.FAIL;
        } else {
            BlockPlaceContext blockplacecontext = this.updatePlacementContext(p_40577_);
            if (blockplacecontext == null) {
                return InteractionResult.FAIL;
            } else {
                BlockState blockstate = this.getPlacementState(blockplacecontext);
                if (blockstate == null) {
                    return InteractionResult.FAIL;
                } else if (!this.placeBlock(blockplacecontext, blockstate)) {
                    return InteractionResult.FAIL;
                } else {
                    BlockPos blockpos = blockplacecontext.getClickedPos();
                    Level level = blockplacecontext.getLevel();
                    Player player = blockplacecontext.getPlayer();
                    ItemStack itemstack = blockplacecontext.getItemInHand();
                    BlockState blockstate1 = level.getBlockState(blockpos);
                    if (blockstate1.is(blockstate.getBlock())) {
                        blockstate1 = this.updateBlockStateFromTag(blockpos, level, itemstack, blockstate1);
                        this.updateCustomBlockEntityTag(blockpos, level, player, itemstack, blockstate1);
                        updateBlockEntityComponents(level, blockpos, itemstack);
                        blockstate1.getBlock().setPlacedBy(level, blockpos, blockstate1, player, itemstack);
                        if (player instanceof ServerPlayer) {
                            CriteriaTriggers.PLACED_BLOCK.trigger((ServerPlayer)player, blockpos, itemstack);
                        }
                    }

                    SoundType soundtype = blockstate1.getSoundType(level, blockpos, p_40577_.getPlayer());
                    level.playSound(
                        player,
                        blockpos,
                        this.getPlaceSound(blockstate1, level, blockpos, p_40577_.getPlayer()),
                        SoundSource.BLOCKS,
                        (soundtype.getVolume() + 1.0F) / 2.0F,
                        soundtype.getPitch() * 0.8F
                    );
                    level.gameEvent(GameEvent.BLOCK_PLACE, blockpos, GameEvent.Context.of(player, blockstate1));
                    itemstack.consume(1, player);
                    return InteractionResult.sidedSuccess(level.isClientSide);
                }
            }
        }
    }

    @Deprecated //Forge: Use more sensitive version {@link BlockItem#getPlaceSound(BlockState, IBlockReader, BlockPos, Entity) }
    protected SoundEvent getPlaceSound(BlockState p_40588_) {
        return p_40588_.getSoundType().getPlaceSound();
    }

    //Forge: Sensitive version of BlockItem#getPlaceSound
    protected SoundEvent getPlaceSound(BlockState state, Level world, BlockPos pos, Player entity) {
        return state.getSoundType(world, pos, entity).getPlaceSound();
    }

    @Nullable
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext p_40609_) {
        return p_40609_;
    }

    private static void updateBlockEntityComponents(Level p_331559_, BlockPos p_331993_, ItemStack p_332021_) {
        BlockEntity blockentity = p_331559_.getBlockEntity(p_331993_);
        if (blockentity != null) {
            blockentity.applyComponentsFromItemStack(p_332021_);
            blockentity.setChanged();
        }
    }

    protected boolean updateCustomBlockEntityTag(BlockPos p_40597_, Level p_40598_, @Nullable Player p_40599_, ItemStack p_40600_, BlockState p_40601_) {
        return updateCustomBlockEntityTag(p_40598_, p_40599_, p_40597_, p_40600_);
    }

    @Nullable
    protected BlockState getPlacementState(BlockPlaceContext p_40613_) {
        BlockState blockstate = this.getBlock().getStateForPlacement(p_40613_);
        return blockstate != null && this.canPlace(p_40613_, blockstate) ? blockstate : null;
    }

    private BlockState updateBlockStateFromTag(BlockPos p_40603_, Level p_40604_, ItemStack p_40605_, BlockState p_40606_) {
        BlockItemStateProperties blockitemstateproperties = p_40605_.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
        if (blockitemstateproperties.isEmpty()) {
            return p_40606_;
        } else {
            BlockState blockstate = blockitemstateproperties.apply(p_40606_);
            if (blockstate != p_40606_) {
                p_40604_.setBlock(p_40603_, blockstate, 2);
            }

            return blockstate;
        }
    }

    protected boolean canPlace(BlockPlaceContext p_40611_, BlockState p_40612_) {
        Player player = p_40611_.getPlayer();
        CollisionContext collisioncontext = player == null ? CollisionContext.empty() : CollisionContext.of(player);
        return (!this.mustSurvive() || p_40612_.canSurvive(p_40611_.getLevel(), p_40611_.getClickedPos()))
            && p_40611_.getLevel().isUnobstructed(p_40612_, p_40611_.getClickedPos(), collisioncontext);
    }

    protected boolean mustSurvive() {
        return true;
    }

    protected boolean placeBlock(BlockPlaceContext p_40578_, BlockState p_40579_) {
        return p_40578_.getLevel().setBlock(p_40578_.getClickedPos(), p_40579_, 11);
    }

    public static boolean updateCustomBlockEntityTag(Level p_40583_, @Nullable Player p_40584_, BlockPos p_40585_, ItemStack p_40586_) {
        MinecraftServer minecraftserver = p_40583_.getServer();
        if (minecraftserver == null) {
            return false;
        } else {
            CustomData customdata = p_40586_.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY);
            if (!customdata.isEmpty()) {
                BlockEntity blockentity = p_40583_.getBlockEntity(p_40585_);
                if (blockentity != null) {
                    if (p_40583_.isClientSide || !blockentity.onlyOpCanSetNbt() || p_40584_ != null && p_40584_.canUseGameMasterBlocks()) {
                        return customdata.loadInto(blockentity, p_40583_.registryAccess());
                    }

                    return false;
                }
            }

            return false;
        }
    }

    @Override
    public String getDescriptionId() {
        return this.getBlock().getDescriptionId();
    }

    @Override
    public void appendHoverText(ItemStack p_40572_, Item.TooltipContext p_339655_, List<Component> p_40574_, TooltipFlag p_40575_) {
        super.appendHoverText(p_40572_, p_339655_, p_40574_, p_40575_);
        this.getBlock().appendHoverText(p_40572_, p_339655_, p_40574_, p_40575_);
    }

    public Block getBlock() {
        return this.block;
    }

    public void registerBlocks(Map<Block, Item> p_40607_, Item p_40608_) {
        p_40607_.put(this.getBlock(), p_40608_);
    }

    /** @deprecated Neo: To be removed without replacement since registry replacement is not a feature anymore. */
    @Deprecated(forRemoval = true, since = "1.21.1")
    public void removeFromBlockToItemMap(Map<Block, Item> blockToItemMap, Item itemIn) {
        blockToItemMap.remove(this.getBlock());
    }

    @Override
    public boolean canFitInsideContainerItems() {
        return !(this.getBlock() instanceof ShulkerBoxBlock);
    }

    @Override
    public void onDestroyed(ItemEntity p_150700_) {
        ItemContainerContents itemcontainercontents = p_150700_.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (itemcontainercontents != null) {
            ItemUtils.onContainerDestroyed(p_150700_, itemcontainercontents.nonEmptyItemsCopy());
        }
    }

    public static void setBlockEntityData(ItemStack p_186339_, BlockEntityType<?> p_186340_, CompoundTag p_186341_) {
        p_186341_.remove("id");
        if (p_186341_.isEmpty()) {
            p_186339_.remove(DataComponents.BLOCK_ENTITY_DATA);
        } else {
            BlockEntity.addEntityType(p_186341_, p_186340_);
            p_186339_.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(p_186341_));
        }
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        return this.getBlock().requiredFeatures();
    }
}
