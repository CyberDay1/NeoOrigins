package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses water-current pushing for players with the {@code ignore_water}
 * capability (emitted by {@link com.cyberday1.neoorigins.power.builtin.IgnoreWaterPower}).
 *
 * <p>Movement speed in water is handled separately via a
 * {@code water_movement_efficiency} attribute modifier on the power itself.
 *
 * <p>Also prevents mining speed penalty <i>for not being on ground</i> while in water
 * for players with the {@code ignore_water} capability.
 *
 * <p>Mining speed penalty <i>for being underwater</i> is handled separately
 * via `underwater_mining_speed` power.
 */
@Mixin(Player.class)
public abstract class PlayerIgnoreWaterMixin {

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$ignoreWaterPushing(CallbackInfoReturnable<Boolean> cir) {
        if (ActiveOriginService.hasCapabilitySided((Player) (Object) this, "ignore_water")) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "getDigSpeed", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;onGround()Z"))
    private boolean neoorigins$ignoreInWaterDigSpeedPenalty(Player player) {
        if (ActiveOriginService.hasCapabilitySided(player, "ignore_water")
                && player.isInWater()) {
            return true;
        }
        return player.onGround();
    }
}
