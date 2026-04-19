package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes {@code LivingEntity.onClimbable()} return {@code true} when the entity has
 * the {@code "wall_climb"} capability tag active AND is pressed against a wall.
 *
 * <p>Enabling vanilla climbing via {@code onClimbable} causes both the server and
 * the client to apply vanilla ladder/vine physics — including jump-to-ascend and
 * slow-descent — in lockstep. This removes the need for server-side delta-movement
 * pushes that caused rubber-banding in the 1.x WallClimbingPower implementation.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityClimbMixin {

    @Inject(method = "onClimbable", at = @At("HEAD"), cancellable = true)
    private void neoorigins$wallClimbCapability(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!self.horizontalCollision) return;
        if (self.onGround()) return;
        if (!PowerCapabilities.hasActive(self, "wall_climb")) return;
        cir.setReturnValue(true);
    }
}
