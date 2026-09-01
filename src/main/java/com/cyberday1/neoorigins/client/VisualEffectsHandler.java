package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.power.builtin.LavaVisionPower;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
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
 */
@EventBusSubscriber(value = Dist.CLIENT, modid = NeoOrigins.MOD_ID)
public final class VisualEffectsHandler {

    /** Tracks which shader we applied so we don't clobber other shaders. */
    private static ResourceLocation appliedShader = null;

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

        ResourceLocation texture = ResourceLocation.parse(texturePath);
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        g.setColor(1.0f, 1.0f, 1.0f, strength);
        g.blit(texture, 0, 0, -90, 0.0f, 0.0f, w, h, w, h);
        g.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    // ---- Model Color ----

    /** True while we have an active model_color tint that needs cleanup in Post. */
    private static boolean modelColorActive = false;

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        // Several model_color powers can be active at once (the Caveborn diet
        // tints, for example). Blend them by averaging each channel, so the
        // result doesn't depend on the iteration order of an unordered set.
        float r = 0.0f, g = 0.0f, b = 0.0f, a = 0.0f;
        int n = 0;
        for (String cap : ClientActivePowers.activeCapabilities()) {
            if (!cap.startsWith("model_color:")) continue;
            String[] parts = cap.substring("model_color:".length()).split(":");
            if (parts.length < 3) continue;
            try {
                r += Float.parseFloat(parts[0]);
                g += Float.parseFloat(parts[1]);
                b += Float.parseFloat(parts[2]);
                a += parts.length >= 4 ? Float.parseFloat(parts[3]) : 1.0f;
                n++;
            } catch (NumberFormatException ignored) {}
        }
        if (n == 0) return;
        r /= n; g /= n; b /= n; a /= n;

        // Flush any previously batched geometry before changing the shader
        // color, so prior renders aren't accidentally tinted.
        MultiBufferSource buf = event.getMultiBufferSource();
        if (buf instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        if (a < 1.0f) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.setShaderColor(r, g, b, a);
        modelColorActive = true;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!modelColorActive) return;
        modelColorActive = false;

        // Flush all batched entity geometry so it is drawn with the tinted
        // shader color before we reset it. Without this, when nametag rendering
        // is skipped (hidden nametag) the buffer is never flushed mid-entity,
        // so the tinted geometry would be drawn after the color reset — losing
        // the tint entirely.
        MultiBufferSource buf = event.getMultiBufferSource();
        if (buf instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

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
        // body (FogRenderer#setupFog), and NeoForge hands us that same value.
        // Testing player.isInLava() instead would fire while merely standing
        // ankle-deep in lava with the eyes in air, hijacking ordinary
        // atmospheric fog and repainting the whole world in the sky colour.
        if (event.getType() != FogType.LAVA) return;
        if (Minecraft.getInstance().player == null) return;

        LavaFog fog = resolveLavaFog();
        if (fog == null) return;

        float[] planes = applyPlanes(fog, event.getNearPlaneDistance(), event.getFarPlaneDistance());
        if (planes == null) return;

        event.setNearPlaneDistance(planes[0]);
        event.setFarPlaneDistance(planes[1]);
        event.setCanceled(true);
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
     * <p>Values that cannot produce sane fog — a non-positive or non-finite
     * multiplier, a negative plane — are discarded rather than applied. Pack
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
            ResourceLocation shaderId = ResourceLocation.parse(data);
            if (!shaderId.equals(appliedShader)) {
                try {
                    mc.gameRenderer.loadEffect(shaderId);
                    appliedShader = shaderId;
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("Failed to load shader '{}': {}", shaderId, e.getMessage());
                    appliedShader = shaderId; // don't retry every tick
                }
            }
        } else if (appliedShader != null) {
            mc.gameRenderer.shutdownEffect();
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
