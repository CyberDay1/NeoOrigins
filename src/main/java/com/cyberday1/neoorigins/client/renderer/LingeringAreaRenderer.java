package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.api.content.vfx.AbstractVfxRenderState;
import com.cyberday1.neoorigins.content.LingeringAreaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer for {@link LingeringAreaEntity}. Empty — the visual is entirely
 * server-emitted particles. The render state still exists so vanilla's
 * render pipeline is happy and so future client-side enhancements (a faint
 * ground ring outline, a pulse decal) have somewhere to live.
 */
@OnlyIn(Dist.CLIENT)
public class LingeringAreaRenderer
        extends EntityRenderer<LingeringAreaEntity, AbstractVfxRenderState> {

    public LingeringAreaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public AbstractVfxRenderState createRenderState() {
        return new AbstractVfxRenderState();
    }

    @Override
    public void extractRenderState(LingeringAreaEntity entity, AbstractVfxRenderState state, float partialTick) {
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
