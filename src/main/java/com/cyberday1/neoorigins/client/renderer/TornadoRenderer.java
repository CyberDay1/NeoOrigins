package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel;
import com.cyberday1.neoorigins.content.TornadoVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.21.1 renderer for {@link TornadoVfxEntity}. Draws a 3D funnel using the
 * shared {@link GeoJsonModel} loader (same pattern as {@link BlackHoleRenderer}),
 * spinning the whole mesh about Y; the server-side spiral particles in
 * {@code TornadoVfxEntity#onVfxTick} layer extra motion on top. See 26.1 twin.
 */
public class TornadoRenderer extends EntityRenderer<TornadoVfxEntity> {

    private static final GeoJsonModel MODEL =
        GeoJsonModel.load("/assets/neoorigins/geo/tornado.geo.json");

    private static final ResourceLocation TEXTURE =
        ResourceLocation.fromNamespaceAndPath("neoorigins", "textures/entity/tornado.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucentEmissive(TEXTURE);

    /** Model radius maps to this fraction of the AoE range. */
    private static final float VISUAL_FRACTION = 0.45f;
    /** Degrees of whole-funnel spin per tick. */
    private static final float SPIN_SPEED = 6.0f;

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TornadoVfxEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(TornadoVfxEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float modelRadius = Math.max(0.001f, MODEL.getRadius());
        float base = entity.getRange() * VISUAL_FRACTION / modelRadius;
        // Non-uniform: wider in XZ, much taller in Y, matching the entity's
        // widened pull field and raised column (shared multipliers).
        float scaleXZ = base * TornadoVfxEntity.WIDTH_MULT;
        float scaleY  = base * TornadoVfxEntity.HEIGHT_MULT;
        float time = entity.tickCount + partialTick;

        poseStack.pushPose();
        poseStack.scale(scaleXZ, scaleY, scaleXZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_SPEED * TornadoVfxEntity.SPIN_MULT));
        MODEL.render(poseStack, buffer.getBuffer(RENDER_TYPE), 0xF000F0, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();

        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
