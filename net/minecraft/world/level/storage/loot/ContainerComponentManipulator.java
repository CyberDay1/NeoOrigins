package net.minecraft.world.level.storage.loot;

import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface ContainerComponentManipulator<T> {
    DataComponentType<T> type();

    T empty();

    T setContents(T p_341056_, Stream<ItemStack> p_341252_);

    Stream<ItemStack> getContents(T p_341229_);

    default void setContents(ItemStack p_340824_, T p_341046_, Stream<ItemStack> p_341372_) {
        T t = p_340824_.getOrDefault(this.type(), p_341046_);
        T t1 = this.setContents(t, p_341372_);
        p_340824_.set(this.type(), t1);
    }

    default void setContents(ItemStack p_340943_, Stream<ItemStack> p_340843_) {
        this.setContents(p_340943_, this.empty(), p_340843_);
    }

    default void modifyItems(ItemStack p_340919_, UnaryOperator<ItemStack> p_341245_) {
        T t = p_340919_.get(this.type());
        if (t != null) {
            UnaryOperator<ItemStack> unaryoperator = p_344668_ -> {
                if (p_344668_.isEmpty()) {
                    return p_344668_;
                } else {
                    ItemStack itemstack = p_341245_.apply(p_344668_);
                    itemstack.limitSize(itemstack.getMaxStackSize());
                    return itemstack;
                }
            };
            this.setContents(p_340919_, this.getContents(t).map(unaryoperator));
        }
    }
}
