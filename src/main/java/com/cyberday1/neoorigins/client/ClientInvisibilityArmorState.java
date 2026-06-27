package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have the
 * {@code neoorigins:invisibility} power active with {@code render_armor: false}
 * (i.e. their worn armor should be hidden for true invisibility).
 *
 * <p>Keyed by entity id. Populated by {@code SyncInvisibilityArmorPayload},
 * broadcast to every client tracking the affected player (and the player
 * themselves), mirroring {@link ClientMorphState}. The local-player entry also
 * drives first-person armor suppression for free.
 *
 * <p><b>26.1 render-state adaptation.</b> On 1.21.1 the armor-layer mixin was
 * handed the rendered {@code LivingEntity} directly and looked the flag up by id.
 * On 26.1 the render pipeline is render-state-based: {@code HumanoidArmorLayer}
 * renders from a {@code HumanoidRenderState} and never sees the entity. So the
 * flag is carried onto the render state through NeoForge's render-data extension
 * ({@link #HIDE_ARMOR_KEY}): a {@code RegisterRenderStateModifiersEvent} modifier
 * on {@code PlayerRenderer} reads {@link #shouldHideArmor(int)} per entity and
 * stamps the key onto the state, and the armor-layer mixin reads it back via
 * {@code state.getRenderData(HIDE_ARMOR_KEY)}. This set remains the
 * server-synced source of truth; the render-data flag is just the per-frame
 * hand-off into the entity-less render layer.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientInvisibilityArmorState {

    private static final Set<Integer> HIDE_ARMOR = ConcurrentHashMap.newKeySet();

    /**
     * Render-data key flagging a render state whose worn armor should be hidden
     * (player is invisible via {@code neoorigins:invisibility} with
     * {@code render_armor:false}). Stamped by the {@code PlayerRenderer} render-state
     * modifier, read by {@code HumanoidArmorLayerMixin}.
     */
    public static final ContextKey<Boolean> HIDE_ARMOR_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "hide_armor"));

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
