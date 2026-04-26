package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SkeletonClothingLayer<T extends Mob & RangedAttackMob, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final SkeletonModel<T> layerModel;
    private final ResourceLocation clothesLocation;

    public SkeletonClothingLayer(RenderLayerParent<T, M> p_326918_, EntityModelSet p_326830_, ModelLayerLocation p_326794_, ResourceLocation p_326858_) {
        super(p_326918_);
        this.clothesLocation = p_326858_;
        this.layerModel = new SkeletonModel<>(p_326830_.bakeLayer(p_326794_));
    }

    public void render(
        PoseStack p_326861_,
        MultiBufferSource p_326915_,
        int p_326897_,
        T p_326852_,
        float p_326908_,
        float p_326841_,
        float p_326922_,
        float p_326956_,
        float p_326949_,
        float p_326790_
    ) {
        coloredCutoutModelCopyLayerRender(
            this.getParentModel(),
            this.layerModel,
            this.clothesLocation,
            p_326861_,
            p_326915_,
            p_326897_,
            p_326852_,
            p_326908_,
            p_326841_,
            p_326956_,
            p_326949_,
            p_326790_,
            p_326922_,
            -1
        );
    }
}
