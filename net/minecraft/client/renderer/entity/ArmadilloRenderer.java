package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.ArmadilloModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.armadillo.Armadillo;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ArmadilloRenderer extends MobRenderer<Armadillo, ArmadilloModel> {
    private static final ResourceLocation ARMADILLO_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/armadillo.png");

    public ArmadilloRenderer(EntityRendererProvider.Context p_316729_) {
        super(p_316729_, new ArmadilloModel(p_316729_.bakeLayer(ModelLayers.ARMADILLO)), 0.4F);
    }

    public ResourceLocation getTextureLocation(Armadillo p_316224_) {
        return ARMADILLO_LOCATION;
    }
}
