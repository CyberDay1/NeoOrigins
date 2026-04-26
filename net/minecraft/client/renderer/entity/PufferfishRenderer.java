package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.PufferfishBigModel;
import net.minecraft.client.model.PufferfishMidModel;
import net.minecraft.client.model.PufferfishSmallModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Pufferfish;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PufferfishRenderer extends MobRenderer<Pufferfish, EntityModel<Pufferfish>> {
    private static final ResourceLocation PUFFER_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/fish/pufferfish.png");
    private int puffStateO = 3;
    private final EntityModel<Pufferfish> small;
    private final EntityModel<Pufferfish> mid;
    private final EntityModel<Pufferfish> big = this.getModel();

    public PufferfishRenderer(EntityRendererProvider.Context p_174358_) {
        super(p_174358_, new PufferfishBigModel<>(p_174358_.bakeLayer(ModelLayers.PUFFERFISH_BIG)), 0.2F);
        this.mid = new PufferfishMidModel<>(p_174358_.bakeLayer(ModelLayers.PUFFERFISH_MEDIUM));
        this.small = new PufferfishSmallModel<>(p_174358_.bakeLayer(ModelLayers.PUFFERFISH_SMALL));
    }

    public ResourceLocation getTextureLocation(Pufferfish p_115775_) {
        return PUFFER_LOCATION;
    }

    public void render(Pufferfish p_115777_, float p_115778_, float p_115779_, PoseStack p_115780_, MultiBufferSource p_115781_, int p_115782_) {
        int i = p_115777_.getPuffState();
        if (i != this.puffStateO) {
            if (i == 0) {
                this.model = this.small;
            } else if (i == 1) {
                this.model = this.mid;
            } else {
                this.model = this.big;
            }
        }

        this.puffStateO = i;
        this.shadowRadius = 0.1F + 0.1F * (float)i;
        super.render(p_115777_, p_115778_, p_115779_, p_115780_, p_115781_, p_115782_);
    }

    protected void setupRotations(Pufferfish p_320383_, PoseStack p_115763_, float p_115764_, float p_115765_, float p_115766_, float p_320616_) {
        p_115763_.translate(0.0F, Mth.cos(p_115764_ * 0.05F) * 0.08F, 0.0F);
        super.setupRotations(p_320383_, p_115763_, p_115764_, p_115765_, p_115766_, p_320616_);
    }
}
