package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.SheepFurModel;
import net.minecraft.client.model.SheepModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SheepFurLayer extends RenderLayer<Sheep, SheepModel<Sheep>> {
    private static final ResourceLocation SHEEP_FUR_LOCATION = ResourceLocation.withDefaultNamespace("textures/entity/sheep/sheep_fur.png");
    private final SheepFurModel<Sheep> model;

    public SheepFurLayer(RenderLayerParent<Sheep, SheepModel<Sheep>> p_174533_, EntityModelSet p_174534_) {
        super(p_174533_);
        this.model = new SheepFurModel<>(p_174534_.bakeLayer(ModelLayers.SHEEP_FUR));
    }

    public void render(
        PoseStack p_117421_,
        MultiBufferSource p_117422_,
        int p_117423_,
        Sheep p_117424_,
        float p_117425_,
        float p_117426_,
        float p_117427_,
        float p_117428_,
        float p_117429_,
        float p_117430_
    ) {
        if (!p_117424_.isSheared()) {
            if (p_117424_.isInvisible()) {
                Minecraft minecraft = Minecraft.getInstance();
                boolean flag = minecraft.shouldEntityAppearGlowing(p_117424_);
                if (flag) {
                    this.getParentModel().copyPropertiesTo(this.model);
                    this.model.prepareMobModel(p_117424_, p_117425_, p_117426_, p_117427_);
                    this.model.setupAnim(p_117424_, p_117425_, p_117426_, p_117428_, p_117429_, p_117430_);
                    VertexConsumer vertexconsumer = p_117422_.getBuffer(RenderType.outline(SHEEP_FUR_LOCATION));
                    this.model.renderToBuffer(p_117421_, vertexconsumer, p_117423_, LivingEntityRenderer.getOverlayCoords(p_117424_, 0.0F), -16777216);
                }
            } else {
                int i;
                if (p_117424_.hasCustomName() && "jeb_".equals(p_117424_.getName().getString())) {
                    int j = 25;
                    int k = p_117424_.tickCount / 25 + p_117424_.getId();
                    int l = DyeColor.values().length;
                    int i1 = k % l;
                    int j1 = (k + 1) % l;
                    float f = ((float)(p_117424_.tickCount % 25) + p_117427_) / 25.0F;
                    int k1 = Sheep.getColor(DyeColor.byId(i1));
                    int l1 = Sheep.getColor(DyeColor.byId(j1));
                    i = FastColor.ARGB32.lerp(f, k1, l1);
                } else {
                    i = Sheep.getColor(p_117424_.getColor());
                }

                coloredCutoutModelCopyLayerRender(
                    this.getParentModel(),
                    this.model,
                    SHEEP_FUR_LOCATION,
                    p_117421_,
                    p_117422_,
                    p_117423_,
                    p_117424_,
                    p_117425_,
                    p_117426_,
                    p_117428_,
                    p_117429_,
                    p_117430_,
                    p_117427_,
                    i
                );
            }
        }
    }
}
