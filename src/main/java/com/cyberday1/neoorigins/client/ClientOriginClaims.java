package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side view of which origins are locked (claimed by another player) in
 * unique-enforced layers, used to grey out the relevant buttons in the origin
 * picker. Populated by {@code SyncOriginClaimsPayload}; the server already
 * excludes the local player's own claims, so anything present here is "taken".
 */
public final class ClientOriginClaims {

    private static Map<ResourceLocation, Map<ResourceLocation, String>> claims = Collections.emptyMap();

    private ClientOriginClaims() {}

    public static void set(Map<ResourceLocation, Map<ResourceLocation, String>> incoming) {
        claims = incoming == null ? Collections.emptyMap() : new HashMap<>(incoming);
    }

    public static void clear() {
        claims = Collections.emptyMap();
    }

    /** True if {@code origin} in {@code layer} is claimed by another player. */
    public static boolean isLocked(ResourceLocation layer, ResourceLocation origin) {
        Map<ResourceLocation, String> byOrigin = claims.get(layer);
        return byOrigin != null && byOrigin.containsKey(origin);
    }

    /** Display name of the player holding the claim, or {@code null} if unlocked. */
    public static String owner(ResourceLocation layer, ResourceLocation origin) {
        Map<ResourceLocation, String> byOrigin = claims.get(layer);
        return byOrigin == null ? null : byOrigin.get(origin);
    }
}
