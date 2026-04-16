package com.cyberday1.neoorigins.client.render;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import software.bernie.geckolib.model.GeoModel;

/**
 * Dynamic {@link GeoModel} that the fur render layer reconfigures per-render by
 * calling {@link #setContext(ResourceLocation, ResourceLocation, ResourceLocation)}
 * just before invoking the renderer.
 *
 * <p>One model instance + one renderer are shared across all origins — the fur
 * cosmetic is per-origin, not per-player, so a single shared pair keeps the
 * GeckoLib baked-model cache warm while still serving any origin's assets.</p>
 *
 * <p>If the context has not been set (first-frame race, no origin selected),
 * the getters fall back to the feline placeholder paths, which is safe because
 * the render layer guards the whole render path on fur presence before calling
 * through.</p>
 */
@OnlyIn(Dist.CLIENT)
public class FurGeoModel extends GeoModel<FurAnimatable> {

    private static final ResourceLocation FALLBACK_MODEL =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "geo/fur/feline.geo.json");
    private static final ResourceLocation FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "textures/fur/feline.png");
    private static final ResourceLocation FALLBACK_ANIMATION =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "animations/fur/feline.animation.json");

    private ResourceLocation currentModel = FALLBACK_MODEL;
    private ResourceLocation currentTexture = FALLBACK_TEXTURE;
    private ResourceLocation currentAnimation = FALLBACK_ANIMATION;

    /**
     * Configure the next render pass. The render layer must call this immediately
     * before {@code GeoObjectRenderer#render}, on the client render thread.
     */
    public void setContext(ResourceLocation model, ResourceLocation texture, ResourceLocation animation) {
        this.currentModel = model;
        this.currentTexture = texture;
        this.currentAnimation = animation;
    }

    @Override
    public ResourceLocation getModelResource(FurAnimatable animatable) {
        return this.currentModel;
    }

    @Override
    public ResourceLocation getTextureResource(FurAnimatable animatable) {
        return this.currentTexture;
    }

    @Override
    public ResourceLocation getAnimationResource(FurAnimatable animatable) {
        return this.currentAnimation;
    }
}
