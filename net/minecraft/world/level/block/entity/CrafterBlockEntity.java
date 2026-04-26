package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class CrafterBlockEntity extends RandomizableContainerBlockEntity implements CraftingContainer {
    public static final int CONTAINER_WIDTH = 3;
    public static final int CONTAINER_HEIGHT = 3;
    public static final int CONTAINER_SIZE = 9;
    public static final int SLOT_DISABLED = 1;
    public static final int SLOT_ENABLED = 0;
    public static final int DATA_TRIGGERED = 9;
    public static final int NUM_DATA = 10;
    private NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
    private int craftingTicksRemaining = 0;
    protected final ContainerData containerData = new ContainerData() {
        private final int[] slotStates = new int[9];
        private int triggered = 0;

        @Override
        public int get(int p_307671_) {
            return p_307671_ == 9 ? this.triggered : this.slotStates[p_307671_];
        }

        @Override
        public void set(int p_307241_, int p_307484_) {
            if (p_307241_ == 9) {
                this.triggered = p_307484_;
            } else {
                this.slotStates[p_307241_] = p_307484_;
            }
        }

        @Override
        public int getCount() {
            return 10;
        }
    };

    public CrafterBlockEntity(BlockPos p_307502_, BlockState p_307485_) {
        super(BlockEntityType.CRAFTER, p_307502_, p_307485_);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.crafter");
    }

    @Override
    protected AbstractContainerMenu createMenu(int p_307441_, Inventory p_307664_) {
        return new CrafterMenu(p_307441_, p_307664_, this, this.containerData);
    }

    public void setSlotState(int p_307571_, boolean p_307624_) {
        if (this.slotCanBeDisabled(p_307571_)) {
            this.containerData.set(p_307571_, p_307624_ ? 0 : 1);
            this.setChanged();
        }
    }

    public boolean isSlotDisabled(int p_307461_) {
        return p_307461_ >= 0 && p_307461_ < 9 ? this.containerData.get(p_307461_) == 1 : false;
    }

    @Override
    public boolean canPlaceItem(int p_307543_, ItemStack p_307267_) {
        if (this.containerData.get(p_307543_) == 1) {
            return false;
        } else {
            ItemStack itemstack = this.items.get(p_307543_);
            int i = itemstack.getCount();
            if (i >= itemstack.getMaxStackSize()) {
                return false;
            } else {
                return itemstack.isEmpty() ? true : !this.smallerStackExist(i, itemstack, p_307543_);
            }
        }
    }

    private boolean smallerStackExist(int p_307396_, ItemStack p_307520_, int p_307348_) {
        for (int i = p_307348_ + 1; i < 9; i++) {
            if (!this.isSlotDisabled(i)) {
                ItemStack itemstack = this.getItem(i);
                if (itemstack.isEmpty() || itemstack.getCount() < p_307396_ && ItemStack.isSameItemSameComponents(itemstack, p_307520_)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    protected void loadAdditional(CompoundTag p_307457_, HolderLookup.Provider p_324538_) {
        super.loadAdditional(p_307457_, p_324538_);
        this.craftingTicksRemaining = p_307457_.getInt("crafting_ticks_remaining");
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(p_307457_)) {
            ContainerHelper.loadAllItems(p_307457_, this.items, p_324538_);
        }

        int[] aint = p_307457_.getIntArray("disabled_slots");

        for (int i = 0; i < 9; i++) {
            this.containerData.set(i, 0);
        }

        for (int j : aint) {
            if (this.slotCanBeDisabled(j)) {
                this.containerData.set(j, 1);
            }
        }

        this.containerData.set(9, p_307457_.getInt("triggered"));
    }

    @Override
    protected void saveAdditional(CompoundTag p_307531_, HolderLookup.Provider p_324381_) {
        super.saveAdditional(p_307531_, p_324381_);
        p_307531_.putInt("crafting_ticks_remaining", this.craftingTicksRemaining);
        if (!this.trySaveLootTable(p_307531_)) {
            ContainerHelper.saveAllItems(p_307531_, this.items, p_324381_);
        }

        this.addDisabledSlots(p_307531_);
        this.addTriggered(p_307531_);
    }

    @Override
    public int getContainerSize() {
        return 9;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : this.items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public ItemStack getItem(int p_307189_) {
        return this.items.get(p_307189_);
    }

    @Override
    public void setItem(int p_307195_, ItemStack p_307232_) {
        if (this.isSlotDisabled(p_307195_)) {
            this.setSlotState(p_307195_, true);
        }

        super.setItem(p_307195_, p_307232_);
    }

    @Override
    public boolean stillValid(Player p_307443_) {
        return Container.stillValidBlockEntity(this, p_307443_);
    }

    @Override
    public NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> p_307392_) {
        this.items = p_307392_;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public void fillStackedContents(StackedContents p_307380_) {
        for (ItemStack itemstack : this.items) {
            p_307380_.accountSimpleStack(itemstack);
        }
    }

    private void addDisabledSlots(CompoundTag p_307523_) {
        IntList intlist = new IntArrayList();

        for (int i = 0; i < 9; i++) {
            if (this.isSlotDisabled(i)) {
                intlist.add(i);
            }
        }

        p_307523_.putIntArray("disabled_slots", intlist);
    }

    private void addTriggered(CompoundTag p_307675_) {
        p_307675_.putInt("triggered", this.containerData.get(9));
    }

    public void setTriggered(boolean p_307366_) {
        this.containerData.set(9, p_307366_ ? 1 : 0);
    }

    @VisibleForTesting
    public boolean isTriggered() {
        return this.containerData.get(9) == 1;
    }

    public static void serverTick(Level p_307316_, BlockPos p_307463_, BlockState p_307350_, CrafterBlockEntity p_307265_) {
        int i = p_307265_.craftingTicksRemaining - 1;
        if (i >= 0) {
            p_307265_.craftingTicksRemaining = i;
            if (i == 0) {
                p_307316_.setBlock(p_307463_, p_307350_.setValue(CrafterBlock.CRAFTING, Boolean.valueOf(false)), 3);
            }
        }
    }

    public void setCraftingTicksRemaining(int p_307276_) {
        this.craftingTicksRemaining = p_307276_;
    }

    public int getRedstoneSignal() {
        int i = 0;

        for (int j = 0; j < this.getContainerSize(); j++) {
            ItemStack itemstack = this.getItem(j);
            if (!itemstack.isEmpty() || this.isSlotDisabled(j)) {
                i++;
            }
        }

        return i;
    }

    private boolean slotCanBeDisabled(int p_307658_) {
        return p_307658_ > -1 && p_307658_ < 9 && this.items.get(p_307658_).isEmpty();
    }
}
