package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.api.origin.Origin;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Client-side cache of per-origin fur metadata (model, texture and animation paths).
 *
 * <p>Populated from the {@code SyncOriginRegistryPayload} Origin objects each time
 * the client receives the registry sync. Consumed by {@code PlayerFurRenderLayer}
 * to look up the cosmetic GeckoLib model for the local player's active origin.</p>
 *
 * <p>Origins that do not declare any fur fields are omitted from the cache entirely,
 * so absence is equivalent to "no fur for this origin".</p>
 */
public final class ClientOriginFurCache {

    /**
     * Per-origin fur asset bundle. All three fields are resource locations; the
     * animation path is always present here (defaulted to a sentinel) but the
     * render layer only uses it if the geo model requires animations.
     */
    public record Entry(ResourceLocation model, ResourceLocation texture, ResourceLocation animation) {}

    private static Map<ResourceLocation, Entry> cache = Map.of();

    /**
     * Rebuild the cache from a fresh origin registry. Origins without a model or
     * texture are skipped — animation alone is not enough to render anything.
     */
    public static void rebuild(Map<ResourceLocation, Origin> origins) {
        Map<ResourceLocation, Entry> next = new HashMap<>(origins.size());
        for (var entry : origins.entrySet()) {
            Origin origin = entry.getValue();
            Optional<ResourceLocation> model = origin.furModel();
            Optional<ResourceLocation> texture = origin.furTexture();
            if (model.isEmpty() || texture.isEmpty()) continue;

            ResourceLocation animation = origin.furAnimation().orElse(
                ResourceLocation.fromNamespaceAndPath("neoorigins", "animations/fur/_empty.animation.json"));
            next.put(entry.getKey(), new Entry(model.get(), texture.get(), animation));
        }
        cache = Map.copyOf(next);
    }

    /** Returns the fur bundle for the given origin id, or {@code null} if the origin has no fur. */
    public static Entry get(ResourceLocation originId) {
        return cache.get(originId);
    }

    public static Map<ResourceLocation, Entry> all() {
        return Collections.unmodifiableMap(cache);
    }

    public static void clear() {
        cache = Map.of();
    }

    private ClientOriginFurCache() {}
}
