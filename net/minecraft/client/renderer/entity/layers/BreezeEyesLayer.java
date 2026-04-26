package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BreezeRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BreezeEyesLayer extends RenderLayer<Breeze, BreezeModel<Breeze>> {
    private static final RenderType BREEZE_EYES = RenderType.breezeEyes(ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_eyes.png"));

    public BreezeEyesLayer(RenderLayerParent<Breeze, BreezeModel<Breeze>> p_312409_) {
        super(p_312409_);
    }

    public void render(
        PoseStack p_311827_,
        MultiBufferSource p_312311_,
        int p_312194_,
        Breeze p_312799_,
        float p_311984_,
        float p_312846_,
        float p_312053_,
        float p_312209_,
        float p_312003_,
        float p_312826_
    ) {
        VertexConsumer vertexconsumer = p_312311_.getBuffer(BREEZE_EYES);
        BreezeModel<Breeze> breezemodel = this.getParentModel();
        BreezeRenderer.enable(breezemodel, breezemodel.head(), breezemodel.eyes())
            .renderToBuffer(p_311827_, vertexconsumer, p_312194_, OverlayTexture.NO_OVERLAY);
    }
}
