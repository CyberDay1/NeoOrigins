package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of the local player's currently-granted powers and the union
 * of their capability tags.
 *
 * Populated by {@code SyncActivePowersPayload} on login, respawn, origin change,
 * and toggle change. Queried by client-predicted movement mixins (wall-climb,
 * flight, climbing) and the 2.0 origin editor / power tester GUI.
 *
 * Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientActivePowers {

    private static Map<Identifier, Boolean> powers = Map.of();
    private static Set<String> capabilities = Set.of();

    public static void set(Map<Identifier, Boolean> powersData, Set<String> capData) {
        powers = Map.copyOf(powersData);
        capabilities = Set.copyOf(capData);
    }

    public static void clear() {
        powers = Map.of();
        capabilities = Set.of();
    }

    /** True if the local player has power {@code id} granted, regardless of toggle state. */
    public static boolean hasPower(Identifier id) {
        return powers.containsKey(id);
    }

    /**
     * True if the local player has power {@code id} granted AND it's either non-toggleable
     * or toggled on. This is the query client-predicted behavior should use.
     */
    public static boolean isActive(Identifier id) {
        return Boolean.TRUE.equals(powers.get(id));
    }

    /**
     * True if any active power on the local player grants the given capability tag.
     * This is the preferred query for client-predicted mixins — they should ask
     * "do I have wall_climb?" rather than "do I have power X?".
     */
    public static boolean hasCapability(String tag) {
        return capabilities.contains(tag);
    }

    /** Unmodifiable view of the full map — for debug HUDs and the power-tester screen. */
    public static Map<Identifier, Boolean> all() {
        return Collections.unmodifiableMap(powers);
    }

    /** Unmodifiable view of active capability tags. */
    public static Set<String> activeCapabilities() {
        return Collections.unmodifiableSet(capabilities);
    }

    private ClientActivePowers() {}
}
