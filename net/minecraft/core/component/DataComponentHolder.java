package net.minecraft.core.component;

import javax.annotation.Nullable;

public interface DataComponentHolder extends net.neoforged.neoforge.common.extensions.IDataComponentHolderExtension {
    DataComponentMap getComponents();

    @Nullable
    default <T> T get(DataComponentType<? extends T> p_331625_) {
        return this.getComponents().get(p_331625_);
    }

    default <T> T getOrDefault(DataComponentType<? extends T> p_331643_, T p_330718_) {
        return this.getComponents().getOrDefault(p_331643_, p_330718_);
    }

    default boolean has(DataComponentType<?> p_330779_) {
        return this.getComponents().has(p_330779_);
    }
}
