package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.ColorableHierarchicalModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.TropicalFishModelA;
import net.minecraft.client.model.TropicalFishModelB;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.TropicalFish;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TropicalFishPatternLayer extends RenderLayer<TropicalFish, ColorableHierarchicalModel<TropicalFish>> {
    private static final ResourceLocation KOB_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_1.png");
    private static final ResourceLocation SUNSTREAK_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_2.png");
    private static final ResourceLocation SNOOPER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_3.png");
    private static final ResourceLocation DASHER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_4.png");
    private static final ResourceLocation BRINELY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_5.png");
    private static final ResourceLocation SPOTTY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_a_pattern_6.png");
    private static final ResourceLocation FLOPPER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_1.png");
    private static final ResourceLocation STRIPEY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_2.png");
    private static final ResourceLocation GLITTER_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_3.png");
    private static final ResourceLocation BLOCKFISH_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_4.png");
    private static final ResourceLocation BETTY_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_5.png");
    private static final ResourceLocation CLAYFISH_TEXTURE = ResourceLocation.withDefaultNamespace("textures/entity/fish/tropical_b_pattern_6.png");
    private final TropicalFishModelA<TropicalFish> modelA;
    private final TropicalFishModelB<TropicalFish> modelB;

    public TropicalFishPatternLayer(RenderLayerParent<TropicalFish, ColorableHierarchicalModel<TropicalFish>> p_174547_, EntityModelSet p_174548_) {
        super(p_174547_);
        this.modelA = new TropicalFishModelA<>(p_174548_.bakeLayer(ModelLayers.TROPICAL_FISH_SMALL_PATTERN));
        this.modelB = new TropicalFishModelB<>(p_174548_.bakeLayer(ModelLayers.TROPICAL_FISH_LARGE_PATTERN));
    }

    public void render(
        PoseStack p_117612_,
        MultiBufferSource p_117613_,
        int p_117614_,
        TropicalFish p_117615_,
        float p_117616_,
        float p_117617_,
        float p_117618_,
        float p_117619_,
        float p_117620_,
        float p_117621_
    ) {
        TropicalFish.Pattern tropicalfish$pattern = p_117615_.getVariant();

        EntityModel<TropicalFish> entitymodel = (EntityModel<TropicalFish>)(switch (tropicalfish$pattern.base()) {
            case SMALL -> this.modelA;
            case LARGE -> this.modelB;
        });

        ResourceLocation resourcelocation = switch (tropicalfish$pattern) {
            case KOB -> KOB_TEXTURE;
            case SUNSTREAK -> SUNSTREAK_TEXTURE;
            case SNOOPER -> SNOOPER_TEXTURE;
            case DASHER -> DASHER_TEXTURE;
            case BRINELY -> BRINELY_TEXTURE;
            case SPOTTY -> SPOTTY_TEXTURE;
            case FLOPPER -> FLOPPER_TEXTURE;
            case STRIPEY -> STRIPEY_TEXTURE;
            case GLITTER -> GLITTER_TEXTURE;
            case BLOCKFISH -> BLOCKFISH_TEXTURE;
            case BETTY -> BETTY_TEXTURE;
            case CLAYFISH -> CLAYFISH_TEXTURE;
        };
        int i = p_117615_.getPatternColor().getTextureDiffuseColor();
        coloredCutoutModelCopyLayerRender(
            this.getParentModel(),
            entitymodel,
            resourcelocation,
            p_117612_,
            p_117613_,
            p_117614_,
            p_117615_,
            p_117616_,
            p_117617_,
            p_117619_,
            p_117620_,
            p_117621_,
            p_117618_,
            i
        );
    }
}
