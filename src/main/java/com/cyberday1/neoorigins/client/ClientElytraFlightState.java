package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have a flight power active with
 * {@code render_elytra: true} — i.e. whose back should show a drawn elytra while
 * fall-flying — and, optionally, a custom texture id for that elytra. The server
 * does not tell us which power asked, only whether to draw.
 *
 * <p>Keyed by entity id — the render layer is handed the rendered player entity
 * directly, so id lookup is the cheapest path. Populated by
 * {@code SyncElytraFlightPayload}, broadcast to every client tracking the affected
 * player (and the player themselves), mirroring {@link ClientInvisibilityArmorState}.
 *
 * <p>A player present in {@link #RENDER} should draw the elytra; the value is the
 * custom texture (or {@code null} for the vanilla elytra texture). Absence means no
 * wings should be drawn by this power.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientElytraFlightState {

    // null value = draw wings with the vanilla elytra texture.
    private static final Map<Integer, ResourceLocation> RENDER = new ConcurrentHashMap<>();
    // Sentinel so a null texture can live in a ConcurrentHashMap (which bans null values)
    // while still meaning "render, vanilla texture".
    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    private ClientElytraFlightState() {}

    /**
     * Record (when {@code render} is true) or clear the elytra-render flag for a
     * player. {@code texture} may be null for the vanilla elytra texture.
     */
    public static void set(int entityId, boolean render, ResourceLocation texture) {
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
    public static ResourceLocation textureFor(int entityId) {
        return RENDER.getOrDefault(entityId, VANILLA);
    }

    public static void clear() {
        RENDER.clear();
    }
}
