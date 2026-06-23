package com.cyberday1.neoorigins.mixin.compat;

import com.cyberday1.neoorigins.power.capability.PowerCapabilities;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Soft-dep compat with the Aliens vs Predator mod ({@code avp_alien}). Short-circuits
 * {@code AlienPredicates.isHost} for any entity carrying the {@code "xeno_passive"}
 * capability (granted by {@code XenoPassivePower}) so facehuggers — and the ovomorph
 * hatch-desire / parasite-attachment logic that gates off the same predicate — stop
 * treating that player as a viable host.
 *
 * <p>The targeting half of "be passive towards xenos" is handled by a
 * {@code mobs_ignore_player} power scoped to the {@code #avp_alien:aliens} and
 * {@code #avp_alien:parasites} tags; this mixin only governs the host gate.
 *
 * <p>{@code @Pseudo} + {@code require = 0} + {@code remap = false} make this entirely
 * optional: if AvP (and its {@code AlienPredicates} class) is absent, the mixin is
 * silently skipped. The handler signature is vanilla-only ({@code Entity}), so no AvP
 * type is referenced and no compile-time dependency on the mod is needed.
 */
@Pseudo
@Mixin(targets = "com.alien.common.util.AlienPredicates", remap = false)
public class AvpAlienHostMixin {

    @Inject(method = "isHost", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void neoorigins$xenoPassiveNotAHost(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof LivingEntity le && PowerCapabilities.hasActive(le, "xeno_passive")) {
            cir.setReturnValue(false);
        }
    }
}
