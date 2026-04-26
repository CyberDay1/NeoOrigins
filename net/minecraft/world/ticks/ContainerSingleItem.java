package net.minecraft.world.ticks;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface ContainerSingleItem extends Container {
    ItemStack getTheItem();

    default ItemStack splitTheItem(int p_304641_) {
        return this.getTheItem().split(p_304641_);
    }

    void setTheItem(ItemStack p_304718_);

    default ItemStack removeTheItem() {
        return this.splitTheItem(this.getMaxStackSize());
    }

    @Override
    default int getContainerSize() {
        return 1;
    }

    @Override
    default boolean isEmpty() {
        return this.getTheItem().isEmpty();
    }

    @Override
    default void clearContent() {
        this.removeTheItem();
    }

    @Override
    default ItemStack removeItemNoUpdate(int p_273409_) {
        return this.removeItem(p_273409_, this.getMaxStackSize());
    }

    @Override
    default ItemStack getItem(int p_304882_) {
        return p_304882_ == 0 ? this.getTheItem() : ItemStack.EMPTY;
    }

    @Override
    default ItemStack removeItem(int p_304944_, int p_304791_) {
        return p_304944_ != 0 ? ItemStack.EMPTY : this.splitTheItem(p_304791_);
    }

    @Override
    default void setItem(int p_304434_, ItemStack p_304854_) {
        if (p_304434_ == 0) {
            this.setTheItem(p_304854_);
        }
    }

    public interface BlockContainerSingleItem extends ContainerSingleItem {
        BlockEntity getContainerBlockEntity();

        @Override
        default boolean stillValid(Player p_324363_) {
            return Container.stillValidBlockEntity(this.getContainerBlockEntity(), p_324363_);
        }
    }
}
