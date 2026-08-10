package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have a flight power active with
 * {@code render_elytra: true} — i.e. whose back should show a drawn elytra while
 * fall-flying — and, optionally, a custom texture id for that elytra. The server
 * does not tell us which power asked, only whether to draw.
 *
 * <p>Keyed by entity id. Populated by {@code SyncElytraFlightPayload}, broadcast to
 * every client tracking the affected player (and the player themselves), mirroring
 * {@link ClientInvisibilityArmorState}.
 *
 * <p>A player present in {@link #RENDER} should draw the elytra; the value is the
 * custom texture (or the vanilla elytra texture when none was set). Absence means no
 * wings should be drawn by this power.
 *
 * <p><b>26.1 render-state adaptation.</b> On 1.21.1 the render layer subclassed
 * vanilla {@code ElytraLayer} and was handed the rendered {@code LivingEntity}
 * directly, looking the flag/texture up by id. On 26.1 vanilla's {@code ElytraLayer}
 * is gone — elytra render moved into {@code WingsLayer}, which renders from a
 * {@code HumanoidRenderState} (never the entity) and only draws when a real elytra is
 * equipped in the chest slot. So this power's own render layer
 * ({@code NeoOriginsElytraLayer}) reads the state through NeoForge render data: a
 * {@code RegisterRenderStateModifiersEvent} modifier on the avatar renderer reads
 * {@link #shouldRenderElytra(int)} / {@link #textureFor(int)} per entity and stamps
 * {@link #RENDER_ELYTRA_KEY} + {@link #ELYTRA_TEXTURE_KEY} onto the state; the layer
 * reads them back via {@code state.getRenderData(...)}. This map remains the
 * server-synced source of truth; the render-data keys are just the per-frame
 * hand-off into the entity-less render layer.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientElytraFlightState {

    // null value = draw wings with the vanilla elytra texture.
    private static final Map<Integer, Identifier> RENDER = new ConcurrentHashMap<>();
    // Sentinel so a null texture can live in a ConcurrentHashMap (which bans null values)
    // while still meaning "render, vanilla texture". 26.1: the vanilla entity elytra
    // texture moved under textures/entity/equipment/wings/ (was textures/entity/elytra.png
    // on 1.21.1).
    private static final Identifier VANILLA =
        Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");

    /**
     * Render-data flag: true when this player's back should show a power-granted
     * elytra while fall-flying. Stamped by the avatar render-state modifier, read by
     * {@code NeoOriginsElytraLayer}.
     */
    public static final ContextKey<Boolean> RENDER_ELYTRA_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "render_elytra"));

    /**
     * Render-data carrying the texture id for the power-granted elytra (the vanilla
     * texture when none was set). Only meaningful when {@link #RENDER_ELYTRA_KEY} is set.
     */
    public static final ContextKey<Identifier> ELYTRA_TEXTURE_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "elytra_texture"));

    private ClientElytraFlightState() {}

    /**
     * Record (when {@code render} is true) or clear the elytra-render flag for a
     * player. {@code texture} may be null for the vanilla elytra texture.
     */
    public static void set(int entityId, boolean render, Identifier texture) {
        if (render) {
            RENDER.put(entityId, texture != null ? texture : VANILLA);
        } else {
            RENDER.remove(entityId);
        }
    }

    /** True if this power wants a drawn elytra on the given player. */
    public static boolean shouldRenderElytra(int entityId) {
        return RENDER.containsKey(entityId);
    }

    /**
     * Texture to draw for the given player's power-granted elytra, or the vanilla
     * elytra texture when no custom one was set. Only meaningful when
     * {@link #shouldRenderElytra(int)} is true.
     */
    public static Identifier textureFor(int entityId) {
        return RENDER.getOrDefault(entityId, VANILLA);
    }

    public static void clear() {
        RENDER.clear();
    }
}
