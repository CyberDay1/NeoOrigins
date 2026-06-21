package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.content.TelegraphVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * 26.2 renderer for {@link TelegraphVfxEntity}. Empty — the danger-zone
 * reticle is drawn entirely from server-emitted particles; this renderer only
 * exists so the engine has something to register against the entity type.
 */
public class TelegraphRenderer
        extends EntityRenderer<TelegraphVfxEntity, AbstractVfxRenderState> {

    public TelegraphRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AbstractVfxRenderState createRenderState() {
        return new AbstractVfxRenderState();
    }

    @Override
    public void extractRenderState(TelegraphVfxEntity entity, AbstractVfxRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AbstractVfxRenderState.extract(entity, state);
    }

    @Override
    public void submit(AbstractVfxRenderState state, PoseStack poseStack,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        // Particles do the visible work — nothing to submit here.
        super.submit(state, poseStack, collector, camera);
    }
}
