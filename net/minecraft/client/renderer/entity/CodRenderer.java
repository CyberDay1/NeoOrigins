package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.CodModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Cod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CodRenderer extends MobRenderer<Cod, CodModel<Cod>> {
    private static final ResourceLocation COD_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fish/cod.png");

    public CodRenderer(EntityRendererProvider.Context p_173954_) {
        super(p_173954_, new CodModel<>(p_173954_.bakeLayer(ModelLayers.COD)), 0.3F);
    }

    public ResourceLocation getTextureLocation(Cod p_114015_) {
        return COD_LOCATION;
    }

    protected void setupRotations(Cod p_320712_, PoseStack p_114010_, float p_114011_, float p_114012_, float p_114013_, float p_320770_) {
        super.setupRotations(p_320712_, p_114010_, p_114011_, p_114012_, p_114013_, p_320770_);
        float f = 4.3F * Mth.sin(0.6F * p_114011_);
        p_114010_.mulPose(Axis.YP.rotationDegrees(f));
        if (!p_320712_.isInWater()) {
            p_114010_.translate(0.1F, 0.1F, -0.1F);
            p_114010_.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }
    }
}
