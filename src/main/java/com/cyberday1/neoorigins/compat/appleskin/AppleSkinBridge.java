package com.cyberday1.neoorigins.compat.appleskin;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.client.ClientFoodNutrition;

import net.minecraft.world.food.FoodProperties;
import net.neoforged.neoforge.common.NeoForge;

import squeek.appleskin.api.event.FoodValuesEvent;

/**
 * The one and only class that references AppleSkin ({@code appleskin}) types.
 * Every symbol here resolves against the compile-only {@code appleskin} API stub
 * (see {@code src/apistubs/java/squeek/appleskin/api/event/FoodValuesEvent.java});
 * the class is isolated behind a {@code ModList.isLoaded("appleskin")} gate in
 * {@link NeoOrigins}, so it is never class-loaded when AppleSkin is absent, which
 * keeps {@code NoClassDefFoundError} off the table for the (majority) of users who
 * don't run it.
 *
 * <h3>The bug this closes</h3>
 * {@code neoorigins:modify_food_nutrition} does not rewrite an item's
 * {@code FOOD} data component. It lets vanilla eat the item, then rewrites the
 * player's food/saturation server-side from a pre-eat baseline
 * ({@code ModifyFoodNutritionPower.applyOverride}). AppleSkin builds its tooltip
 * and held-food HUD preview by reading {@code DataComponents.FOOD} off the stack
 * client-side, so it could never see the override: an aquatic origin holding a
 * cod was shown the vanilla hunger/saturation while eating it actually gave the
 * elevated value. This is a missing integration, not a regression, and it affected
 * every diet-override origin (aquatic fish diets, Caveborn, vampire, and so on), so
 * the hook is written generically off the power rather than special-cased to fish.
 *
 * <h3>Integration shape</h3>
 * AppleSkin publishes {@link FoodValuesEvent} for exactly this. It is posted on
 * {@code NeoForge.EVENT_BUS} from {@code FoodHelper.query}, whose only callers are
 * AppleSkin's client-side {@code HUDOverlayHandler} and
 * {@code TooltipOverlayHandler}, so it is a logical-client-only event, and the
 * override values have to have been synced down first
 * ({@code SyncFoodNutritionPayload} to {@link ClientFoodNutrition}).
 *
 * <p>We write {@code modifiedFoodProperties} and leave
 * {@code defaultFoodProperties} alone, which is what makes AppleSkin render the
 * vanilla value struck through next to ours rather than silently replacing it.
 *
 * <p>The event carries its own {@code player}, so we resolve against that rather
 * than assuming it is the local player: in practice both AppleSkin call sites pass
 * the local player, but our synced override data only ever describes the local
 * player, so answering for anyone else would be a guess. Non-local players are
 * left untouched.
 */
public final class AppleSkinBridge {

    private AppleSkinBridge() {}

    /**
     * Wire the food-values listener. Called from {@link NeoOrigins} on the physical
     * client only, already behind the {@code ModList.isLoaded("appleskin")} gate.
     */
    public static void register() {
        try {
            NeoForge.EVENT_BUS.addListener(AppleSkinBridge::onFoodValues);
            NeoOrigins.LOGGER.info("[Compat] AppleSkin detected, modify_food_nutrition overrides "
                + "will show in the food tooltip and held-food HUD preview");
        } catch (Throwable t) {
            NeoOrigins.LOGGER.warn("[Compat] AppleSkin detected but the food-values hook could not "
                + "be wired ({}), diet overrides will still apply on eat, but the preview will "
                + "show vanilla values", t.toString());
        }
    }

    private static void onFoodValues(FoodValuesEvent event) {
        // Fast out for the overwhelmingly common case: an origin with no diet
        // override at all. This runs per frame while food is held.
        if (ClientFoodNutrition.isEmpty()) return;
        if (event.player == null || event.itemStack == null) return;
        if (!ClientActivePowers.isLocalPlayer(event.player)) return;

        // Prefer whatever another mod already put in modifiedFoodProperties so we
        // compose with it instead of stomping it; fall back to the defaults.
        FoodProperties base = event.modifiedFoodProperties != null
            ? event.modifiedFoodProperties
            : event.defaultFoodProperties;
        if (base == null) return;

        FoodProperties resolved = ClientFoodNutrition.resolve(event.itemStack, base);
        if (resolved != null) event.modifiedFoodProperties = resolved;
    }
}
