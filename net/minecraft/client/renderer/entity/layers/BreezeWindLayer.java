package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.BreezeRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BreezeWindLayer extends RenderLayer<Breeze, BreezeModel<Breeze>> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze_wind.png");
    private final BreezeModel<Breeze> model;

    public BreezeWindLayer(EntityRendererProvider.Context p_350777_, RenderLayerParent<Breeze, BreezeModel<Breeze>> p_312625_) {
        super(p_312625_);
        this.model = new BreezeModel<>(p_350777_.bakeLayer(ModelLayers.BREEZE_WIND));
    }

    public void render(
        PoseStack p_312822_,
        MultiBufferSource p_312869_,
        int p_311783_,
        Breeze p_312046_,
        float p_312170_,
        float p_311773_,
        float p_312428_,
        float p_312287_,
        float p_312118_,
        float p_312531_
    ) {
        float f = (float)p_312046_.tickCount + p_312428_;
        VertexConsumer vertexconsumer = p_312869_.getBuffer(RenderType.breezeWind(TEXTURE_LOCATION, this.xOffset(f) % 1.0F, 0.0F));
        this.model.setupAnim(p_312046_, p_312170_, p_311773_, p_312287_, p_312118_, p_312531_);
        BreezeRenderer.enable(this.model, this.model.wind()).renderToBuffer(p_312822_, vertexconsumer, p_311783_, OverlayTexture.NO_OVERLAY);
    }

    private float xOffset(float p_312086_) {
        return p_312086_ * 0.02F;
    }
}
