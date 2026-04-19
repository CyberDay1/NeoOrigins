package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;

/**
 * Client-side mirror of the local player's currently-granted powers.
 *
 * Populated by {@code SyncActivePowersPayload} on login, respawn, origin change,
 * and toggle change. Queried by client-predicted movement mixins (wall-climb,
 * flight, climbing) and the 2.0 origin editor / power tester GUI.
 *
 * Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientActivePowers {

    private static Map<ResourceLocation, Boolean> powers = Map.of();

    public static void set(Map<ResourceLocation, Boolean> data) {
        powers = Map.copyOf(data);
    }

    public static void clear() {
        powers = Map.of();
    }

    /** True if the local player has power {@code id} granted, regardless of toggle state. */
    public static boolean hasPower(ResourceLocation id) {
        return powers.containsKey(id);
    }

    /**
     * True if the local player has power {@code id} granted AND it's either non-toggleable
     * or toggled on. This is the query client-predicted behavior should use.
     */
    public static boolean isActive(ResourceLocation id) {
        return Boolean.TRUE.equals(powers.get(id));
    }

    /** Unmodifiable view of the full map — for debug HUDs and the power-tester screen. */
    public static Map<ResourceLocation, Boolean> all() {
        return Collections.unmodifiableMap(powers);
    }

    private ClientActivePowers() {}
}
