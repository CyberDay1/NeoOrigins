package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower;
import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cancels vanilla's out-of-water airSupply refill while the player has a
 * {@code neoorigins:breath_out_of_fluid} power draining them (its
 * {@link BreathOutOfFluidPower#DRAIN_CAPABILITY} tag). Applied on both client
 * and server so client-side prediction agrees with server state — otherwise
 * the bubble HUD oscillates between the server's clamped value and the
 * client-predicted refilled value (one-tick flicker reported by tester on
 * 2.0.0-alpha.11). With the refill suppressed, the Post-tick handler in
 * {@link BreathOutOfFluidPower.Handler} fully owns the airSupply value while
 * the player is out of water.
 *
 * <p>Both sides read the drain tag synced via the active-powers channel. It
 * replaced {@code dries_out_of_water} here, which stayed on with the
 * {@code [ocean_origins] dries_out} config off and froze the bubble row.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAirRefillMixin {

    @Shadow protected abstract int increaseAirSupply(int air);

    @Redirect(method = "baseTick",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/entity/LivingEntity;increaseAirSupply(I)I"))
    private int neoorigins$skipRefillWhileDryingOut(LivingEntity self, int currentAir) {
        if (shouldSkipRefill(self)) return currentAir;
        return this.increaseAirSupply(currentAir);
    }

    private static boolean shouldSkipRefill(LivingEntity self) {
        if (!(self instanceof Player player)) return false;
        // Power only drains while out of water — let vanilla refill run normally
        // when the player re-enters water so the bubble row recovers.
        if (player.isInWater()) return false;
        // Water Breathing effect acts as a magical air supply — let vanilla
        // refill the bar so the HUD recovers while the potion is active.
        if (player.hasEffect(MobEffects.WATER_BREATHING)) return false;
        // Conduit Power likewise, which ConduitBlockEntityMixin now puts on
        // dry-land players carrying this power. Without this clause the drain
        // stops but the refill stays suppressed, so the bubble row freezes at
        // whatever it had drained to instead of recovering — the exemption
        // would look half-applied to the player standing beside the conduit.
        if (player.hasEffect(MobEffects.CONDUIT_POWER)) return false;

        // The synced tag, gated by power_condition and by [ocean_origins]
        // dries_out like the drain itself: when either stops the drain, the
        // refill comes back on both sides instead of the air freezing.
        return PowerCapabilities.hasActive(player, BreathOutOfFluidPower.DRAIN_CAPABILITY);
    }
}
