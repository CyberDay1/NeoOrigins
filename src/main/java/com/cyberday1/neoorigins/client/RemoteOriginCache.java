package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache of every known player's per-layer origin selections, keyed
 * by player UUID. Populated from {@code SyncRemoteOriginsPayload} broadcasts.
 *
 * <p>Used by {@code PlayerFurRenderLayer} to resolve a remote player's fur
 * cosmetic — the local player's own origin map lives in
 * {@link ClientOriginState} and is not duplicated here, so the render layer
 * must branch on local vs remote.</p>
 *
 * <p>Entries are removed when the server broadcasts an empty origins map for a
 * UUID (logout) and on world disconnect via {@link #clear()}.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class RemoteOriginCache {

    private static final Map<UUID, Map<ResourceLocation, ResourceLocation>> CACHE = new ConcurrentHashMap<>();

    private RemoteOriginCache() {}

    /**
     * Replace the origins mapped to {@code playerUuid}. An empty {@code origins}
     * map removes the entry entirely so stale data doesn't linger after logout.
     */
    public static void put(UUID playerUuid, Map<ResourceLocation, ResourceLocation> origins) {
        if (origins == null || origins.isEmpty()) {
            CACHE.remove(playerUuid);
        } else {
            CACHE.put(playerUuid, Map.copyOf(origins));
        }
    }

    /** Returns the cached origins for {@code playerUuid}, or an empty map if unknown. */
    public static Map<ResourceLocation, ResourceLocation> get(UUID playerUuid) {
        Map<ResourceLocation, ResourceLocation> origins = CACHE.get(playerUuid);
        return origins != null ? origins : Collections.emptyMap();
    }

    public static void remove(UUID playerUuid) {
        CACHE.remove(playerUuid);
    }

    /** Clears the entire cache — called on world disconnect to avoid cross-session leakage. */
    public static void clear() {
        CACHE.clear();
    }
}
