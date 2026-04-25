package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel;
import com.cyberday1.neoorigins.content.BlackHoleVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 1.21.1 renderer for {@link BlackHoleVfxEntity}. Uses {@link GeoJsonModel}
 * identically to the 26.1 variant; only the render-flow differs.
 */
@OnlyIn(Dist.CLIENT)
public class BlackHoleRenderer extends EntityRenderer<BlackHoleVfxEntity> {

    private static final GeoJsonModel MODEL =
        GeoJsonModel.load("/assets/neoorigins/geo/black_hole.geo.json");

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "textures/entity/black_hole.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    private static final float SPIN_SPEED = 9.0f;
    private static final float VISUAL_FRACTION = 0.195f;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(BlackHoleVfxEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(BlackHoleVfxEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float modelRadius = Math.max(0.001f, MODEL.getRadius());
        float scale = entity.getRange() * VISUAL_FRACTION / modelRadius;
        float time = entity.tickCount + partialTick;

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_SPEED));
        MODEL.render(poseStack, buffer.getBuffer(RENDER_TYPE), 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
