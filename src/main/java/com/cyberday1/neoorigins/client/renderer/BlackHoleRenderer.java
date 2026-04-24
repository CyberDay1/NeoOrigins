package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel;
import com.cyberday1.neoorigins.content.BlackHoleVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer for {@link BlackHoleVfxEntity} — uses {@link GeoJsonModel} to
 * load the black-hole mesh once at classload, continuously Y-axis spins
 * it, and scales visually with the entity's configured range.
 */
@OnlyIn(Dist.CLIENT)
public class BlackHoleRenderer extends EntityRenderer<BlackHoleVfxEntity, AbstractVfxRenderState> {

    private static final GeoJsonModel MODEL =
        GeoJsonModel.load("/assets/neoorigins/geo/black_hole.geo.json");

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("neoorigins", "textures/entity/black_hole.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    /** Degrees per tick = 9°, ≈ one full rotation every 2 seconds. */
    private static final float SPIN_SPEED = 9.0f;
    /** Model radius maps to this fraction of the AoE range. */
    private static final float VISUAL_FRACTION = 0.195f;

    public BlackHoleRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AbstractVfxRenderState createRenderState() {
        return new AbstractVfxRenderState();
    }

    @Override
    public void extractRenderState(BlackHoleVfxEntity entity, AbstractVfxRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override
    public void submit(AbstractVfxRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        float modelRadius = Math.max(0.001f, MODEL.getRadius());
        float scale = state.range * VISUAL_FRACTION / modelRadius;
        float time = state.lifetime + state.partialTick;

        poseStack.pushPose();
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_SPEED));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) ->
            MODEL.render(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY));
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }
}
