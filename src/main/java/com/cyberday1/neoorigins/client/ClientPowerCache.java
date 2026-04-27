package com.cyberday1.neoorigins.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache of power display data received from the server.
 * Used by the origin selection screen when full PowerHolder objects are not available
 * (i.e. on a dedicated server where PowerDataManager only runs server-side).
 */
public final class ClientPowerCache {

    public record Entry(Component name, Component description, boolean active, boolean toggle, boolean hidden) {

        /** Back-compat ctor for callers pre-dating the {@code hidden} flag. */
        public Entry(Component name, Component description, boolean active, boolean toggle) {
            this(name, description, active, toggle, false);
        }
    }

    private static Map<Identifier, Entry> cache = Map.of();

    public static void set(Map<Identifier, Entry> data) {
        cache = Map.copyOf(data);
    }

    public static Entry get(Identifier id) {
        return cache.get(id);
    }

    public static void clear() {
        cache = Map.of();
    }

    private ClientPowerCache() {}
}
