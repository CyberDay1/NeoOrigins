package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Apoli's {@code modify_lava_speed} has no clean 1.21+ attribute target —
 * vanilla doesn't expose a {@code lava_movement_efficiency} sibling to
 * water's. Instead we mixin the fluid-travel method and scale the
 * {@code 0.02F} input-speed factor passed to {@code moveRelative} in
 * the lava branch.
 *
 * <p>Two {@code moveRelative} invocations exist in {@code travelInFluid}:
 * the first inside the water/eye-fluid branch (line ~2522) and the
 * second inside the {@code else} (lava) branch (line ~2532). We target
 * {@code ordinal = 1} to match only the lava call. {@code @ModifyArg}
 * on the float argument multiplies it by the registered modifier for
 * the entity's UUID — non-players have no entries so the helper returns
 * the input unchanged, leaving vanilla mob lava-swim untouched.
 *
 * <p>Numeric semantics: an Apoli {@code addition} of {@code 0.4} pushes
 * the factor from {@code 0.02} to {@code 0.42}, which produces a roughly
 * Apoli-equivalent lava-swim feel; {@code multiply_base 0.4} produces
 * {@code 0.028}. Both flavours are honoured via
 * {@link com.cyberday1.neoorigins.compat.OriginsModifierMath}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityLavaSpeedMixin {

    @ModifyArg(
        method = "travelInFluid(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/level/material/FluidState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V",
            ordinal = 1
        ),
        index = 0,
        require = 0   // Tolerate optifine/sodium reorderings; failure → vanilla speed.
    )
    private float neoorigins$modifyLavaSpeed(float speed) {
        LivingEntity self = (LivingEntity) (Object) this;
        return (float) NumericModifierRegistry.applyByUuid(
            self.getUUID(), NumericModifierRegistry.Kind.LAVA_SPEED, speed);
    }
}
