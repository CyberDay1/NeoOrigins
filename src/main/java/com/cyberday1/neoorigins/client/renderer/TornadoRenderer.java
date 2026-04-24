package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.content.TornadoVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer for {@link TornadoVfxEntity}. Empty — the visual is entirely
 * server-emitted spiral particles. The render state is kept so vanilla's
 * render pipeline is happy and so future client-side enhancements (a
 * funnel silhouette, dust-cloud overlay) have somewhere to live.
 */
@OnlyIn(Dist.CLIENT)
public class TornadoRenderer
        extends EntityRenderer<TornadoVfxEntity, AbstractVfxRenderState> {

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
        super.submit(state, poseStack, collector, camera);
    }
}
