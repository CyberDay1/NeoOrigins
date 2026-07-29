package com.cyberday1.neoorigins.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens {@code LivingEntity}'s protected voice getters so a morph can ask a
 * throwaway mob what it sounds like.
 *
 * <p>{@code protected} is only usable on {@code this} and on subclasses of the
 * calling class, and the sound donor is neither — it is an arbitrary sibling
 * {@link LivingEntity}. An access transformer would widen these to public, but
 * {@code getHurtSound} and {@code getDeathSound} are overridden by dozens of
 * vanilla mobs, and widening a method a subclass still declares {@code protected}
 * is how you get a load-time visibility error. An invoker sidesteps that
 * entirely: Mixin adds a public bridge <em>inside</em> {@code LivingEntity} that
 * calls the method virtually, so every subclass override still wins.
 */
@Mixin(LivingEntity.class)
public interface LivingEntitySoundInvoker {

    @Invoker("getHurtSound")
    SoundEvent neoorigins$getHurtSound(DamageSource source);

    @Invoker("getDeathSound")
    SoundEvent neoorigins$getDeathSound();
}
