package net.minecraft.advancements.critereon;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

public interface SingleComponentItemPredicate<T> extends ItemSubPredicate {
    @Override
    default boolean matches(ItemStack p_333958_) {
        T t = p_333958_.get(this.componentType());
        return t != null && this.matches(p_333958_, t);
    }

    DataComponentType<T> componentType();

    boolean matches(ItemStack p_333980_, T p_334084_);
}
