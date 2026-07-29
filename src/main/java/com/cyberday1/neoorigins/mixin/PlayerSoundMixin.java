package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.morph.MorphSoundResolver;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives a morphed player the voice of whatever they are morphed into — the
 * {@code sounds} half of {@code neoorigins:entity_model}.
 *
 * <p>Every one of these getters is the single point vanilla goes through to ask
 * "what does this entity sound like", and they all run on both logical sides, so
 * patching them here covers the server broadcast and the client's own playback
 * at once with nothing to sync. There is no NeoForge event for any of them.
 *
 * <p>Injected at RETURN rather than HEAD so the vanilla answer is available as a
 * fallback — {@code getFallSounds} in particular resolves its two halves
 * independently and needs somewhere to inherit the half a morph didn't set.
 *
 * <p>Step sounds are absent on purpose: those come from the block underfoot, not
 * from the entity, so morphing into a chicken should not change them.
 */
@Mixin(Player.class)
public abstract class PlayerSoundMixin {

    @Inject(method = "getHurtSound", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphHurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent morphed = MorphSoundResolver.hurt(self());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    @Inject(method = "getDeathSound", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphDeathSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent morphed = MorphSoundResolver.death(self());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    @Inject(method = "getFallSounds", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphFallSounds(CallbackInfoReturnable<LivingEntity.Fallsounds> cir) {
        LivingEntity.Fallsounds morphed = MorphSoundResolver.fall(self(), cir.getReturnValue());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    @Inject(method = "getSwimSound", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphSwimSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent morphed = MorphSoundResolver.swim(self());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    @Inject(method = "getSwimSplashSound", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphSplashSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent morphed = MorphSoundResolver.splash(self());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    @Inject(method = "getSwimHighSpeedSplashSound", at = @At("RETURN"), cancellable = true)
    private void neoorigins$morphHighSpeedSplashSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent morphed = MorphSoundResolver.splashHighSpeed(self());
        if (morphed != null) cir.setReturnValue(morphed);
    }

    private Player self() {
        return (Player) (Object) this;
    }
}
