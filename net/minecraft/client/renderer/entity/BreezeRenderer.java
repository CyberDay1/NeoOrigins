package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.BreezeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.BreezeEyesLayer;
import net.minecraft.client.renderer.entity.layers.BreezeWindLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.breeze.Breeze;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BreezeRenderer extends MobRenderer<Breeze, BreezeModel<Breeze>> {
    private static final ResourceLocation TEXTURE_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/breeze/breeze.png");

    public BreezeRenderer(EntityRendererProvider.Context p_312679_) {
        super(p_312679_, new BreezeModel<>(p_312679_.bakeLayer(ModelLayers.BREEZE)), 0.5F);
        this.addLayer(new BreezeWindLayer(p_312679_, this));
        this.addLayer(new BreezeEyesLayer(this));
    }

    public void render(Breeze p_316547_, float p_316622_, float p_316268_, PoseStack p_316604_, MultiBufferSource p_316232_, int p_316777_) {
        BreezeModel<Breeze> breezemodel = this.getModel();
        enable(breezemodel, breezemodel.head(), breezemodel.rods());
        super.render(p_316547_, p_316622_, p_316268_, p_316604_, p_316232_, p_316777_);
    }

    public ResourceLocation getTextureLocation(Breeze p_311864_) {
        return TEXTURE_LOCATION;
    }

    public static BreezeModel<Breeze> enable(BreezeModel<Breeze> p_316245_, ModelPart... p_316382_) {
        p_316245_.head().visible = false;
        p_316245_.eyes().visible = false;
        p_316245_.rods().visible = false;
        p_316245_.wind().visible = false;

        for (ModelPart modelpart : p_316382_) {
            modelpart.visible = true;
        }

        return p_316245_;
    }
}
