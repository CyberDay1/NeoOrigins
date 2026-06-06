package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.recipe.OriginGate;
import com.cyberday1.neoorigins.recipe.OriginGatedRecipe;
import com.cyberday1.neoorigins.recipe.RecipeManagerReplaceAccess;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects {@code origins:recipe} powers that ship an inline recipe JSON (or
 * point to an existing recipe id) and injects / gates them in the live
 * {@link RecipeManager} after each datapack reload.
 *
 * <p>26.1 dropped the public {@code RecipeManager.replaceRecipes} hook that
 * 1.21.1 uses (recipes now live in an otherwise-immutable {@code RecipeMap}), so
 * the actual map swap goes through {@link RecipeManagerReplaceAccess}, a
 * duck-type interface added to {@code RecipeManager} by
 * {@code mixin.recipe.RecipeManagerReplaceMixin}. With that hook in place this
 * class behaves identically to the 1.21.1 branch.
 *
 * <p>{@link com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader#parseRecipe}
 * populates this registry during its {@code apply()} pass; injection runs from
 * {@link OnDatapackSyncEvent} (which fires once per datapack reload, after every
 * reload listener — including vanilla's {@code RecipeManager} — has completed,
 * and again on each player login).
 *
 * <p>Per-player gating: every injected inline recipe and every referenced recipe
 * is wrapped in an {@link OriginGatedRecipe} carrying a single
 * {@link OriginGate.HasPower} gate keyed to the {@code origins:recipe} power's
 * own id. Only players who currently hold that power pass the craft-time gate,
 * so the recipe is no longer globally craftable. The power's {@code onGranted}
 * still calls {@code player.awardRecipes} so the recipe shows in the holder's
 * recipe book.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class InlineRecipeRegistry {

    private InlineRecipeRegistry() {}

    /** Synthetic id → inline JSON. Collected during reload, injected post-reload. */
    private static final Map<Identifier, JsonObject> PENDING = new HashMap<>();

    /** Inline-recipe ids successfully injected. Tracked so we can re-inject after subsequent reloads. */
    private static final Map<Identifier, JsonObject> INJECTED = new HashMap<>();

    /** Synthetic inline-recipe id → owning power id, for the has_power craft gate. */
    private static final Map<Identifier, Identifier> PENDING_INLINE_POWER = new HashMap<>();
    private static final Map<Identifier, Identifier> INJECTED_INLINE_POWER = new HashMap<>();

    /** Existing (referenced) recipe id → owning power id. The live recipe is wrapped in place. */
    private static final Map<Identifier, Identifier> PENDING_REF_GATE = new HashMap<>();
    private static final Map<Identifier, Identifier> INJECTED_REF_GATE = new HashMap<>();

    /**
     * Build the synthetic recipe id for a given power id. Stable across reloads
     * so {@code player.awardRecipes} continues to resolve the same recipe holder
     * after /reload. Matches the 1.21.1 scheme so packs authoring against either
     * branch produce the same id.
     */
    public static Identifier syntheticId(Identifier powerId) {
        String safe = powerId.getNamespace() + "_" + powerId.getPath().replace('/', '_');
        return Identifier.fromNamespaceAndPath("neoorigins", "compat_inline/" + safe);
    }

    /** Called from parseRecipe with the inline JSON. Idempotent within a reload pass. */
    public static void register(Identifier syntheticId, JsonObject inlineJson) {
        PENDING.put(syntheticId, inlineJson);
    }

    /** Records the power that owns an inline recipe so its injected form can be has_power gated. */
    public static void registerInlinePower(Identifier syntheticId, Identifier powerId) {
        PENDING_INLINE_POWER.put(syntheticId, powerId);
    }

    /** Records that an existing referenced recipe should be wrapped with a has_power gate. */
    public static void registerRefGate(Identifier recipeId, Identifier powerId) {
        PENDING_REF_GATE.put(recipeId, powerId);
    }

    /** Called from OriginsCompatPowerLoader at the start of each apply() pass. */
    public static void resetPending() {
        PENDING.clear();
        PENDING_INLINE_POWER.clear();
        PENDING_REF_GATE.clear();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // Promote the pending set; merge with anything previously injected so a
        // partial-reload (e.g. a different listener triggered the sync) doesn't
        // drop earlier inline recipes / gates.
        if (!PENDING.isEmpty()) {
            INJECTED.putAll(PENDING);
            PENDING.clear();
        }
        if (!PENDING_INLINE_POWER.isEmpty()) {
            INJECTED_INLINE_POWER.putAll(PENDING_INLINE_POWER);
            PENDING_INLINE_POWER.clear();
        }
        if (!PENDING_REF_GATE.isEmpty()) {
            INJECTED_REF_GATE.putAll(PENDING_REF_GATE);
            PENDING_REF_GATE.clear();
        }
        if (INJECTED.isEmpty() && INJECTED_REF_GATE.isEmpty()) return;
        inject(server.getRecipeManager(), server.registryAccess());
    }

    private static void inject(RecipeManager recipeManager, RegistryAccess access) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);

        // Start with the recipes already present so the rebuild doesn't wipe them.
        List<RecipeHolder<?>> all = new ArrayList<>(recipeManager.getRecipes());

        int injected = 0;
        for (Map.Entry<Identifier, JsonObject> e : INJECTED.entrySet()) {
            Identifier id = e.getKey();
            ResourceKey<Recipe<?>> key = ResourceKey.create(Registries.RECIPE, id);
            // Skip if already present (e.g. server-start re-injection after /reload preserved them).
            if (recipeManager.byKey(key).isPresent()) continue;
            try {
                JsonObject normalized = normalizeApoliRecipe(e.getValue());
                var parsed = Recipe.CODEC.parse(ops, normalized);
                if (parsed.error().isPresent()) {
                    NeoOrigins.LOGGER.warn("[CompatB] inline recipe {} failed to decode: {}",
                        id, parsed.error().get().message());
                    continue;
                }
                Recipe<?> recipe = parsed.result().orElseThrow();
                // Gate the recipe by the owning power so only holders can craft it.
                Identifier powerId = INJECTED_INLINE_POWER.get(id);
                if (powerId != null && recipe instanceof CraftingRecipe cr && !(cr instanceof OriginGatedRecipe)) {
                    recipe = new OriginGatedRecipe(List.of(new OriginGate.HasPower(powerId)), cr);
                } else if (powerId != null) {
                    NeoOrigins.LOGGER.warn("[CompatB] inline recipe {} is not a crafting recipe — cannot has_power gate; "
                        + "it will remain globally craftable", id);
                }
                all.add(new RecipeHolder<>(key, recipe));
                injected++;
            } catch (Exception ex) {
                NeoOrigins.LOGGER.warn("[CompatB] inline recipe {} threw during decode: {}", id, ex.getMessage());
            }
        }

        // Wrap referenced (string-id) recipes in place with their has_power gate.
        int gated = 0;
        for (Map.Entry<Identifier, Identifier> e : INJECTED_REF_GATE.entrySet()) {
            Identifier recipeId = e.getKey();
            Identifier powerId = e.getValue();
            for (int i = 0; i < all.size(); i++) {
                RecipeHolder<?> h = all.get(i);
                if (!h.id().identifier().equals(recipeId)) continue;
                if (h.value() instanceof OriginGatedRecipe) break; // already gated
                if (h.value() instanceof CraftingRecipe cr) {
                    all.set(i, new RecipeHolder<>(h.id(),
                        new OriginGatedRecipe(List.of(new OriginGate.HasPower(powerId)), cr)));
                    gated++;
                } else {
                    NeoOrigins.LOGGER.warn("[CompatB] origins:recipe referenced recipe {} is not a crafting recipe — "
                        + "cannot has_power gate; it remains globally craftable", recipeId);
                }
                break;
            }
        }

        if (injected > 0 || gated > 0) {
            ((RecipeManagerReplaceAccess) recipeManager).neoorigins$replaceRecipes(all);
            NeoOrigins.LOGGER.info("[CompatB] injected {} inline recipe(s) and gated {} referenced recipe(s)",
                injected, gated);
        }
    }

    /**
     * Apoli ships inline recipes in the pre-1.20.5 crafting JSON shape. The
     * ingredient object form — {@code {"item": ...}} / {@code {"tag": ...}} — is
     * still what the (either-list-or-object) ingredient codec wants, so
     * ingredients are left untouched. The one breaking change is the
     * <b>result</b>: it is now an {@code ItemStack} keyed by {@code "id"}
     * (was {@code "item"}), with {@code "count"} unchanged.
     *
     * <p>Mutates a deep copy so the registered source JSON is left intact for
     * re-injection after subsequent reloads.
     */
    private static JsonObject normalizeApoliRecipe(JsonObject src) {
        JsonObject out = src.deepCopy();
        if (out.has("result")) {
            out.add("result", normalizeResult(out.get("result")));
        }
        return out;
    }

    /** {@code {"item": X, "count": N}} → {@code {"id": X, "count": N}}; bare {@code "X"} → {@code {"id": "X"}}. */
    private static JsonElement normalizeResult(JsonElement el) {
        if (el.isJsonPrimitive()) {
            JsonObject o = new JsonObject();
            o.add("id", el);
            return o;
        }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item") && !o.has("id")) {
                o.add("id", o.get("item"));
                o.remove("item");
            }
            // Apoli's "amount" alias for stack size, if a pack used it.
            if (o.has("amount") && !o.has("count")) {
                o.add("count", o.get("amount"));
                o.remove("amount");
            }
            return o;
        }
        return el;
    }
}
