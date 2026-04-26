package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Wolf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WolfCollarLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {
    private static final ResourceLocation WOLF_COLLAR_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_collar.png");

    public WolfCollarLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> p_117707_) {
        super(p_117707_);
    }

    public void render(
        PoseStack p_117720_,
        MultiBufferSource p_117721_,
        int p_117722_,
        Wolf p_117723_,
        float p_117724_,
        float p_117725_,
        float p_117726_,
        float p_117727_,
        float p_117728_,
        float p_117729_
    ) {
        if (p_117723_.isTame() && !p_117723_.isInvisible()) {
            int i = p_117723_.getCollarColor().getTextureDiffuseColor();
            VertexConsumer vertexconsumer = p_117721_.getBuffer(RenderType.entityCutoutNoCull(WOLF_COLLAR_LOCATION));
            this.getParentModel().renderToBuffer(p_117720_, vertexconsumer, p_117722_, OverlayTexture.NO_OVERLAY, i);
        }
    }
}
