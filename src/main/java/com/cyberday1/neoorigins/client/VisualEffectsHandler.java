package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.LavaVisionPower;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side handler for visual power effects: overlay, model_color,
 * lava_vision, and shader.
 *
 * <p>Each visual power emits a capability string with encoded parameters
 * (e.g. {@code "overlay:minecraft:textures/misc/pumpkinblur.png:0.5"}).
 * This handler parses those strings from {@link ClientActivePowers} and
 * applies the corresponding visual effects.
 *
 * <p><b>26.1 port note:</b> The rendering pipeline was significantly
 * reworked in MC 26.1. RenderSystem.setShaderColor, depthMask, enableBlend,
 * and GameRenderer.loadEffect/shutdownEffect were all removed. The overlay
 * renderer uses the new GuiGraphicsExtractor.blit pipeline. Model colour
 * tinting and post-processing shaders are stubbed pending a native
 * implementation.
 *
 * <p>Lava vision fog is NOT stubbed. It was, on the reasoning that
 * {@code ViewportEvent.RenderFog} stopped implementing {@code ICancellableEvent}
 * in the rework. That is true but does not matter: the event now carries the
 * live {@link net.minecraft.client.renderer.fog.FogData} and its plane setters
 * write straight through to it, so cancelling is neither possible nor needed.
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class VisualEffectsHandler {

    /** Tracks which shader we applied so we don't clobber other shaders. */
    private static Identifier appliedShader = null;

    private VisualEffectsHandler() {}

    // ---- Overlay ----

    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiEvent.Post event) {
        String data = findCapabilityData("overlay");
        if (data == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;

        // Parse "texture:strength"
        int lastColon = data.lastIndexOf(':');
        String texturePath;
        float strength;
        if (lastColon > 0) {
            texturePath = data.substring(0, lastColon);
            try {
                strength = Float.parseFloat(data.substring(lastColon + 1));
            } catch (NumberFormatException e) {
                texturePath = data;
                strength = 1.0f;
            }
        } else {
            texturePath = data;
            strength = 1.0f;
        }
        if (strength <= 0.0f) return;

        Identifier texture = Identifier.parse(texturePath);
        GuiGraphicsExtractor g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        // TODO: 26.1 removed RenderSystem.setShaderColor — overlay renders at
        // full opacity for now. A proper alpha-blended overlay needs the new
        // render pipeline (BufferBuilder + RenderType).
        g.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0.0f, 0.0f, w, h, w, h);
    }

    // ---- Model Color ----
    // TODO: 26.1 removed RenderSystem.setShaderColor(). Model colour tinting
    // needs a custom RenderType or entity renderer layer. Capability is synced
    // but visual effect is not yet applied on this branch.
    // When it is, blend overlapping tints by averaging each channel rather
    // than taking one: several model_color powers can be active at once (the
    // Caveborn diet tints), and activeCapabilities() is unordered, so picking
    // one makes the colour depend on iteration order.

    // ---- Lava Vision ----

    /**
     * Resolved lava fog planes, combined across every active
     * {@code lava_vision} power. {@code start}/{@code end} are absolute
     * distances in blocks, or {@link Float#NaN} when that plane should keep
     * using {@code multiplier} against vanilla's value.
     */
    record LavaFog(float multiplier, float start, float end) {}

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        // Vanilla decides lava fog from the CAMERA's fluid, not the player's
        // body, and NeoForge hands us that same value. Testing player.isInLava()
        // instead would fire while merely standing ankle-deep in lava with the
        // eyes in air, hijacking ordinary atmospheric fog and repainting the
        // whole world in the sky colour.
        if (event.getType() != FogType.LAVA) return;
        if (Minecraft.getInstance().player == null) return;

        LavaFog fog = resolveLavaFog();
        if (fog == null) return;

        float[] planes = applyPlanes(fog, event.getNearPlaneDistance(), event.getFarPlaneDistance());
        if (planes == null) return;

        event.setNearPlaneDistance(planes[0]);
        event.setFarPlaneDistance(planes[1]);

        // The event is not cancellable here, and does not need to be: its
        // setters write straight through to the live FogData that vanilla is
        // about to upload. But LavaFogEnvironment#setupFog also pins skyEnd and
        // cloudEnd to the environmental end it just chose (1.0 blocks, or 5.0
        // under fire resistance), and the two setters above do not touch them.
        // Left alone they clamp the sky and clouds at the old distance, which
        // draws as a hard band at arm's length in front of a view that is
        // otherwise clear. They have to follow the far plane out.
        FogData data = event.getFogData();
        data.skyEnd = planes[1];
        data.cloudEnd = planes[1];
    }

    /**
     * Applies resolved planes on top of vanilla's own distances, returning
     * {@code {near, far}} or null when the result would not describe a fog
     * volume and vanilla should be left alone.
     *
     * <p>Package-private so {@code LavaVisionFogResolveTest} can exercise it
     * without a live client. This step is where the plane arithmetic actually
     * lives, and it previously disagreed with the documented contract without
     * anything being able to notice.
     */
    static float[] applyPlanes(LavaFog fog, float vanillaNear, float vanillaFar) {
        // Per plane: an absolute value wins for the plane it names, and a plane
        // with none falls back to the multiplier. Vanilla starts lava fog at
        // 0.25 blocks, so the near plane has to move too or the screen stays
        // hazy however far out the far plane goes.
        float near = Float.isNaN(fog.start()) ? vanillaNear * fog.multiplier() : fog.start();
        float far = Float.isNaN(fog.end()) ? vanillaFar * fog.multiplier() : fog.end();

        // A far plane at or behind the near plane means total fog at every
        // distance: a flat, fog-coloured screen.
        if (!(far > near) || !Float.isFinite(near) || !Float.isFinite(far)) return null;
        return new float[] { near, far };
    }

    /**
     * Combines every active {@code lava_vision} capability into one set of
     * planes: the most generous multiplier, the nearest start and the
     * furthest end. Returns null when no usable power is active.
     *
     * <p>Values that cannot produce sane fog, a non-positive or non-finite
     * multiplier or a negative plane, are discarded rather than applied. Pack
     * data reaches this path directly, and a zero multiplier would otherwise
     * collapse both planes onto the origin and blank the screen.
     *
     * <p>Package-private so {@code LavaVisionFogResolveTest} can exercise it
     * without a live client; nothing outside this class should call it.
     */
    static LavaFog resolveLavaFog() {
        boolean found = false;
        float multiplier = Float.NaN;
        float start = Float.NaN;
        float end = Float.NaN;

        for (String cap : ClientActivePowers.activeCapabilities()) {
            if (!cap.startsWith(LavaVisionPower.CAPABILITY_PREFIX)) continue;
            found = true;
            String[] parts = cap.substring(LavaVisionPower.CAPABILITY_PREFIX.length()).split(":", -1);

            float m = parseFloatOrNaN(parts, 0);
            if (Float.isFinite(m) && m > 0.0f) {
                multiplier = Float.isNaN(multiplier) ? m : Math.max(multiplier, m);
            }
            float s = parseFloatOrNaN(parts, 1);
            if (Float.isFinite(s) && s >= 0.0f) {
                start = Float.isNaN(start) ? s : Math.min(start, s);
            }
            float e = parseFloatOrNaN(parts, 2);
            if (Float.isFinite(e) && e > 0.0f) {
                end = Float.isNaN(end) ? e : Math.max(end, e);
            }
        }

        // A bare "lava_vision" capability with no payload still counts.
        if (!found && ClientActivePowers.hasCapability("lava_vision")) {
            return new LavaFog(3.0f, Float.NaN, Float.NaN);
        }
        if (!found) return null;

        if (Float.isNaN(multiplier)) {
            // Every multiplier was unusable. Only proceed if an absolute plane
            // survived; otherwise there is nothing sane left to apply.
            if (Float.isNaN(start) && Float.isNaN(end)) return null;
            multiplier = 3.0f;
        }
        return new LavaFog(multiplier, start, end);
    }

    private static float parseFloatOrNaN(String[] parts, int index) {
        if (index >= parts.length) return Float.NaN;
        try {
            return Float.parseFloat(parts[index]);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    /**
     * Suppresses the first-person burning-screen overlay for {@code lava_vision}
     * holders. The power is granted to fire-immune origins (Draconic, Blazeborn
     * compat) where the full-screen flame animation is pure noise — they take no
     * damage from the fire it warns about.
     */
    @SubscribeEvent
    public static void onRenderBlockScreenEffect(RenderBlockScreenEffectEvent event) {
        if (event.getOverlayType() != RenderBlockScreenEffectEvent.OverlayType.FIRE) return;
        if (findCapabilityData("lava_vision") != null) {
            event.setCanceled(true);
        }
    }

    // ---- Shader ----

    @SubscribeEvent
    public static void onClientTick(PlayerTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // Only run on the local player's tick
        if (event.getEntity() != mc.player) return;

        String data = findCapabilityData("shader");
        if (data != null) {
            Identifier shaderId = Identifier.parse(data);
            if (!shaderId.equals(appliedShader)) {
                // TODO: 26.1 removed GameRenderer.loadEffect() / shutdownEffect().
                // Post-processing shaders need the new PostChain pipeline.
                NeoOrigins.LOGGER.debug("Shader power active but visual application not yet ported to 26.1: {}", shaderId);
                appliedShader = shaderId;
            }
        } else if (appliedShader != null) {
            appliedShader = null;
        }
    }

    // ---- Helpers ----

    /**
     * Finds a capability string starting with {@code prefix:} and returns
     * the data portion after the prefix. Returns null if no match.
     */
    private static String findCapabilityData(String prefix) {
        String needle = prefix + ":";
        for (String cap : ClientActivePowers.activeCapabilities()) {
            if (cap.startsWith(needle)) {
                return cap.substring(needle.length());
            }
        }
        return null;
    }
}
