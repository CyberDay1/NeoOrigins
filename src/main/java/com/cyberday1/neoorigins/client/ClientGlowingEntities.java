package com.cyberday1.neoorigins.client;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side set of entity ids the local player's {@code neoorigins:entity_glow}
 * power outlines for them. Filled wholesale by {@code SyncGlowingEntitiesPayload}
 * and read every frame by {@code MinecraftEntityGlowMixin}. Empty by default, which
 * is vanilla behaviour. Cleared on disconnect with the other client mirrors.
 */
public final class ClientGlowingEntities {

    private static final Set<Integer> GLOWING = ConcurrentHashMap.newKeySet();

    private ClientGlowingEntities() {}

    public static void set(Collection<Integer> entityIds) {
        GLOWING.clear();
        GLOWING.addAll(entityIds);
    }

    public static boolean isGlowing(int entityId) {
        return !GLOWING.isEmpty() && GLOWING.contains(entityId);
    }

    public static void clear() {
        GLOWING.clear();
    }
}
