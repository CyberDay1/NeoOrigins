package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.OriginDataManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Client-side cache of power display data received from the server.
 * Used by the origin selection screen when full PowerHolder objects are not available
 * (i.e. on a dedicated server where PowerDataManager only runs server-side).
 *
 * <p>{@link Entry#typeId} carries the server-side registry ID of the power's type
 * (e.g. {@code neoorigins:wall_climbing}) so client code can reason about a power
 * without shipping the full PowerType class to the client.
 */
public final class ClientPowerCache {

    public record Entry(Component name, Component description, boolean active, boolean toggle,
                        ResourceLocation typeId, boolean hidden) {

        /** Back-compat ctor for callers pre-dating the {@code hidden} flag. */
        public Entry(Component name, Component description, boolean active, boolean toggle, ResourceLocation typeId) {
            this(name, description, active, toggle, typeId, false);
        }
    }

    private static Map<ResourceLocation, Entry> cache = Map.of();

    public static void set(Map<ResourceLocation, Entry> data) {
        cache = Map.copyOf(data);
    }

    public static Entry get(ResourceLocation id) {
        return cache.get(id);
    }

    public static void clear() {
        cache = Map.of();
    }

    /**
     * Returns true when any origin currently selected on the local player's
     * client-side origin state carries a power whose type registry ID equals
     * {@code typeId}. Used by mixins that need client-side prediction to
     * agree with server-side power behaviour (e.g. wall climbing).
     */
    public static boolean localPlayerHasPowerOfType(ResourceLocation typeId) {
        if (typeId == null || cache.isEmpty()) return false;
        Map<ResourceLocation, ResourceLocation> origins = ClientOriginState.getOrigins();
        if (origins.isEmpty()) return false;
        for (ResourceLocation originId : origins.values()) {
            Origin origin = OriginDataManager.INSTANCE.getOrigin(originId);
            if (origin == null) continue;
            for (ResourceLocation powerId : origin.powers()) {
                Entry entry = cache.get(powerId);
                if (entry != null && typeId.equals(entry.typeId())) return true;
            }
        }
        return false;
    }

    private ClientPowerCache() {}
}
