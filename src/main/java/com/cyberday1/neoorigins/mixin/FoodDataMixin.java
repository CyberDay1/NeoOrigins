package com.cyberday1.neoorigins.mixin;

import com.cyberday1.neoorigins.power.builtin.HungerDrainModifierPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public class FoodDataMixin {

    /**
     * Scales the exhaustion value passed to causeFoodExhaustion() by the
     * player's cumulative HungerDrainModifierPower multiplier.
     * This makes hunger-drain origins (Feline 1.3x, Draconic 1.5x, Tiny 1.8x,
     * Avian 0.25x) actually affect hunger drain rate.
     */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float neoorigins$modifyExhaustion(float amount) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return amount;
        final float[] mult = {1.0f};
        ActiveOriginService.forEachOfType(sp, HungerDrainModifierPower.class,
            cfg -> mult[0] *= cfg.multiplier());
        if (mult[0] == 1.0f) return amount;
        float scaled = amount * mult[0];
        return Float.isFinite(scaled) ? scaled : amount;
    }
}
