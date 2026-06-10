package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Apoli's {@code modify_lava_speed} has no clean 1.21+ attribute target —
 * vanilla doesn't expose a {@code lava_movement_efficiency} sibling to
 * water's. Instead we mixin the fluid-travel path and scale the
 * {@code 0.02F} input-speed factor passed to {@code moveRelative} in
 * the lava branch.
 *
 * <p>In 1.21.1 the fluid branches live INLINE in {@code travel(Vec3)} —
 * {@code travelInFluid} only exists from 1.21.2 onward. The first shipped
 * version of this mixin targeted {@code travelInFluid} with {@code require = 0},
 * so it silently never applied and the power was a no-op (GitHub #102).
 *
 * <p>Rather than pinning the lava call by ordinal (fragile under other mods'
 * transforms of {@code travel}, and the water/lava branches are vanilla-order
 * dependent), this matches EVERY {@code moveRelative} invocation in
 * {@code travel} and gates at runtime on {@code isInLava()}: only the lava
 * branch executes while the entity is in lava, so the factor is scaled exactly
 * where vanilla applies it. Non-players (and players without the power) have
 * no registry entries, so the helper returns the input unchanged.
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
        method = "travel(Lnet/minecraft/world/phys/Vec3;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"
        ),
        index = 0,
        require = 0   // Degrade to vanilla speed if another mod's transform replaces the calls.
    )
    private float neoorigins$modifyLavaSpeed(float speed) {
        LivingEntity self = (LivingEntity) (Object) this;
        // The water branch takes precedence in vanilla, so only scale when the
        // entity is actually travelling in lava (and not in water — boundary
        // cases where both fluids register go to the water branch).
        if (!self.isInLava() || self.isInWater()) return speed;
        return (float) NumericModifierRegistry.applyByUuid(
            self.getUUID(), NumericModifierRegistry.Kind.LAVA_SPEED, speed);
    }
}
