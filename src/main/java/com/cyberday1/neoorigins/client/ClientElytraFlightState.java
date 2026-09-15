package com.cyberday1.neoorigins.client;

import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side mirror of which players currently have a flight power active that asks
 * for drawn wings — i.e. whose back should show an elytra — and, optionally, a custom
 * texture id for that elytra. The server does not tell us which power asked, only
 * whether to draw and whether to keep drawing while not gliding.
 *
 * <p>Keyed by entity id. Populated by {@code SyncElytraFlightPayload}, broadcast to
 * every client tracking the affected player (and the player themselves), mirroring
 * {@link ClientInvisibilityArmorState}.
 *
 * <p>A player present in {@link #RENDER} should draw the elytra; the value is the
 * custom texture (or the vanilla elytra texture when none was set). Absence means no
 * wings should be drawn by this power. {@link #ALWAYS} is the subset whose
 * {@code render_elytra} is {@code "always"} rather than {@code "flying"}, so the layer
 * draws their (folded) wings outside a glide too; it is always a subset of
 * {@link #RENDER}, and both are written and cleared together.
 *
 * <p><b>26.1 render-state adaptation.</b> On 1.21.1 the render layer subclassed
 * vanilla {@code ElytraLayer} and was handed the rendered {@code LivingEntity}
 * directly, looking the flag/texture up by id. On 26.1 vanilla's {@code ElytraLayer}
 * is gone — elytra render moved into {@code WingsLayer}, which renders from a
 * {@code HumanoidRenderState} (never the entity) and only draws when a real elytra is
 * equipped in the chest slot. So this power's own render layer
 * ({@code NeoOriginsElytraLayer}) reads the state through NeoForge render data: a
 * {@code RegisterRenderStateModifiersEvent} modifier on the avatar renderer reads
 * {@link #shouldRenderElytra(int)} / {@link #alwaysRendersElytra(int)} /
 * {@link #textureFor(int)} per entity and stamps {@link #RENDER_ELYTRA_KEY} +
 * {@link #RENDER_ELYTRA_ALWAYS_KEY} + {@link #ELYTRA_TEXTURE_KEY} onto the state; the
 * layer reads them back via {@code state.getRenderData(...)}. This map remains the
 * server-synced source of truth; the render-data keys are just the per-frame
 * hand-off into the entity-less render layer.
 *
 * <p>Not valid on a dedicated server — only populated on the logical client.
 */
public final class ClientElytraFlightState {

    // null value = draw wings with the vanilla elytra texture.
    private static final Map<Integer, Identifier> RENDER = new ConcurrentHashMap<>();
    // Subset of RENDER: wings stay on outside a glide.
    private static final Set<Integer> ALWAYS = ConcurrentHashMap.newKeySet();
    // Sentinel so a null texture can live in a ConcurrentHashMap (which bans null values)
    // while still meaning "render, vanilla texture". 26.1: the vanilla entity elytra
    // texture moved under textures/entity/equipment/wings/ (was textures/entity/elytra.png
    // on 1.21.1).
    private static final Identifier VANILLA =
        Identifier.withDefaultNamespace("textures/entity/equipment/wings/elytra.png");

    /**
     * Render-data flag: true when this player's back should show a power-granted
     * elytra at all. Stamped by the avatar render-state modifier, read by
     * {@code NeoOriginsElytraLayer}.
     */
    public static final ContextKey<Boolean> RENDER_ELYTRA_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "render_elytra"));

    /**
     * Render-data refinement of {@link #RENDER_ELYTRA_KEY}: true when the wings stay
     * on outside a glide ({@code render_elytra: "always"}). A SEPARATE key rather than
     * a widened {@link #RENDER_ELYTRA_KEY}, mirroring the capability encoding on the
     * server side — the old flag keeps its old type and meaning, so any reader written
     * against it (here or downstream) keeps working untouched.
     */
    public static final ContextKey<Boolean> RENDER_ELYTRA_ALWAYS_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "render_elytra_always"));

    /**
     * Render-data carrying the texture id for the power-granted elytra (the vanilla
     * texture when none was set). Only meaningful when {@link #RENDER_ELYTRA_KEY} is set.
     */
    public static final ContextKey<Identifier> ELYTRA_TEXTURE_KEY =
        new ContextKey<>(Identifier.fromNamespaceAndPath("neoorigins", "elytra_texture"));

    private ClientElytraFlightState() {}

    /**
     * Record (when {@code render} is true) or clear the elytra-render flags for a
     * player. {@code texture} may be null for the vanilla elytra texture;
     * {@code always} is only meaningful while {@code render} is true.
     */
    public static void set(int entityId, boolean render, boolean always, Identifier texture) {
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
    public static Identifier textureFor(int entityId) {
        return RENDER.getOrDefault(entityId, VANILLA);
    }

    public static void clear() {
        RENDER.clear();
        ALWAYS.clear();
    }
}
