package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WindChargeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WindChargeRenderer extends EntityRenderer<AbstractWindCharge> {
    private static final float MIN_CAMERA_DISTANCE_SQUARED = Mth.square(3.5F);
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/projectiles/wind_charge.png");
    private final WindChargeModel model;

    public WindChargeRenderer(EntityRendererProvider.Context p_312557_) {
        super(p_312557_);
        this.model = new WindChargeModel(p_312557_.bakeLayer(ModelLayers.WIND_CHARGE));
    }

    public void render(AbstractWindCharge p_326004_, float p_311977_, float p_312685_, PoseStack p_312434_, MultiBufferSource p_312239_, int p_312700_) {
        if (p_326004_.tickCount >= 2 || !(this.entityRenderDispatcher.camera.getEntity().distanceToSqr(p_326004_) < (double)MIN_CAMERA_DISTANCE_SQUARED)) {
            float f = (float)p_326004_.tickCount + p_312685_;
            VertexConsumer vertexconsumer = p_312239_.getBuffer(RenderType.breezeWind(TEXTURE_LOCATION, this.xOffset(f) % 1.0F, 0.0F));
            this.model.setupAnim(p_326004_, 0.0F, 0.0F, f, 0.0F, 0.0F);
            this.model.renderToBuffer(p_312434_, vertexconsumer, p_312700_, OverlayTexture.NO_OVERLAY);
            super.render(p_326004_, p_311977_, p_312685_, p_312434_, p_312239_, p_312700_);
        }
    }

    protected float xOffset(float p_312655_) {
        return p_312655_ * 0.03F;
    }

    public ResourceLocation getTextureLocation(AbstractWindCharge p_326149_) {
        return TEXTURE_LOCATION;
    }
}
