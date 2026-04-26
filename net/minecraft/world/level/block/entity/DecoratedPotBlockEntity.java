package net.minecraft.world.level.block.entity;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.ticks.ContainerSingleItem;

public class DecoratedPotBlockEntity extends BlockEntity implements RandomizableContainer, ContainerSingleItem.BlockContainerSingleItem {
    public static final String TAG_SHERDS = "sherds";
    public static final String TAG_ITEM = "item";
    public static final int EVENT_POT_WOBBLES = 1;
    public long wobbleStartedAtTick;
    @Nullable
    public DecoratedPotBlockEntity.WobbleStyle lastWobbleStyle;
    private PotDecorations decorations;
    private ItemStack item = ItemStack.EMPTY;
    @Nullable
    protected ResourceKey<LootTable> lootTable;
    protected long lootTableSeed;

    public DecoratedPotBlockEntity(BlockPos p_273660_, BlockState p_272831_) {
        super(BlockEntityType.DECORATED_POT, p_273660_, p_272831_);
        this.decorations = PotDecorations.EMPTY;
    }

    @Override
    protected void saveAdditional(CompoundTag p_272957_, HolderLookup.Provider p_323719_) {
        super.saveAdditional(p_272957_, p_323719_);
        this.decorations.save(p_272957_);
        if (!this.trySaveLootTable(p_272957_) && !this.item.isEmpty()) {
            p_272957_.put("item", this.item.save(p_323719_));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag p_338486_, HolderLookup.Provider p_338310_) {
        super.loadAdditional(p_338486_, p_338310_);
        this.decorations = PotDecorations.load(p_338486_);
        if (!this.tryLoadLootTable(p_338486_)) {
            if (p_338486_.contains("item", 10)) {
                this.item = ItemStack.parse(p_338310_, p_338486_.getCompound("item")).orElse(ItemStack.EMPTY);
            } else {
                this.item = ItemStack.EMPTY;
            }
        }
    }

    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider p_324359_) {
        return this.saveCustomOnly(p_324359_);
    }

    public Direction getDirection() {
        return this.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    public PotDecorations getDecorations() {
        return this.decorations;
    }

    public void setFromItem(ItemStack p_273109_) {
        this.applyComponentsFromItemStack(p_273109_);
    }

    public ItemStack getPotAsItem() {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();
        itemstack.applyComponents(this.collectComponents());
        return itemstack;
    }

    public static ItemStack createDecoratedPotItem(PotDecorations p_330827_) {
        ItemStack itemstack = Items.DECORATED_POT.getDefaultInstance();
        itemstack.set(DataComponents.POT_DECORATIONS, p_330827_);
        return itemstack;
    }

    @Nullable
    @Override
    public ResourceKey<LootTable> getLootTable() {
        return this.lootTable;
    }

    @Override
    public void setLootTable(@Nullable ResourceKey<LootTable> p_336080_) {
        this.lootTable = p_336080_;
    }

    @Override
    public long getLootTableSeed() {
        return this.lootTableSeed;
    }

    @Override
    public void setLootTableSeed(long p_309580_) {
        this.lootTableSeed = p_309580_;
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder p_338608_) {
        super.collectImplicitComponents(p_338608_);
        p_338608_.set(DataComponents.POT_DECORATIONS, this.decorations);
        p_338608_.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(this.item)));
    }

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput p_338521_) {
        super.applyImplicitComponents(p_338521_);
        this.decorations = p_338521_.getOrDefault(DataComponents.POT_DECORATIONS, PotDecorations.EMPTY);
        this.item = p_338521_.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyOne();
    }

    @Override
    public void removeComponentsFromTag(CompoundTag p_330569_) {
        super.removeComponentsFromTag(p_330569_);
        p_330569_.remove("sherds");
        p_330569_.remove("item");
    }

    @Override
    public ItemStack getTheItem() {
        this.unpackLootTable(null);
        return this.item;
    }

    @Override
    public ItemStack splitTheItem(int p_305991_) {
        this.unpackLootTable(null);
        ItemStack itemstack = this.item.split(p_305991_);
        if (this.item.isEmpty()) {
            this.item = ItemStack.EMPTY;
        }

        return itemstack;
    }

    @Override
    public void setTheItem(ItemStack p_305817_) {
        this.unpackLootTable(null);
        this.item = p_305817_;
    }

    @Override
    public BlockEntity getContainerBlockEntity() {
        return this;
    }

    public void wobble(DecoratedPotBlockEntity.WobbleStyle p_305984_) {
        if (this.level != null && !this.level.isClientSide()) {
            this.level.blockEvent(this.getBlockPos(), this.getBlockState().getBlock(), 1, p_305984_.ordinal());
        }
    }

    @Override
    public boolean triggerEvent(int p_306146_, int p_305858_) {
        if (this.level != null && p_306146_ == 1 && p_305858_ >= 0 && p_305858_ < DecoratedPotBlockEntity.WobbleStyle.values().length) {
            this.wobbleStartedAtTick = this.level.getGameTime();
            this.lastWobbleStyle = DecoratedPotBlockEntity.WobbleStyle.values()[p_305858_];
            return true;
        } else {
            return super.triggerEvent(p_306146_, p_305858_);
        }
    }

    public static enum WobbleStyle {
        POSITIVE(7),
        NEGATIVE(10);

        public final int duration;

        private WobbleStyle(int p_305780_) {
            this.duration = p_305780_;
        }
    }
}
