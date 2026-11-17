package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * LivingEntity part of {@code neoorigins:ignore_fluid} with {@code "mode": "physics"}
 * (see {@link EntityIgnoreFluidPhysicsMixin} for the rest).
 *
 * <ul>
 *   <li>{@code travel} takes its fluid branch only when {@code shouldTravelInFluid}
 *       holds. Answering no while the player is in a covered fluid sends them
 *       down the air branch: land speed and gravity, no buoyancy, no drag. That
 *       is what lets them sink to the bottom and walk.</li>
 *   <li>{@code aiStep} swims upward through {@code jumpInFluid} when jump is held
 *       in fluid deeper than the jump threshold. For a covered fluid that becomes
 *       an ordinary jump off the bottom, or nothing while off the ground.</li>
 * </ul>
 *
 * <p>{@code isAffectedByFluids()} itself is left alone: {@code aiStep} also
 * gates the plain ground jump on it, so overriding it would stop the player
 * jumping at all.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityIgnoreFluidPhysicsMixin {

    @Shadow private int noJumpDelay;

    @Shadow public abstract void jumpFromGround();

    @Inject(method = "shouldTravelInFluid", at = @At("HEAD"), cancellable = true)
    private void neoorigins$ignoreFluidPhysicsTravel(FluidState fluidState,
                                                     CallbackInfoReturnable<Boolean> cir) {
        if (IgnoreFluidCapabilities.inPhysicsIgnoredFluid((LivingEntity) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @WrapOperation(
        method = "aiStep",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;jumpInFluid(Lnet/neoforged/neoforge/fluids/FluidType;)V"
        )
    )
    private void neoorigins$ignoreFluidPhysicsJump(LivingEntity self, FluidType type,
                                                   Operation<Void> original) {
        if (!IgnoreFluidCapabilities.ignoresPhysics(self, type)) {
            original.call(self, type);
            return;
        }
        if (self.onGround() && this.noJumpDelay == 0) {
            this.jumpFromGround();
            this.noJumpDelay = 10;
        }
    }
}
