package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.FoxModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.FoxHeldItemLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Fox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class FoxRenderer extends MobRenderer<Fox, FoxModel<Fox>> {
    private static final ResourceLocation RED_FOX_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fox/fox.png");
    private static final ResourceLocation RED_FOX_SLEEP_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fox/fox_sleep.png");
    private static final ResourceLocation SNOW_FOX_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fox/snow_fox.png");
    private static final ResourceLocation SNOW_FOX_SLEEP_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fox/snow_fox_sleep.png");

    public FoxRenderer(EntityRendererProvider.Context p_174127_) {
        super(p_174127_, new FoxModel<>(p_174127_.bakeLayer(ModelLayers.FOX)), 0.4F);
        this.addLayer(new FoxHeldItemLayer(this, p_174127_.getItemInHandRenderer()));
    }

    protected void setupRotations(Fox p_320936_, PoseStack p_114731_, float p_114732_, float p_114733_, float p_114734_, float p_320025_) {
        super.setupRotations(p_320936_, p_114731_, p_114732_, p_114733_, p_114734_, p_320025_);
        if (p_320936_.isPouncing() || p_320936_.isFaceplanted()) {
            float f = -Mth.lerp(p_114734_, p_320936_.xRotO, p_320936_.getXRot());
            p_114731_.mulPose(Axis.XP.rotationDegrees(f));
        }
    }

    public ResourceLocation getTextureLocation(Fox p_114736_) {
        if (p_114736_.getVariant() == Fox.Type.RED) {
            return p_114736_.isSleeping() ? RED_FOX_SLEEP_TEXTURE : RED_FOX_TEXTURE;
        } else {
            return p_114736_.isSleeping() ? SNOW_FOX_SLEEP_TEXTURE : SNOW_FOX_TEXTURE;
        }
    }
}
