package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.NoNaturalRegenPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Short-circuits FoodData.tick's natural-regen branches for players with
 * NoNaturalRegenPower. Both regen paths (saturation-based and hunger-based)
 * gate on {@code player.isHurt()}; we return false from that invocation so
 * the branches are skipped. Exhaustion consumption (top of tick) and
 * starvation (bottom) are untouched.
 *
 * <p>LivingHealEvent is not affected, so Regeneration potion, beacon,
 * totem, and direct origins:heal actions continue to heal normally.
 */
@Mixin(FoodData.class)
public class FoodDataNoRegenMixin {

    // 26.1 FoodData.tick now takes ServerPlayer (was Player on 1.21.1) — javac
    // emits the INVOKE on the receiver's static type, so we must match
    // ServerPlayer.isHurt() not Player.isHurt(). See reference_mixin_owner_match
    // memory note. require=0 silences the apply-failure if a future MC version
    // refactors FoodData.tick further; the regen-skip just won't apply.
    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;isHurt()Z"),
        require = 0
    )
    private boolean neoorigins$skipRegenIfBlocked(boolean original, @Local(argsOnly = true) ServerPlayer sp) {
        if (!original) return false;
        if (ActiveOriginService.has(sp, NoNaturalRegenPower.class, c -> true)) return false;
        return original;
    }
}
