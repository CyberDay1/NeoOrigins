package com.cyberday1.neoorigins.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the protected {@code jumping} field on LivingEntity so non-subclass
 * code (e.g. {@link com.cyberday1.neoorigins.power.builtin.WallClimbingPower})
 * can read the player's current jump input state server-side.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean neoorigins$isJumping();
}
