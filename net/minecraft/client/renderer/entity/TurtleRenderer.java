package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.TurtleModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Turtle;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TurtleRenderer extends MobRenderer<Turtle, TurtleModel<Turtle>> {
    private static final ResourceLocation TURTLE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/turtle/big_sea_turtle.png");

    public TurtleRenderer(EntityRendererProvider.Context p_174430_) {
        super(p_174430_, new TurtleModel<>(p_174430_.bakeLayer(ModelLayers.TURTLE)), 0.7F);
    }

    protected float getShadowRadius(Turtle p_316236_) {
        float f = super.getShadowRadius(p_316236_);
        return p_316236_.isBaby() ? f * 0.83F : f;
    }

    public ResourceLocation getTextureLocation(Turtle p_116259_) {
        return TURTLE_LOCATION;
    }
}
