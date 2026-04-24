package com.cyberday1.neoorigins.client.renderer;

import com.cyberday1.neoorigins.content.LingeringAreaEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 1.21.1 renderer for {@link LingeringAreaEntity}. Empty — particles are
 * server-emitted; this renderer exists so the engine has something to
 * register against the entity type.
 */
@OnlyIn(Dist.CLIENT)
public class LingeringAreaRenderer extends EntityRenderer<LingeringAreaEntity> {

    private static final ResourceLocation NONE = ResourceLocation.parse("minecraft:textures/misc/white.png");

    public LingeringAreaRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LingeringAreaEntity entity) {
        return NONE;
    }

    @Override
    public void render(LingeringAreaEntity entity, float yaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Particles do the visible work — nothing to submit here.
        super.render(entity, yaw, partialTick, poseStack, buffer, packedLight);
    }
}
