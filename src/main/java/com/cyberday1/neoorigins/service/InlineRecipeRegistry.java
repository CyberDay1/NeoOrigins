package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
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
 * Collects {@code origins:recipe} powers that ship an inline recipe JSON
 * (rather than a recipe id pointer) and injects them into the live
 * {@link RecipeManager} after each datapack reload.
 *
 * <p>{@link com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader#parseRecipe}
 * populates this registry during its {@code apply()} pass. The injection
 * itself runs from {@link OnDatapackSyncEvent} (which fires once per
 * datapack reload, after every reload listener — including vanilla's
 * {@code RecipeManager} — has completed) using
 * {@link RecipeManager#replaceRecipes}, the same hook KubeJS-style mods
 * use for runtime recipe registration.
 *
 * <p>Limitation: this grants the recipe globally, not per-player. The
 * power's {@code onGranted} still calls {@code player.awardRecipes} so
 * the recipe shows in the holder's recipe book, but other players can
 * also craft it if they discover it. Per-player gating would require a
 * crafting-event veto and is out of scope for v2.1.4.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class InlineRecipeRegistry {

    private InlineRecipeRegistry() {}

    /** Synthetic id → inline JSON. Collected during reload, injected post-reload. */
    private static final Map<ResourceLocation, JsonObject> PENDING = new HashMap<>();

    /** Inline-recipe ids successfully injected. Tracked so we can re-inject after subsequent reloads. */
    private static final Map<ResourceLocation, JsonObject> INJECTED = new HashMap<>();

    /**
     * Build the synthetic recipe id for a given power id. Stable across
     * reloads so {@code player.awardRecipes} continues to resolve the
     * same recipe holder after /reload.
     */
    public static ResourceLocation syntheticId(ResourceLocation powerId) {
        String safe = powerId.getNamespace() + "_" + powerId.getPath().replace('/', '_');
        return ResourceLocation.fromNamespaceAndPath("neoorigins", "compat_inline/" + safe);
    }

    /** Called from parseRecipe with the inline JSON. Idempotent within a reload pass. */
    public static void register(ResourceLocation syntheticId, JsonObject inlineJson) {
        PENDING.put(syntheticId, inlineJson);
    }

    /** Called from OriginsCompatPowerLoader at the start of each apply() pass. */
    public static void resetPending() {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        // Promote the pending set; merge with anything previously injected so
        // a partial-reload (e.g. a different listener triggered the sync)
        // doesn't drop earlier inline recipes.
        if (!PENDING.isEmpty()) {
            INJECTED.putAll(PENDING);
            PENDING.clear();
        }
        if (INJECTED.isEmpty()) return;
        inject(server.getRecipeManager(), server.registryAccess());
    }

    private static void inject(RecipeManager recipeManager, RegistryAccess access) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);

        // Start with the recipes already present so replaceRecipes doesn't wipe them.
        List<RecipeHolder<?>> all = new ArrayList<>(recipeManager.getRecipes());
        int injected = 0;
        for (Map.Entry<ResourceLocation, JsonObject> e : INJECTED.entrySet()) {
            ResourceLocation id = e.getKey();
            // Skip if already present (e.g. server-start re-injection after /reload preserved them).
            if (recipeManager.byKey(id).isPresent()) continue;
            try {
                JsonObject normalized = normalizeApoliRecipe(e.getValue());
                var parsed = Recipe.CODEC.parse(ops, normalized);
                if (parsed.error().isPresent()) {
                    NeoOrigins.LOGGER.warn("[CompatB] inline recipe {} failed to decode: {}",
                        id, parsed.error().get().message());
                    continue;
                }
                Recipe<?> recipe = parsed.result().orElseThrow();
                all.add(new RecipeHolder<>(id, recipe));
                injected++;
            } catch (Exception ex) {
                NeoOrigins.LOGGER.warn("[CompatB] inline recipe {} threw during decode: {}", id, ex.getMessage());
            }
        }
        if (injected > 0) {
            recipeManager.replaceRecipes(all);
            NeoOrigins.LOGGER.info("[CompatB] injected {} inline recipe(s) into RecipeManager", injected);
        }
    }

    /**
     * Apoli ships inline recipes in the pre-1.20.5 Fabric crafting format, which
     * the 1.21.1 vanilla {@link Recipe#CODEC} no longer accepts. Two breaking
     * changes are bridged here:
     * <ul>
     *   <li><b>Ingredients</b> are now bare strings — {@code "minecraft:flint"} or
     *       {@code "#minecraft:planks"} — not {@code {"item": ...}} /
     *       {@code {"tag": ...}} objects.</li>
     *   <li><b>Result</b> is now an {@code ItemStack} keyed by {@code "id"} (was
     *       {@code "item"}); {@code "count"} is unchanged.</li>
     * </ul>
     * Mutates a deep copy so the registered source JSON is left intact for
     * re-injection after subsequent reloads.
     */
    private static JsonObject normalizeApoliRecipe(JsonObject src) {
        JsonObject out = src.deepCopy();

        // Shapeless / smithing: ingredients[]
        if (out.get("ingredients") instanceof JsonArray arr) {
            JsonArray fixed = new JsonArray();
            for (JsonElement el : arr) fixed.add(normalizeIngredient(el));
            out.add("ingredients", fixed);
        }
        // Smelting / stonecutting / single-ingredient: ingredient
        if (out.has("ingredient")) {
            out.add("ingredient", normalizeIngredient(out.get("ingredient")));
        }
        // Shaped: key{ char -> ingredient }
        if (out.get("key") instanceof JsonObject key) {
            JsonObject fixedKey = new JsonObject();
            for (Map.Entry<String, JsonElement> k : key.entrySet()) {
                fixedKey.add(k.getKey(), normalizeIngredient(k.getValue()));
            }
            out.add("key", fixedKey);
        }
        // Result: rename item -> id (object form) or wrap bare string.
        if (out.has("result")) {
            out.add("result", normalizeResult(out.get("result")));
        }
        return out;
    }

    /** {@code {"item": X}} → {@code "X"}; {@code {"tag": T}} → {@code "#T"}; passes strings/arrays through. */
    private static JsonElement normalizeIngredient(JsonElement el) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("item")) return new JsonPrimitive(o.get("item").getAsString());
            if (o.has("tag")) return new JsonPrimitive("#" + o.get("tag").getAsString());
            return el;
        }
        if (el.isJsonArray()) {
            JsonArray fixed = new JsonArray();
            for (JsonElement e : el.getAsJsonArray()) fixed.add(normalizeIngredient(e));
            return fixed;
        }
        return el; // already a bare id or "#tag" string
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
