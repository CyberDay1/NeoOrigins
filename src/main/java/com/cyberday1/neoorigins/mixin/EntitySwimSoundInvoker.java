package com.cyberday1.neoorigins.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The water half of {@link LivingEntitySoundInvoker}. These three live on
 * {@code Entity} rather than {@code LivingEntity}, so they need their own
 * invoker; the reasoning for using one instead of an access transformer is the
 * same, and fish, dolphins, turtles, drowned and every monster override them.
 */
@Mixin(Entity.class)
public interface EntitySwimSoundInvoker {

    @Invoker("getSwimSound")
    SoundEvent neoorigins$getSwimSound();

    @Invoker("getSwimSplashSound")
    SoundEvent neoorigins$getSwimSplashSound();

    @Invoker("getSwimHighSpeedSplashSound")
    SoundEvent neoorigins$getSwimHighSpeedSplashSound();
}
