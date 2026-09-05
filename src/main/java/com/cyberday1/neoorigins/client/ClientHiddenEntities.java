package com.cyberday1.neoorigins.client;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side set of entity ids the local player's
 * {@code neoorigins:prevent_entity_render} power hides from them.
 *
 * <p>Populated wholesale by {@code SyncHiddenEntitiesPayload}, which the server
 * re-sends only when the set changes. Read on the render hot path by
 * {@code EntityRenderDispatcherHideMixin}, so the backing set is a plain hash set
 * lookup and nothing is computed here.
 *
 * <p>Empty by default, which is the "render everything" state — so a client that
 * has never received the payload, or one whose power was just revoked, behaves like
 * vanilla. Cleared on disconnect alongside the other client mirrors.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientHiddenEntities {

    private static final Set<Integer> HIDDEN = ConcurrentHashMap.newKeySet();

    private ClientHiddenEntities() {}

    /** Replace the hidden set with the server's current verdict. */
    public static void set(Collection<Integer> entityIds) {
        HIDDEN.clear();
        HIDDEN.addAll(entityIds);
    }

    /** True if this entity id must not be rendered for the local player. */
    public static boolean isHidden(int entityId) {
        return !HIDDEN.isEmpty() && HIDDEN.contains(entityId);
    }

    /** True if nothing is currently hidden — lets callers skip work entirely. */
    public static boolean isEmpty() {
        return HIDDEN.isEmpty();
    }

    public static void clear() {
        HIDDEN.clear();
    }
}
