package com.cyberday1.neoorigins.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.ElytraOnPlayerSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses vanilla's {@code item.elytra.flying} wind sound when a player
 * enters fall-flying <em>without an actual Elytra equipped</em> — i.e., the
 * gliding is being driven by a NeoOrigins flight power like
 * {@code natural_glide} (via {@code LocalPlayerNaturalGlideMixin}) or by the
 * {@code AirJumpPayload} server hook calling {@code startFallFlying()}.
 *
 * <p>Vanilla emission site is
 * {@code LocalPlayer.onSyncedDataUpdated(EntityDataAccessor)}: when the
 * shared {@code DATA_SHARED_FLAGS_ID} flips and {@code isFallFlying() &&
 * !wasFallFlying}, vanilla unconditionally calls
 * {@code getSoundManager().play(new ElytraOnPlayerSoundInstance(this))} —
 * never checking the chest slot. Vanilla assumed fall-flying always implies
 * a real elytra; the mod's powers legitimately break that assumption, so the
 * sound has to be gated here.
 *
 * <p>{@link Redirect} the single {@code SoundManager.play(SoundInstance)} call
 * in that method: forward as normal unless the sound is the elytra-wind
 * instance AND no Elytra is equipped, in which case drop it on the floor.
 * Real-elytra users are unaffected (the instanceof + chest-slot check is
 * mod-agnostic — no coupling to specific powers). Surgical: only fires once
 * per fall-flying transition, no per-tick cost — when suppressed, the sound
 * instance is never even created.
 *
 * <p>Client-side only — listed in the {@code client} array of
 * {@code neoorigins.mixins.json} so it never loads on a dedicated server.
 * {@code require = 0} mirrors {@code LocalPlayerNaturalGlideMixin}'s cautious
 * stance: if a future MC remap moves the call site, the mixin degrades
 * silently to vanilla behavior rather than failing the boot.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerElytraSoundMixin {

    @Redirect(
        method = "onSyncedDataUpdated",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/sounds/SoundManager;play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V"),
        require = 0
    )
    private void neoorigins$skipElytraSoundIfNoElytra(SoundManager mgr, SoundInstance sound) {
        if (sound instanceof ElytraOnPlayerSoundInstance) {
            LocalPlayer self = (LocalPlayer) (Object) this;
            // Cross-version: `stack.is(Items.ELYTRA)` instead of
            // `instanceof ElytraItem` — newer MC mappings (master/26.1) replace
            // the ElytraItem class with component-based equipment, but the
            // registry item Items.ELYTRA exists on both lines.
            if (!self.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                return; // gliding without an elytra — suppress the elytra wind
            }
        }
        mgr.play(sound);
    }
}
