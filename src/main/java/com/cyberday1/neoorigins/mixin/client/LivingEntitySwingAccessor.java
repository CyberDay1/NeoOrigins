package com.cyberday1.neoorigins.mixin.client;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.3 moved the public swing fields into a private final {@code SwingState}.
 * The morph renderer points its dummy at the player's state so every render
 * path that reads the swing sees the player's.
 */
@Mixin(LivingEntity.class)
public interface LivingEntitySwingAccessor {

    @Accessor("swingState")
    LivingEntity.SwingState neoorigins$getSwingState();

    @Mutable
    @Accessor("swingState")
    void neoorigins$setSwingState(LivingEntity.SwingState state);
}
