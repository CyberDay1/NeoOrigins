package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.NoNaturalRegenPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
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

    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z")
    )
    private boolean neoorigins$skipRegenIfBlocked(boolean original, @Local(argsOnly = true) Player player) {
        if (!original) return false;
        if (!(player instanceof ServerPlayer sp)) return original;
        if (ActiveOriginService.has(sp, NoNaturalRegenPower.class, c -> true)) return false;
        return original;
    }
}
