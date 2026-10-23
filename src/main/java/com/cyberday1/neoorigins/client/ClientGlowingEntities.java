package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.network.payload.SyncGlowingEntitiesPayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side map of the entity ids the local player's {@code neoorigins:entity_glow}
 * powers outline for them, to the outline colour for each. Filled wholesale by
 * {@code SyncGlowingEntitiesPayload} and read every frame by
 * {@code MinecraftEntityGlowMixin} and {@code EntityRendererGlowColorMixin}. Empty by
 * default, which is vanilla behaviour. Cleared on disconnect.
 */
public final class ClientGlowingEntities {

    private static final Map<Integer, Integer> GLOWING = new ConcurrentHashMap<>();

    private ClientGlowingEntities() {}

    public static void set(Map<Integer, Integer> colorsById) {
        GLOWING.clear();
        GLOWING.putAll(colorsById);
    }

    public static boolean isGlowing(int entityId) {
        return !GLOWING.isEmpty() && GLOWING.containsKey(entityId);
    }

    /** The outline colour to draw, or {@code null} to keep the entity's own. */
    public static Integer colorFor(int entityId) {
        if (GLOWING.isEmpty()) return null;
        Integer color = GLOWING.get(entityId);
        return color == null || color == SyncGlowingEntitiesPayload.TEAM_COLOR ? null : color;
    }

    public static void clear() {
        GLOWING.clear();
    }
}
