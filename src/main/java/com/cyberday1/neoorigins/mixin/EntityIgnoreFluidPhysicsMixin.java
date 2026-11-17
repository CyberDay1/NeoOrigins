package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.capability.IgnoreFluidCapabilities;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entity part of {@code neoorigins:ignore_fluid} with {@code "mode": "physics"}:
 * keeps the player out of the swim pose while they are in a covered fluid, which
 * would otherwise give sprint-swimming back.
 *
 * <p>Unlike {@link EntityFluidInteractionIgnoreFluidMixin} the physics mode
 * leaves fluid detection alone, so {@code isInWater()}, drowning, fog and every
 * fluid condition keep working. The current push is dropped in
 * {@link EntityFluidInteractionIgnoreFluidPhysicsMixin}, and travel and the
 * swim-up in {@link LivingEntityIgnoreFluidPhysicsMixin}.
 *
 * <p>Common mixin, both logical sides, for the same reason as the detection
 * mixin: the player predicts its own movement and the two must agree.
 */
@Mixin(Entity.class)
public abstract class EntityIgnoreFluidPhysicsMixin {

    @Shadow public abstract void setSwimming(boolean swimming);

    @Inject(method = "updateSwimming", at = @At("HEAD"), cancellable = true)
    private void neoorigins$ignoreFluidPhysicsSwim(CallbackInfo ci) {
        if (IgnoreFluidCapabilities.inPhysicsIgnoredFluid((Entity) (Object) this)) {
            this.setSwimming(false);
            ci.cancel();
        }
    }
}
