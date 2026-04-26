package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.BoggedModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.SkeletonClothingLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Bogged;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BoggedRenderer extends SkeletonRenderer<Bogged> {
    private static final ResourceLocation BOGGED_SKELETON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/bogged.png");
    private static final ResourceLocation BOGGED_OUTER_LAYER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/bogged_overlay.png");

    public BoggedRenderer(EntityRendererProvider.Context p_326938_) {
        super(p_326938_, ModelLayers.BOGGED_INNER_ARMOR, ModelLayers.BOGGED_OUTER_ARMOR, new BoggedModel(p_326938_.bakeLayer(ModelLayers.BOGGED)));
        this.addLayer(new SkeletonClothingLayer<>(this, p_326938_.getModelSet(), ModelLayers.BOGGED_OUTER_LAYER, BOGGED_OUTER_LAYER_LOCATION));
    }

    public ResourceLocation getTextureLocation(Bogged p_331017_) {
        return BOGGED_SKELETON_LOCATION;
    }
}
