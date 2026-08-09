package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Overrides the nutrition (hunger) value of food the player eats.
 * When set, matching food gives exactly {@code nutrition} hunger points
 * regardless of its original value. Saturation is scaled proportionally.
 *
 * <p>An optional {@code food_item} or {@code food_tag} field filters which
 * foods are affected. If neither is set, ALL food is affected.
 *
 * <pre>{@code
 * { "type": "neoorigins:modify_food_nutrition", "nutrition": 1 }
 * { "type": "neoorigins:modify_food_nutrition", "nutrition": 8, "food_tag": "#minecraft:meat" }
 * { "type": "neoorigins:modify_food_nutrition", "nutrition": 2, "food_item": "minecraft:sweet_berries" }
 * }</pre>
 *
 * <p>Applied at eat-finish time via
 * {@link com.cyberday1.neoorigins.event.InteractionPowerEvents}.
 */
public class ModifyFoodNutritionPower extends PowerType<ModifyFoodNutritionPower.Config> {

    public record Config(int nutrition, Optional<String> foodItem, Optional<String> foodTag, String type)
            implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("nutrition", 1).forGetter(Config::nutrition),
            Codec.STRING.optionalFieldOf("food_item").forGetter(Config::foodItem),
            Codec.STRING.optionalFieldOf("food_tag").forGetter(Config::foodTag),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Check if the given food item matches this power's filter.
     * Returns true if no filter is set (affects all food).
     */
    public static boolean matchesFilter(ItemStack stack, Config config) {
        if (config.foodItem().isPresent()) {
            String id = config.foodItem().get();
            var itemOpt = BuiltInRegistries.ITEM.get(Identifier.parse(id));
            if (itemOpt.isEmpty() || !stack.is(itemOpt.get().value())) return false;
        }
        if (config.foodTag().isPresent()) {
            String tag = config.foodTag().get();
            if (tag.startsWith("#")) tag = tag.substring(1);
            var tagKey = TagKey.create(Registries.ITEM, Identifier.parse(tag));
            if (!stack.is(tagKey)) return false;
        }
        return true;
    }

    /**
     * Re-applies the eaten food at its overridden nutrition value, working from
     * the player's food/saturation as it was <em>before</em> vanilla ate the
     * item.
     *
     * <p>This used to correct the bar with a post-eat delta
     * ({@code current + (target - original)}), but that silently broke whenever
     * the food filled the bar to its cap: vanilla clamps the actual gain at 20,
     * so subtracting the full {@code original - target} over-corrected and the
     * player could end up with nothing (or less) than they started with. By
     * recomputing absolutely from the pre-eat baseline we get the same result
     * vanilla would have produced had the food simply carried {@code target}
     * nutrition to begin with.
     *
     * @param preFood       foodLevel captured before vanilla applied the food
     * @param preSaturation saturation captured before vanilla applied the food
     */
    public static void applyOverride(ServerPlayer player, ItemStack originalStack,
                                     int targetNutrition, int preFood, float preSaturation) {
        FoodProperties food = originalStack.get(DataComponents.FOOD);
        if (food == null) return;

        int originalNutrition = food.nutrition();
        if (originalNutrition == targetNutrition) return;

        float targetSaturation = scaledSaturation(originalNutrition, food.saturation(), targetNutrition);

        var foodData = player.getFoodData();
        int desiredFood = Mth.clamp(preFood + targetNutrition, 0, 20);
        foodData.setFoodLevel(desiredFood);
        foodData.setSaturation(Mth.clamp(preSaturation + targetSaturation, 0.0F, (float) desiredFood));
    }

    /**
     * The single definition of how an overridden nutrition value scales the food's
     * saturation: saturation keeps the same per-nutrition ratio the food shipped
     * with, so a cod worth 2/0.4 overridden to 6 nutrition yields 1.2 saturation.
     *
     * <p>This lives in one place on purpose. {@link #applyOverride} (the real,
     * server-side eat outcome) and {@link #resolve} (what AppleSkin previews on the
     * client) must produce identical numbers. If the two ever drifted apart the
     * preview would start lying again, which is the exact bug this was factored out
     * to prevent.
     *
     * <p>Zero-nutrition source food scales to zero saturation rather than dividing
     * by zero.
     */
    public static float scaledSaturation(int originalNutrition, float originalSaturation,
                                         int targetNutrition) {
        float ratio = originalNutrition > 0
            ? originalSaturation / (float) originalNutrition
            : 0f;
        return targetNutrition * ratio;
    }

    /**
     * Rebuild {@link FoodProperties} at {@code targetNutrition} with proportionally
     * scaled saturation, preserving {@code canAlwaysEat}.
     *
     * <p>On 26.2 {@code FoodProperties} is just
     * {@code (nutrition, saturation, canAlwaysEat)}: eat duration, the
     * using-converts-to remainder and the consumption effects moved out to the
     * {@code Consumable} data component, which this never touches, so vanilla keeps
     * it intact on the stack. (On the 1.21.1 branch the same method has to carry
     * {@code eatSeconds}, {@code usingConvertsTo} and {@code effects} across too.)
     *
     * <p>Constructs the record directly rather than going through
     * {@link FoodProperties.Builder}: the builder's {@code saturationModifier} is a
     * <em>multiplier</em> that {@code build()} expands through
     * {@code FoodConstants.saturationByModifier}, which would blow the value up (the
     * same trap documented on {@code CraftingPowerEvents.rebuildFood}).
     */
    public static FoodProperties overridden(FoodProperties source, int targetNutrition) {
        return new FoodProperties(
            targetNutrition,
            scaledSaturation(source.nutrition(), source.saturation(), targetNutrition),
            source.canAlwaysEat()
        );
    }

    /**
     * Resolve what {@code stack} is actually worth to a player holding the given
     * nutrition-override configs, without eating it. Used by the AppleSkin
     * integration to preview the same numbers {@link #applyOverride} will produce.
     *
     * <p>Mirrors the server's dispatch exactly:
     * {@code InteractionPowerEvents.onItemUseFinish} walks every
     * {@code modify_food_nutrition} power the player holds and calls
     * {@link #applyOverride} for each one that matches the stack, each recomputing
     * from the same pre-eat baseline, so the <b>last</b> matching override is the
     * one that lands. An override whose nutrition already equals the food's own is
     * a no-op on the server (early return in {@link #applyOverride}) and is skipped
     * here too, leaving any earlier match standing rather than cancelling it.
     *
     * @param defaults the stack's unmodified {@link FoodProperties}
     * @return the effective properties, or {@code null} when no override applies
     *         (caller should leave the vanilla values alone)
     */
    public static FoodProperties resolve(ItemStack stack, FoodProperties defaults,
                                         java.util.List<Config> overrides) {
        if (defaults == null || overrides == null || overrides.isEmpty()) return null;
        FoodProperties result = null;
        for (Config config : overrides) {
            if (config == null) continue;
            if (config.nutrition() == defaults.nutrition()) continue;
            if (!matchesFilter(stack, config)) continue;
            result = overridden(defaults, config.nutrition());
        }
        return result;
    }
}
