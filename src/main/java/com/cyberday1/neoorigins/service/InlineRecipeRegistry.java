package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Stub on 26.1. Inline {@code origins:recipe} JSON injection is supported on
 * 1.21.1 because {@code RecipeManager} exposes {@code replaceRecipes}; the
 * 26.1 {@code RecipeManager} stores recipes in an immutable {@link
 * net.minecraft.world.item.crafting.RecipeMap}, so the same hook isn't
 * available. The API surface here exists only so {@link
 * com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader#parseRecipe}
 * can compile on both branches; on 26.1 we log a one-shot warning per
 * inline recipe and let the power's {@code awardRecipes} call fail
 * gracefully (the recipe id won't resolve, so nothing is awarded).
 *
 * <p>Lifting this restriction on 26.1 needs either a mixin against
 * {@code RecipeManager.recipes}, or a coatrack registry built on top of the
 * NeoForge recipe-event APIs. Out of scope for v2.1.4.
 */
public final class InlineRecipeRegistry {

    private InlineRecipeRegistry() {}

    private static final Set<Identifier> WARNED = new HashSet<>();

    /**
     * Stable synthetic id for a given power. Matches the 2.1 scheme so packs
     * authoring against either branch produce the same id.
     */
    public static Identifier syntheticId(Identifier powerId) {
        String safe = powerId.getNamespace() + "_" + powerId.getPath().replace('/', '_');
        return Identifier.fromNamespaceAndPath("neoorigins", "compat_inline/" + safe);
    }

    /**
     * On 26.1 this just emits a one-shot warning; the inline JSON is
     * discarded. {@link com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader}
     * still treats the call as a registration so the power compiles, but
     * the recipe never appears in the {@code RecipeManager}.
     */
    public static void register(Identifier syntheticId, JsonObject inlineJson) {
        if (WARNED.add(syntheticId)) {
            NeoOrigins.LOGGER.warn(
                "[CompatB] inline origins:recipe is not supported on 26.1 ({}) — "
              + "use a string recipe id pointing to a data/<ns>/recipe/...json file instead",
                syntheticId);
        }
    }

    /** Called from OriginsCompatPowerLoader at the start of each apply() pass. */
    public static void resetPending() {
        // No-op on 26.1 — nothing is pending to clear.
    }
}
