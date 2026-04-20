package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.HungerDrainModifierPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Scales the exhaustion argument of Player.causeFoodExhaustion() by the
 * player's cumulative HungerDrainModifierPower multiplier. Catches
 * movement / combat / damage sources that route through Player.
 * Regen-exhaustion (healing tick) is handled separately in FoodDataTickMixin.
 */
@Mixin(Player.class)
public class FoodDataMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float neoorigins$modifyExhaustion(float amount) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return amount;
        // Legacy class scan (backward compat while HungerDrainModifierPower exists).
        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, HungerDrainModifierPower.class,
            cfg -> mult[0] *= cfg.multiplier());
        float scaled = amount * mult[0];
        // 2.0: chain any action_on_event powers declared for MOD_EXHAUSTION.
        scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_EXHAUSTION, null, scaled);
        return Float.isFinite(scaled) ? scaled : amount;
    }
}
