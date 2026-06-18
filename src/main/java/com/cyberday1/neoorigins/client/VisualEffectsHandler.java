package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
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
 * tinting, lava vision fog, and post-processing shaders are stubbed pending
 * a 26.1-native implementation.
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
        if (mc.player == null || mc.gameRenderer.gameRenderState().guiRenderState.isHudHidden) return;

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

    // ---- Lava Vision ----
    // TODO: 26.1 ViewportEvent.RenderFog is no longer cancellable, and the
    // fog pipeline changed. Lava vision capability is synced but the fog-plane
    // scaling (near + far) is not yet applied on this branch.

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
