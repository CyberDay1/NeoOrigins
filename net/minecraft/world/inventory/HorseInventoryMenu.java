package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HorseInventoryMenu extends AbstractContainerMenu {
    private final Container horseContainer;
    private final Container armorContainer;
    private final AbstractHorse horse;
    private static final int SLOT_BODY_ARMOR = 1;
    private static final int SLOT_HORSE_INVENTORY_START = 2;

    public HorseInventoryMenu(int p_39656_, Inventory p_39657_, Container p_39658_, final AbstractHorse p_39659_, int p_352384_) {
        super(null, p_39656_);
        this.horseContainer = p_39658_;
        this.armorContainer = p_39659_.getBodyArmorAccess();
        this.horse = p_39659_;
        int i = 3;
        p_39658_.startOpen(p_39657_.player);
        int j = -18;
        this.addSlot(new Slot(p_39658_, 0, 8, 18) {
            @Override
            public boolean mayPlace(ItemStack p_39677_) {
                return p_39677_.is(Items.SADDLE) && !this.hasItem() && p_39659_.isSaddleable();
            }

            @Override
            public boolean isActive() {
                return p_39659_.isSaddleable();
            }
        });
        this.addSlot(new ArmorSlot(this.armorContainer, p_39659_, EquipmentSlot.BODY, 0, 8, 36, null) {
            @Override
            public boolean mayPlace(ItemStack p_39690_) {
                return p_39659_.isBodyArmorItem(p_39690_);
            }

            @Override
            public boolean isActive() {
                return p_39659_.canUseSlot(EquipmentSlot.BODY);
            }
        });
        if (p_352384_ > 0) {
            for (int k = 0; k < 3; k++) {
                for (int l = 0; l < p_352384_; l++) {
                    this.addSlot(new Slot(p_39658_, 1 + l + k * p_352384_, 80 + l * 18, 18 + k * 18));
                }
            }
        }

        for (int i1 = 0; i1 < 3; i1++) {
            for (int k1 = 0; k1 < 9; k1++) {
                this.addSlot(new Slot(p_39657_, k1 + i1 * 9 + 9, 8 + k1 * 18, 102 + i1 * 18 + -18));
            }
        }

        for (int j1 = 0; j1 < 9; j1++) {
            this.addSlot(new Slot(p_39657_, j1, 8 + j1 * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player p_39661_) {
        return !this.horse.hasInventoryChanged(this.horseContainer)
            && this.horseContainer.stillValid(p_39661_)
            && this.armorContainer.stillValid(p_39661_)
            && this.horse.isAlive()
            && p_39661_.canInteractWithEntity(this.horse, 4.0);
    }

    @Override
    public ItemStack quickMoveStack(Player p_39665_, int p_39666_) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(p_39666_);
        if (slot != null && slot.hasItem()) {
            ItemStack itemstack1 = slot.getItem();
            itemstack = itemstack1.copy();
            int i = this.horseContainer.getContainerSize() + 1;
            if (p_39666_ < i) {
                if (!this.moveItemStackTo(itemstack1, i, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(1).mayPlace(itemstack1) && !this.getSlot(1).hasItem()) {
                if (!this.moveItemStackTo(itemstack1, 1, 2, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (this.getSlot(0).mayPlace(itemstack1)) {
                if (!this.moveItemStackTo(itemstack1, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (i <= 1 || !this.moveItemStackTo(itemstack1, 2, i, false)) {
                int j = i + 27;
                int k = j + 9;
                if (p_39666_ >= j && p_39666_ < k) {
                    if (!this.moveItemStackTo(itemstack1, i, j, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (p_39666_ >= i && p_39666_ < j) {
                    if (!this.moveItemStackTo(itemstack1, j, k, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(itemstack1, j, j, false)) {
                    return ItemStack.EMPTY;
                }

                return ItemStack.EMPTY;
            }

            if (itemstack1.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }

        return itemstack;
    }

    @Override
    public void removed(Player p_39663_) {
        super.removed(p_39663_);
        this.horseContainer.stopOpen(p_39663_);
    }
}
