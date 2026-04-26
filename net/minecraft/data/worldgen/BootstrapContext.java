package net.minecraft.data.worldgen;

import com.mojang.serialization.Lifecycle;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

public interface BootstrapContext<T> {
    Holder.Reference<T> register(ResourceKey<T> p_321720_, T p_321670_, Lifecycle p_321792_);

    default Holder.Reference<T> register(ResourceKey<T> p_321660_, T p_321479_) {
        return this.register(p_321660_, p_321479_, Lifecycle.stable());
    }

    <S> HolderGetter<S> lookup(ResourceKey<? extends Registry<? extends S>> p_321547_);

    default <S> java.util.Optional<net.minecraft.core.HolderLookup.RegistryLookup<S>> registryLookup(ResourceKey<? extends Registry<? extends S>> registry) { return java.util.Optional.empty(); }
}
