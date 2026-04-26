package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.layers.PhantomEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PhantomRenderer extends MobRenderer<Phantom, PhantomModel<Phantom>> {
    private static final ResourceLocation PHANTOM_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/phantom.png");

    public PhantomRenderer(EntityRendererProvider.Context p_174338_) {
        super(p_174338_, new PhantomModel<>(p_174338_.bakeLayer(ModelLayers.PHANTOM)), 0.75F);
        this.addLayer(new PhantomEyesLayer<>(this));
    }

    public ResourceLocation getTextureLocation(Phantom p_115679_) {
        return PHANTOM_LOCATION;
    }

    protected void scale(Phantom p_115681_, PoseStack p_115682_, float p_115683_) {
        int i = p_115681_.getPhantomSize();
        float f = 1.0F + 0.15F * (float)i;
        p_115682_.scale(f, f, f);
        p_115682_.translate(0.0F, 1.3125F, 0.1875F);
    }

    protected void setupRotations(Phantom p_320470_, PoseStack p_115674_, float p_115675_, float p_115676_, float p_115677_, float p_320856_) {
        super.setupRotations(p_320470_, p_115674_, p_115675_, p_115676_, p_115677_, p_320856_);
        p_115674_.mulPose(Axis.XP.rotationDegrees(p_320470_.getXRot()));
    }
}
