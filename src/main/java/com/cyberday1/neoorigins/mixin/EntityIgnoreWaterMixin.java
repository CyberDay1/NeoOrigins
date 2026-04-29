package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses water-current pushing for players with the {@code ignore_water}
 * capability (emitted by {@link com.cyberday1.neoorigins.power.builtin.IgnoreWaterPower}).
 *
 * <p>Movement speed in water is handled separately via a
 * {@code water_movement_efficiency} attribute modifier on the power itself.
 */
@Mixin(Entity.class)
public abstract class EntityIgnoreWaterMixin {

    @Inject(method = "isPushedByFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$ignoreWaterPushing(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer sp) {
            if (ActiveOriginService.hasCapability(sp, "ignore_water")) {
                cir.setReturnValue(false);
            }
        }
    }
}
