package com.cyberday1.neoorigins.client;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have a flight power active that asks
 * for drawn wings — i.e. whose back should show an elytra — and, optionally, a custom
 * texture id for that elytra. The server does not tell us which power asked, only
 * whether to draw and whether to keep drawing while not gliding.
 *
 * <p>Keyed by entity id — the render layer is handed the rendered player entity
 * directly, so id lookup is the cheapest path. Populated by
 * {@code SyncElytraFlightPayload}, broadcast to every client tracking the affected
 * player (and the player themselves), mirroring {@link ClientInvisibilityArmorState}.
 *
 * <p>A player present in {@link #RENDER} should draw the elytra; the value is the
 * custom texture (or {@code null} for the vanilla elytra texture). Absence means no
 * wings should be drawn by this power. {@link #ALWAYS} is the subset whose
 * {@code render_elytra} is {@code "always"} rather than {@code "flying"}, so the layer
 * draws their (folded) wings outside a glide too; it is always a subset of
 * {@link #RENDER}, and both are written and cleared together.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientElytraFlightState {

    // null value = draw wings with the vanilla elytra texture.
    private static final Map<Integer, ResourceLocation> RENDER = new ConcurrentHashMap<>();
    // Subset of RENDER: wings stay on outside a glide.
    private static final Set<Integer> ALWAYS = ConcurrentHashMap.newKeySet();
    // Sentinel so a null texture can live in a ConcurrentHashMap (which bans null values)
    // while still meaning "render, vanilla texture".
    private static final ResourceLocation VANILLA = ResourceLocation.withDefaultNamespace("textures/entity/elytra.png");

    private ClientElytraFlightState() {}

    /**
     * Record (when {@code render} is true) or clear the elytra-render flags for a
     * player. {@code texture} may be null for the vanilla elytra texture;
     * {@code always} is only meaningful while {@code render} is true.
     */
    public static void set(int entityId, boolean render, boolean always, ResourceLocation texture) {
        if (render) {
            RENDER.put(entityId, texture != null ? texture : VANILLA);
            if (always) {
                ALWAYS.add(entityId);
            } else {
                ALWAYS.remove(entityId);
            }
        } else {
            RENDER.remove(entityId);
            ALWAYS.remove(entityId);
        }
    }

    /** True if this power wants a drawn elytra on the given player. */
    public static boolean shouldRenderElytra(int entityId) {
        return RENDER.containsKey(entityId);
    }

    /**
     * True if the player's wings stay drawn while they are not fall-flying
     * ({@code render_elytra: "always"}). Only meaningful when
     * {@link #shouldRenderElytra(int)} is true.
     */
    public static boolean alwaysRendersElytra(int entityId) {
        return ALWAYS.contains(entityId);
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
        ALWAYS.clear();
    }
}
