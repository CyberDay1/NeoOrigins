package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Overrides the nutrition (hunger) value of all food the player eats.
 * When set, every food item gives exactly {@code nutrition} hunger points
 * regardless of its original value. Saturation is scaled proportionally.
 *
 * <p><b>Why post-eat correction.</b> The NeoForge {@code Finish} event fires
 * <em>after</em> vanilla has already consumed the food and applied nutrition /
 * saturation to {@link net.minecraft.world.food.FoodData}. Modifying the
 * stack's {@link DataComponents#FOOD} at that point does nothing. Instead we
 * compute the difference between what vanilla applied and what we want, and
 * correct the player's {@code FoodData} directly.
 *
 * <p>Applied at eat-finish time via
 * {@link com.cyberday1.neoorigins.event.InteractionPowerEvents}.
 */
public class ModifyFoodNutritionPower extends PowerType<ModifyFoodNutritionPower.Config> {

    public record Config(int nutrition, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("nutrition", 1).forGetter(Config::nutrition),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Corrects the player's food data after vanilla has already applied the
     * original food properties. Called from the {@code Finish} event handler.
     *
     * @param player        the player who just ate
     * @param originalStack a copy of the food item <em>before</em> it was consumed
     *                      (the {@code Finish} event provides this)
     * @param targetNutrition the nutrition value this power wants to enforce
     */
    public static void applyOverride(ServerPlayer player, ItemStack originalStack, int targetNutrition) {
        FoodProperties food = originalStack.get(DataComponents.FOOD);
        if (food == null) return;

        int originalNutrition = food.nutrition();
        float originalSaturation = food.saturation();
        if (originalNutrition == targetNutrition) return;

        // Scale saturation proportionally to the nutrition change.
        // saturation = nutrition * ratio, so preserve the ratio.
        float ratio = originalNutrition > 0
            ? originalSaturation / (float) originalNutrition
            : 0f;
        float targetSaturation = targetNutrition * ratio;

        // Compute deltas and correct the player's FoodData.
        int nutritionDelta = targetNutrition - originalNutrition;
        float saturationDelta = targetSaturation - originalSaturation;

        var foodData = player.getFoodData();
        foodData.setFoodLevel(Mth.clamp(foodData.getFoodLevel() + nutritionDelta, 0, 20));
        // Saturation is capped at the current food level per vanilla rules.
        foodData.setSaturation(Mth.clamp(
            foodData.getSaturationLevel() + saturationDelta,
            0.0F, (float) foodData.getFoodLevel()));
    }
}
