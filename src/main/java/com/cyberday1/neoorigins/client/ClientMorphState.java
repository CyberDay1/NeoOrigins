package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.power.morph.MorphSpec;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of how each morphed player (via the
 * {@code neoorigins:entity_model} power) should be rendered.
 *
 * <p>Keyed by entity id — {@code RenderPlayerEvent} hands us the player entity
 * directly, so id lookup is the cheapest path. Populated by
 * {@code SyncPlayerMorphPayload}, which the server broadcasts to every client
 * tracking the morphed player. Querying with the local player's id therefore
 * also tells us whether to hide our own first-person hands.
 *
 * <p>The stored value is the already-resolved {@link MorphSpec}: the server
 * has collapsed any referenced morph definition and inline overrides into it,
 * so nothing here needs to know about morph ids.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientMorphState {

    private static final Map<Integer, MorphSpec> MORPHS = new ConcurrentHashMap<>();

    private ClientMorphState() {}

    /** Record (or clear, when {@code spec} is null) the morph for a player. */
    public static void set(int entityId, MorphSpec spec) {
        if (spec == null) {
            MORPHS.remove(entityId);
        } else {
            MORPHS.put(entityId, spec);
        }
    }

    /** The full morph description for {@code entityId}, or null if not morphed. */
    public static MorphSpec getSpec(int entityId) {
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
