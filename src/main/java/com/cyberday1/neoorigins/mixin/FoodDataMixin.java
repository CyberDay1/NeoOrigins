package com.cyberday1.neoorigins.mixin;

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

    /**
     * Scales the exhaustion value passed to causeFoodExhaustion() via any
     * action_on_event powers declared for the MOD_EXHAUSTION event. Hunger-drain
     * origins (Feline 1.3x, Draconic 1.5x, Tiny 1.8x, Avian 0.25x) go through
     * the hunger_drain_modifier → action_on_event alias since 2.0.
     */
    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float neoorigins$modifyExhaustion(float amount) {
        Player self = (Player) (Object) this;
        if (!(self instanceof ServerPlayer sp)) return amount;
        // SPRINT_FOOD immunity — pack authors use this for "sprinting costs no
        // extra hunger" powers (Voidwalker Weightless, Verdant Tireless,
        // Umbral Shadowrun). We can't distinguish sprint-movement exhaustion
        // from other sources at this call site, so zero all exhaustion while
        // sprinting. Non-sprint exhaustion (combat, block break, etc.) still
        // ticks normally as soon as the player stops sprinting.
        if (sp.isSprinting()
            && com.cyberday1.neoorigins.service.ActiveOriginService.has(
                sp, com.cyberday1.neoorigins.power.builtin.PreventActionPower.class,
                cfg -> cfg.action() == com.cyberday1.neoorigins.power.builtin.PreventActionPower.Action.SPRINT_FOOD
                    && com.cyberday1.neoorigins.power.builtin.PreventActionPower.isGateOpen(sp, cfg))) {
            return 0F;
        }
        float scaled = com.cyberday1.neoorigins.service.EventPowerIndex.dispatchModifier(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.MOD_EXHAUSTION, null, amount);
        return Float.isFinite(scaled) ? scaled : amount;
    }
}
