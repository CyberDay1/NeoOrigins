package net.minecraft.world.inventory;

import java.util.Optional;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class NonInteractiveResultSlot extends Slot {
    public NonInteractiveResultSlot(Container p_307372_, int p_307687_, int p_307440_, int p_307347_) {
        super(p_307372_, p_307687_, p_307440_, p_307347_);
    }

    @Override
    public void onQuickCraft(ItemStack p_307668_, ItemStack p_307399_) {
    }

    @Override
    public boolean mayPickup(Player p_307569_) {
        return false;
    }

    @Override
    public Optional<ItemStack> tryRemove(int p_307436_, int p_307573_, Player p_307226_) {
        return Optional.empty();
    }

    @Override
    public ItemStack safeTake(int p_307592_, int p_307524_, Player p_307360_) {
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack safeInsert(ItemStack p_307685_) {
        return p_307685_;
    }

    @Override
    public ItemStack safeInsert(ItemStack p_307656_, int p_307278_) {
        return this.safeInsert(p_307656_);
    }

    @Override
    public boolean allowModification(Player p_307532_) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack p_307504_) {
        return false;
    }

    @Override
    public ItemStack remove(int p_307370_) {
        return ItemStack.EMPTY;
    }

    @Override
    public void onTake(Player p_307447_, ItemStack p_307430_) {
    }

    @Override
    public boolean isHighlightable() {
        return false;
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
