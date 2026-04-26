package net.minecraft.world.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public class CrafterSlot extends Slot {
    private final CrafterMenu menu;

    public CrafterSlot(Container p_307192_, int p_307545_, int p_307618_, int p_307649_, CrafterMenu p_307432_) {
        super(p_307192_, p_307545_, p_307618_, p_307649_);
        this.menu = p_307432_;
    }

    @Override
    public boolean mayPlace(ItemStack p_307320_) {
        return !this.menu.isSlotDisabled(this.index) && super.mayPlace(p_307320_);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        this.menu.slotsChanged(this.container);
    }
}
