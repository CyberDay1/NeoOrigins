package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.NeoOrigins;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
        String data = findCapabilityData("model_color");
        if (data == null) return;

        String[] parts = data.split(":");
        if (parts.length < 3) return;
        try {
            float r = Float.parseFloat(parts[0]);
            float g = Float.parseFloat(parts[1]);
            float b = Float.parseFloat(parts[2]);
            float a = parts.length >= 4 ? Float.parseFloat(parts[3]) : 1.0f;
            if (a < 1.0f) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
            RenderSystem.setShaderColor(r, g, b, a);
            modelColorActive = true;
        } catch (NumberFormatException ignored) {}
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!modelColorActive) return;
        modelColorActive = false;

        // Flush any batched geometry so it is drawn with the tinted shader color
        // before we reset it. Without this, when nametag rendering is skipped the
        // buffer may not have been flushed yet and the tint would be lost.
        MultiBufferSource buf = event.getMultiBufferSource();
        if (buf instanceof MultiBufferSource.BufferSource bufferSource) {
            bufferSource.endBatch();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.disableBlend();
    }

    // ---- Lava Vision ----

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isInLava()) return;

        String data = findCapabilityData("lava_vision");
        float multiplier;
        if (data != null) {
            try {
                multiplier = Float.parseFloat(data);
            } catch (NumberFormatException e) {
                multiplier = 3.0f;
            }
        } else if (ClientActivePowers.hasCapability("lava_vision")) {
            multiplier = 3.0f;
        } else {
            return;
        }

        event.setFarPlaneDistance(event.getFarPlaneDistance() * multiplier);
        event.setCanceled(true);
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
