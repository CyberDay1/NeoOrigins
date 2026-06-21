package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.content.TelegraphVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 renderer for {@link TelegraphVfxEntity}. Empty — the danger-zone
 * reticle is drawn entirely from server-emitted particles; this renderer only
 * exists so the engine has something to register against the entity type.
 */
public class TelegraphRenderer extends EntityRenderer<TelegraphVfxEntity> {

    private static final ResourceLocation NONE = ResourceLocation.parse("minecraft:textures/misc/white.png");

    public TelegraphRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TelegraphVfxEntity entity) {
        return NONE;
    }

    @Override
    public void render(TelegraphVfxEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Particles do the visible work — nothing to submit here.
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
