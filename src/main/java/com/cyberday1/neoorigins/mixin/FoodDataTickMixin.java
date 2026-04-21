package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.HungerDrainModifierPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
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
 * Without this hook, Avian's Athlete's Diet (0.25x) leaves healing exhaustion
 * unchanged, so hunger drops at full rate while the player is regenerating.
 */
@Mixin(FoodData.class)
public class FoodDataTickMixin {

    @ModifyArg(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;addExhaustion(F)V")
    )
    private float neoorigins$scaleRegenExhaustion(float amount, @Local(argsOnly = true) Player player) {
        if (!(player instanceof ServerPlayer sp)) return amount;
        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, HungerDrainModifierPower.class,
            cfg -> mult[0] *= cfg.multiplier());
        if (mult[0] == 1.0f) return amount;
        float scaled = amount * mult[0];
        return Float.isFinite(scaled) ? scaled : amount;
    }
}
