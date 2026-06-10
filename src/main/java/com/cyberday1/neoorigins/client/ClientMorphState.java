package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players are currently morphed (via the
 * {@code neoorigins:entity_model} power) and into what entity type.
 *
 * <p>Keyed by entity id — {@code RenderPlayerEvent} hands us the player's
 * render state (whose {@code id} field is the entity id), so id lookup is the
 * cheapest path. Populated by {@code SyncPlayerMorphPayload}, which the server
 * broadcasts to every client tracking the morphed player. Querying with the
 * local player's id therefore also tells us whether to hide our own
 * first-person hands.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientMorphState {

    private static final Map<Integer, Identifier> MORPHS = new ConcurrentHashMap<>();

    private ClientMorphState() {}

    /** Record (or clear, when {@code entityType} is null) the morph for a player. */
    public static void set(int entityId, Identifier entityType) {
        if (entityType == null) {
            MORPHS.remove(entityId);
        } else {
            MORPHS.put(entityId, entityType);
        }
    }

    /** The entity type {@code entityId} is morphed into, or null if not morphed. */
    public static Identifier getMorph(int entityId) {
        return MORPHS.get(entityId);
    }

    /** True if the given player entity id is currently morphed. */
    public static boolean isMorphed(int entityId) {
        return MORPHS.containsKey(entityId);
    }

    public static void clear() {
        MORPHS.clear();
    }
}
