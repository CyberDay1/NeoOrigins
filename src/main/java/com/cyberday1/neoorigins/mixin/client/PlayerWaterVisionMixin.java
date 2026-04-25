package com.cyberday1.neoorigins.mixin.client;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Clears the underwater fog filter for aquatic origins (those carrying the
 * {@code dries_out_of_water} capability emitted by
 * {@link com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower}).
 *
 * <p>Vanilla's {@code FogRenderer} multiplies the water-fog far distance by
 * {@code Player.getWaterVision()}, which is a 0..1 ramp tied to the conduit-
 * power mob effect duration. Trying to apply {@code MobEffects.CONDUIT_POWER}
 * via {@link com.cyberday1.neoorigins.power.builtin.PersistentEffectPower} fails
 * to clear fog because the persistent applier uses {@code INFINITE_DURATION}
 * (-1) — which falls into vanilla's {@code duration < 600} ramp branch and
 * returns {@code clamp(-1/600, 0, 1) = 0}. So the effect is "applied" but
 * waterVision still reads as zero and fog is at maximum.
 *
 * <p>Cleanest fix: bypass conduit-power entirely and short-circuit
 * {@code getWaterVision} to {@code 1.0F} when the local player has our
 * aquatic capability and is currently in water. Out of water we yield to
 * vanilla so any real conduit power the player picks up still works
 * normally.
 *
 * <p>Client-side only — fog rendering happens client-side; server callers of
 * {@code getWaterVision} (rare; mostly mob AI) keep vanilla behavior.
 */
// priority = 1500 to win against any other mod that mixins LocalPlayer
// in the same vein (Alex's Caves cave-fog overrides etc.).
@Mixin(value = LocalPlayer.class, priority = 1500)
public abstract class PlayerWaterVisionMixin {

    @Inject(method = "getWaterVision", at = @At("HEAD"), cancellable = true)
    private void neoorigins$clearFogForAquatic(CallbackInfoReturnable<Float> cir) {
        LocalPlayer self = (LocalPlayer)(Object) this;
        if (!self.isInWater()) return;
        if (ClientActivePowers.hasCapability("dries_out_of_water")) {
            cir.setReturnValue(1.0F);
        }
    }
}
