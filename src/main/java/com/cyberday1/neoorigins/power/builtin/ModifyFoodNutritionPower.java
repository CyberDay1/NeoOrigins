package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Overrides the nutrition (hunger) value of all food the player eats.
 * When set, every food item gives exactly {@code nutrition} hunger points
 * regardless of its original value. Saturation is scaled proportionally.
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
     * Modifies the food component on the stack to use the configured nutrition.
     * Called just before vanilla processes the eaten food.
     */
    public static void applyOverride(ServerPlayer player, ItemStack stack, int nutrition) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) return;

        FoodProperties.Builder builder = new FoodProperties.Builder()
            .nutrition(nutrition)
            .saturationModifier(food.saturation());
        if (food.canAlwaysEat()) builder.alwaysEdible();
        stack.set(DataComponents.FOOD, builder.build());
    }
}
