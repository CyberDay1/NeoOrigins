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
 * Vanilla's {@code LivingEntity.isInvertedHealAndHarm()} returns true only for
 * entities whose {@code getMobType()} is {@code MobType.UNDEAD}. Players always
 * return {@code MobType.UNDEFINED}, so even with an "undead" entity-group power
 * instant Health/Damage potions are never inverted.
 *
 * <p>The {@code MobEffectEvent.Applicable} handler in CombatPowerEvents blocks
 * addEffect-based effects (Poison, Regen) but cannot intercept instant effects
 * because vanilla applies them via {@code MobEffect.applyInstantEffect()} which
 * calls {@code isInvertedHealAndHarm()} directly without going through
 * {@code addEffect()}. This mixin closes that gap.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityUndeadPotionMixin {

    @Inject(method = "isInvertedHealAndHarm", at = @At("HEAD"), cancellable = true)
    private void neoorigins$undeadPotionInversion(CallbackInfoReturnable<Boolean> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof ServerPlayer sp
                && ActiveOriginService.has(sp, EntityGroupPower.class,
                    config -> config.groupDef().invertsInstant())) {
            cir.setReturnValue(true);
        }
    }
}
