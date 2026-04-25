package com.cyberday1.neoorigins.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Scales regen-source exhaustion in FoodData.tick(Player) — those call sites
 * invoke this.addExhaustion(...) directly, bypassing Player.causeFoodExhaustion.
 * Routed through action_on_event (MOD_EXHAUSTION) in 2.0 so the hunger_drain_modifier
 * alias applies to regen ticks as well as movement/damage exhaustion.
 */
@Mixin(FoodData.class)
public class FoodDataTickMixin {

    @ModifyArg(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V")
    )
    private float neoorigins$scaleRegenExhaustion(float amount, @Local(argsOnly = true) Player player) {
        if (!(player instanceof ServerPlayer sp)) return amount;
        float scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_EXHAUSTION, null, amount);
        return Float.isFinite(scaled) ? scaled : amount;
    }
}
