package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.content.TornadoVfxEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 1.21.1 renderer for {@link TornadoVfxEntity}. Empty — spiral particles
 * emitted server-side carry the visual. See 26.1 twin for rationale.
 */
@OnlyIn(Dist.CLIENT)
public class TornadoRenderer extends EntityRenderer<TornadoVfxEntity> {

    private static final ResourceLocation NONE = ResourceLocation.parse("minecraft:textures/misc/white.png");

    public TornadoRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(TornadoVfxEntity entity) {
        return NONE;
    }

    @Override
    public void render(TornadoVfxEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
