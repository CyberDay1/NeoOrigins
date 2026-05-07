package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.EntityGroupPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides {@code isInvertedHealAndHarm()} for players with an undead
 * entity group, so Instant Health damages and Instant Damage heals —
 * matching vanilla undead mob behavior.
 *
 * <p>Vanilla applies instant effects via {@code applyInstantenousEffect()}
 * which bypasses {@code addEffect()} (and therefore MobEffectEvent.Applicable).
 * The only way to invert them for players is through this method.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUndeadPotionMixin {

    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void neoorigins$undeadPotionInversion(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer sp) {
            if (ActiveOriginService.has(sp, EntityGroupPower.class, EntityGroupPower.Config::isUndead)) {
                cir.setReturnValue(true);
            }
        }
    }
}
