package com.cyberday1.neoorigins.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets origins with the {@code natural_glide} capability (Phantom's
 * "Spectral Wings") start elytra-style gliding without needing to
 * equip an actual elytra.
 *
 * <p>Runs at the head of {@code Player.tryToStartFallFlying} — if the
 * player has the capability, we replicate vanilla's preconditions
 * (airborne, not already flying, not in water, not levitating) and
 * call {@code startFallFlying()} directly, bypassing the chest-slot
 * elytra check. Return {@code true} via cancellable CIR so vanilla
 * doesn't also run the normal path.
 *
 * <p>Server-side only (ServerPlayer check) — client prediction for
 * gliding is handled via vanilla packet sync once the server authoritatively
 * enters fall-flying state.
 */
@Mixin(Player.class)
public abstract class PlayerStartFallFlyingMixin {

    @Inject(method = "tryToStartFallFlying", at = @At("HEAD"), cancellable = true)
    private void neoorigins$naturalGlide(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return;
        if (self.onGround() || self.isFallFlying() || self.isInWater()
            || self.hasEffect(MobEffects.LEVITATION)) {
            return;
        }
        if (!com.cyberday1.neoorigins.service.ActiveOriginService.hasCapability(sp, "natural_glide")) {
            return;
        }
        self.startFallFlying();
        cir.setReturnValue(true);
    }
}
