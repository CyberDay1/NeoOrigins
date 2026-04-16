package com.cyberday1.neoorigins.client.render;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Client-side helper that attaches {@link PlayerFurRenderLayer} to both the
 * {@code WIDE} and {@code SLIM} player renderers during entity-renderer setup.
 *
 * <p>The mod main class wires {@link #onAddLayers} onto the mod event bus only
 * on the client distribution, following the same pattern the project uses for
 * {@code NeoOriginsKeybindings}. Using an {@code addListener} hook rather than
 * an {@code @EventBusSubscriber} annotation keeps NeoForge's deprecated-Bus.MOD
 * warnings out of the build and matches the existing registration style.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class NeoOriginsRenderLayerRegistrar {

    private NeoOriginsRenderLayerRegistrar() {}

    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        attach(event, PlayerSkin.Model.WIDE);
        attach(event, PlayerSkin.Model.SLIM);
    }

    private static void attach(EntityRenderersEvent.AddLayers event, PlayerSkin.Model skinModel) {
        PlayerRenderer renderer = event.getSkin(skinModel);
        if (renderer == null) {
            NeoOrigins.LOGGER.warn("NeoOrigins fur: no PlayerRenderer for skin model {}; skipping", skinModel);
            return;
        }
        renderer.addLayer(new PlayerFurRenderLayer(renderer));
        NeoOrigins.LOGGER.debug("NeoOrigins fur: attached PlayerFurRenderLayer to {} player renderer", skinModel);
    }
}
