package net.minecraft.client.renderer.entity;

import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkeletonRenderer<T extends AbstractSkeleton> extends HumanoidMobRenderer<T, SkeletonModel<T>> {
    private static final ResourceLocation SKELETON_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");

    public SkeletonRenderer(EntityRendererProvider.Context p_174380_) {
        this(p_174380_, ModelLayers.SKELETON, ModelLayers.SKELETON_INNER_ARMOR, ModelLayers.SKELETON_OUTER_ARMOR);
    }

    public SkeletonRenderer(EntityRendererProvider.Context p_174382_, ModelLayerLocation p_174383_, ModelLayerLocation p_174384_, ModelLayerLocation p_174385_) {
        this(p_174382_, p_174384_, p_174385_, new SkeletonModel<>(p_174382_.bakeLayer(p_174383_)));
    }

    public SkeletonRenderer(EntityRendererProvider.Context p_331294_, ModelLayerLocation p_330964_, ModelLayerLocation p_331594_, SkeletonModel<T> p_331796_) {
        super(p_331294_, p_331796_, 0.5F);
        this.addLayer(
            new HumanoidArmorLayer<>(
                this, new SkeletonModel(p_331294_.bakeLayer(p_330964_)), new SkeletonModel(p_331294_.bakeLayer(p_331594_)), p_331294_.getModelManager()
            )
        );
    }

    public ResourceLocation getTextureLocation(T p_115941_) {
        return SKELETON_LOCATION;
    }

    protected boolean isShaking(T p_174389_) {
        return p_174389_.isShaking();
    }
}
