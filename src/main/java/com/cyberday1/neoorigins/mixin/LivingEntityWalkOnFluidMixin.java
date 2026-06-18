package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allows players with the {@code walk_on_water} or {@code walk_on_lava}
 * capabilities to stand on fluid surfaces, using the same mechanic vanilla
 * Striders use for walking on lava.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityWalkOnFluidMixin {

    @Inject(method = "canStandOnFluid", at = @At("HEAD"), cancellable = true, require = 0)
    private void neoorigins$walkOnFluid(FluidState fluidState, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ServerPlayer sp)) return;
        if (fluidState.isEmpty()) return;

        if (fluidState.is(FluidTags.WATER) && ActiveOriginService.hasCapability(sp, "walk_on_water")) {
            // Only walk on water surface — if the player is fully submerged,
            // let normal swim physics apply so they can dive by looking down.
            if (!sp.isUnderWater()) {
                cir.setReturnValue(true);
            }
        }
        if (fluidState.is(FluidTags.LAVA) && ActiveOriginService.hasCapability(sp, "walk_on_lava")) {
            // Mirror the water branch: gate on eye-level submersion, not mere contact.
            // isInLava() is true the instant any lava enters the AABB (feet on the
            // surface), which flipped the power off and dropped the player in.
            if (!sp.isEyeInFluid(FluidTags.LAVA)) {
                cir.setReturnValue(true);
            }
        }
    }
}
