package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.fluids.FluidType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity half of {@code neoorigins:ignore_fluid} with {@code "mode": "physics"}.
 *
 * <p>Unlike {@link EntityIgnoreFluidMixin} this leaves fluid detection alone, so
 * {@code isInWater()}, drowning, fog and every fluid condition keep working. It
 * strips two movement effects instead:
 *
 * <ul>
 *   <li>The current push, by answering no to the per-type
 *       {@code isPushedByFluid(FluidType)} the body scan asks before adding a
 *       fluid's flow. The scan still records the fluid's height.</li>
 *   <li>The swim pose, which would otherwise give sprint-swimming back.</li>
 * </ul>
 *
 * <p>Common mixin, both logical sides, for the same reason as the detection
 * mixin: the player predicts its own movement and the two must agree.
 */
@Mixin(Entity.class)
public abstract class EntityIgnoreFluidPhysicsMixin {

    @Shadow public abstract void setSwimming(boolean swimming);

    @WrapOperation(
        method = "updateFluidHeightAndDoFluidPushing()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;isPushedByFluid(Lnet/neoforged/neoforge/fluids/FluidType;)Z"
        )
    )
    private boolean neoorigins$ignoreFluidPhysicsPush(Entity self, FluidType type,
                                                      Operation<Boolean> original) {
        if (IgnoreFluidCapabilities.ignoresPhysics(self, type)) return false;
        return original.call(self, type);
    }

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void neoorigins$ignoreFluidPhysicsSwim(CallbackInfo ci) {
        if (IgnoreFluidCapabilities.inPhysicsIgnoredFluid((Entity) (Object) this)) {
            this.setSwimming(false);
            ci.cancel();
        }
    }
}
