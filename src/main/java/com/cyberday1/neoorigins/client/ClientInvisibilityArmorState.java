package com.cyberday1.neoorigins.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have the
 * {@code neoorigins:invisibility} power active with {@code render_armor: false}
 * (i.e. their worn armor should be hidden for true invisibility).
 *
 * <p>Keyed by entity id — the armor-layer mixin is handed the rendered player
 * entity directly, so id lookup is the cheapest path. Populated by
 * {@code SyncInvisibilityArmorPayload}, broadcast to every client tracking the
 * affected player (and the player themselves), mirroring {@link ClientMorphState}.
 * The local-player entry also drives first-person armor suppression for free.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientInvisibilityArmorState {

    private static final Set<Integer> HIDE_ARMOR = ConcurrentHashMap.newKeySet();

    private ClientInvisibilityArmorState() {}

    /** Record (when {@code hide} is true) or clear the armor-hide flag for a player. */
    public static void set(int entityId, boolean hide) {
        if (hide) {
            HIDE_ARMOR.add(entityId);
        } else {
            HIDE_ARMOR.remove(entityId);
        }
    }

    /** True if the given player entity id should have its worn armor hidden. */
    public static boolean shouldHideArmor(int entityId) {
        return HIDE_ARMOR.contains(entityId);
    }

    public static void clear() {
        HIDE_ARMOR.clear();
    }
}
