package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.api.content.vfx.GeoJsonModel;
import com.cyberday1.neoorigins.content.TornadoVfxEntity;
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

/**
 * Renderer for {@link TornadoVfxEntity} — loads the funnel mesh once at
 * classload, spins it on the Y axis, and scales it non-uniformly so the
 * visual funnel matches the widened pull field: the radius widens with
 * {@link TornadoVfxEntity#WIDTH_MULT} and the column rises with
 * {@link TornadoVfxEntity#HEIGHT_MULT}, kept in lockstep with the
 * server-side field on the entity. A column of spiral particles backs it.
 */
public class TornadoRenderer
        extends EntityRenderer<TornadoVfxEntity, AbstractVfxRenderState> {

    private static final GeoJsonModel MODEL =
        GeoJsonModel.load("/assets/neoorigins/geo/tornado.geo.json");

    private static final Identifier TEXTURE =
        Identifier.fromNamespaceAndPath("neoorigins", "textures/entity/tornado.png");
    private static final RenderType RENDER_TYPE = RenderTypes.entityTranslucentEmissive(TEXTURE);

    /** Degrees per tick, multiplied by {@link TornadoVfxEntity#SPIN_MULT}. */
    private static final float SPIN_SPEED = 6.0f;
    /** Model radius maps to this fraction of the design range (before mults). */
    private static final float VISUAL_FRACTION = 0.45f;

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AbstractVfxRenderState createRenderState() {
        return new AbstractVfxRenderState();
    }

    @Override
    public void extractRenderState(TornadoVfxEntity entity, AbstractVfxRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override
    public void submit(AbstractVfxRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        float modelRadius = Math.max(0.001f, MODEL.getRadius());
        float base = state.range * VISUAL_FRACTION / modelRadius;
        float scaleXZ = base * TornadoVfxEntity.WIDTH_MULT;
        float scaleY = base * TornadoVfxEntity.HEIGHT_MULT;
        float time = state.lifetime + state.partialTick;

        poseStack.pushPose();
        poseStack.scale(scaleXZ, scaleY, scaleXZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * SPIN_SPEED * TornadoVfxEntity.SPIN_MULT));
        collector.submitCustomGeometry(poseStack, RENDER_TYPE, (pose, consumer) ->
            MODEL.render(pose, consumer, 0xF000F0, OverlayTexture.NO_OVERLAY));
        poseStack.popPose();

        super.submit(state, poseStack, collector, camera);
    }
}
