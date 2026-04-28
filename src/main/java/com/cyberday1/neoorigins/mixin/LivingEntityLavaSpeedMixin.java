package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.compat.NumericModifierRegistry;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Apoli's {@code modify_lava_speed} has no clean 1.21+ attribute target —
 * vanilla doesn't expose a {@code lava_movement_efficiency} sibling to
 * water's. We mixin the dedicated {@code travelInLava} method (split out
 * from {@code travelInFluid} on 26.1+) and scale the {@code 0.02F}
 * input-speed factor passed to {@code moveRelative}.
 *
 * <p>Single moveRelative call inside the method, so no ordinal
 * disambiguation is required (unlike on 1.21.1 where the lava and water
 * branches share {@code travelInFluid}).
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
        method = "travelInLava(Lnet/minecraft/world/phys/Vec3;DZD)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"
        ),
        index = 0,
        require = 0
    )
    private float neoorigins$modifyLavaSpeed(float speed) {
        LivingEntity self = (LivingEntity) (Object) this;
        return (float) NumericModifierRegistry.applyByUuid(
            self.getUUID(), NumericModifierRegistry.Kind.LAVA_SPEED, speed);
    }
}
