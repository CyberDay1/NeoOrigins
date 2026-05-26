package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
                var parsed = Recipe.CODEC.parse(ops, e.getValue());
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
}
