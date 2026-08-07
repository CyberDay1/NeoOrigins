package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.network.payload.SyncFoodNutritionPayload;
import com.cyberday1.neoorigins.power.builtin.ModifyFoodNutritionPower;

import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Client-side mirror of the local player's {@code neoorigins:modify_food_nutrition}
 * overrides, as last pushed by {@code SyncFoodNutritionPayload}.
 *
 * <p>{@link ClientActivePowers} only carries power ids and capability tags, not
 * power <em>config values</em>, so it can't answer "what is a cod worth to this
 * origin?". This holder exists purely to make that question answerable client-side,
 * which is what the AppleSkin tooltip/HUD preview needs.
 *
 * <p>Read by {@code compat.appleskin.AppleSkinBridge}. Nothing else should depend
 * on it: the authoritative eat outcome is still computed server-side in
 * {@code ModifyFoodNutritionPower.applyOverride}, and this is a display mirror of
 * that, never a substitute for it.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientFoodNutrition {

    private static List<ModifyFoodNutritionPower.Config> overrides = List.of();

    public static void set(List<SyncFoodNutritionPayload.Entry> entries) {
        // Inflate to real power Configs once on receipt rather than per tooltip
        // frame — AppleSkin queries this every frame the player holds food.
        overrides = entries.stream().map(SyncFoodNutritionPayload.Entry::toConfig).toList();
    }

    public static void clear() {
        overrides = List.of();
    }

    /** True when the local player has no diet override at all (the common case). */
    public static boolean isEmpty() {
        return overrides.isEmpty();
    }

    /** Unmodifiable view of the synced overrides, in server dispatch order. */
    public static List<ModifyFoodNutritionPower.Config> overrides() {
        return overrides;
    }

    /**
     * The effective {@link FoodProperties} for {@code stack} under the local
     * player's overrides, or {@code null} when none applies (leave the vanilla
     * values alone).
     *
     * <p>Delegates straight to {@link ModifyFoodNutritionPower#resolve} — the same
     * filter and the same nutrition/saturation math the server's eat path uses — so
     * the preview cannot disagree with the real outcome.
     */
    public static FoodProperties resolve(ItemStack stack, FoodProperties defaults) {
        return ModifyFoodNutritionPower.resolve(stack, defaults, overrides);
    }

    private ClientFoodNutrition() {}
}
