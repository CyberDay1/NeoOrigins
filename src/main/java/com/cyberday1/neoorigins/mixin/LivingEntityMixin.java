package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.FlightPower;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Shadow
    protected int fallFlyTicks;

    /**
     * When a player has the FlightPower active, skip vanilla's canGlide() check
     * (which requires an elytra) and the equipment damage logic. This allows
     * elytra-style gliding without needing an elytra equipped.
     */
    @Inject(method = "updateFallFlying", at = @At("HEAD"), cancellable = true)
    private void neoorigins$skipGlideCheckForFlightPower(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp
                && FlightPower.isActive(sp)) {
            self.checkSlowFallDistance();
            if (!self.level().isClientSide) {
                // Don't check canGlide() — allow flight without elytra.
                // Don't damage equipment — there's no elytra to damage.
                this.fallFlyTicks++;
            }
            ci.cancel();
        }
    }
}
