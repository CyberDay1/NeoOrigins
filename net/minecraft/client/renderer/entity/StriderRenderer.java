package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.StriderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Strider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class StriderRenderer extends MobRenderer<Strider, StriderModel<Strider>> {
    private static final ResourceLocation STRIDER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/strider/strider.png");
    private static final ResourceLocation COLD_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_cold.png");
    private static final float SHADOW_RADIUS = 0.5F;

    public StriderRenderer(EntityRendererProvider.Context p_174411_) {
        super(p_174411_, new StriderModel<>(p_174411_.bakeLayer(ModelLayers.STRIDER)), 0.5F);
        this.addLayer(
            new SaddleLayer<>(
                this,
                new StriderModel<>(p_174411_.bakeLayer(ModelLayers.STRIDER_SADDLE)),
                ResourceLocation.withDefaultNamespace("textures/entity/strider/strider_saddle.png")
            )
        );
    }

    public ResourceLocation getTextureLocation(Strider p_116064_) {
        return p_116064_.isSuffocating() ? COLD_LOCATION : STRIDER_LOCATION;
    }

    protected float getShadowRadius(Strider p_316706_) {
        float f = super.getShadowRadius(p_316706_);
        return p_316706_.isBaby() ? f * 0.5F : f;
    }

    protected void scale(Strider p_320685_, PoseStack p_319790_, float p_319907_) {
        float f = p_320685_.getAgeScale();
        p_319790_.scale(f, f, f);
    }

    protected boolean isShaking(Strider p_116070_) {
        return super.isShaking(p_116070_) || p_116070_.isSuffocating();
    }
}
