package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Cancels vanilla's out-of-water airSupply refill while the player has a
 * {@code neoorigins:breath_out_of_fluid} power active. Applied on both client
 * and server so client-side prediction agrees with server state — otherwise
 * the bubble HUD oscillates between the server's clamped value and the
 * client-predicted refilled value (one-tick flicker reported by tester on
 * 2.0.0-alpha.11). With the refill suppressed, the Post-tick handler in
 * {@link BreathOutOfFluidPower.Handler} fully owns the airSupply value while
 * the player is out of water.
 *
 * <p>Client check uses the {@code dries_out_of_water} capability tag synced
 * via the active-powers channel — same mechanism the {@code LightTextureMixin}
 * uses for the night-vision boost. The 26.1 {@code ClientPowerCache.Entry}
 * dropped the {@code typeId} field, so type-based lookups aren't available on
 * this branch; the capability tag is the cross-branch consistent path.
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

        if (self.level().isClientSide()) {
            // Client / integrated server. Capability tag is set on the local
            // player when an aquatic origin is selected.
            return ClientActivePowers.hasCapability("dries_out_of_water");
        }
        if (self instanceof ServerPlayer sp) {
            boolean[] has = {false};
            ActiveOriginService.forEachOfType(sp, BreathOutOfFluidPower.class, cfg -> has[0] = true);
            return has[0];
        }
        return false;
    }
}
