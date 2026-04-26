package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Crackiness;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.AnimalArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WolfArmorLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {
    private final WolfModel<Wolf> model;
    private static final Map<Crackiness.Level, ResourceLocation> ARMOR_CRACK_LOCATIONS = Map.of(
        Crackiness.Level.LOW,
        ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_low.png"),
        Crackiness.Level.MEDIUM,
        ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_medium.png"),
        Crackiness.Level.HIGH,
        ResourceLocation.withDefaultNamespace("textures/entity/wolf/wolf_armor_crackiness_high.png")
    );

    public WolfArmorLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> p_316639_, EntityModelSet p_316756_) {
        super(p_316639_);
        this.model = new WolfModel<>(p_316756_.bakeLayer(ModelLayers.WOLF_ARMOR));
    }

    public void render(
        PoseStack p_316608_,
        MultiBufferSource p_316832_,
        int p_316312_,
        Wolf p_316642_,
        float p_316350_,
        float p_316147_,
        float p_316637_,
        float p_316734_,
        float p_316302_,
        float p_316605_
    ) {
        if (p_316642_.hasArmor()) {
            ItemStack itemstack = p_316642_.getBodyArmorItem();
            if (itemstack.getItem() instanceof AnimalArmorItem animalarmoritem && animalarmoritem.getBodyType() == AnimalArmorItem.BodyType.CANINE) {
                this.getParentModel().copyPropertiesTo(this.model);
                this.model.prepareMobModel(p_316642_, p_316350_, p_316147_, p_316637_);
                this.model.setupAnim(p_316642_, p_316350_, p_316147_, p_316734_, p_316302_, p_316605_);
                VertexConsumer vertexconsumer = p_316832_.getBuffer(RenderType.entityCutoutNoCull(animalarmoritem.getTexture()));
                this.model.renderToBuffer(p_316608_, vertexconsumer, p_316312_, OverlayTexture.NO_OVERLAY);
                this.maybeRenderColoredLayer(p_316608_, p_316832_, p_316312_, itemstack, animalarmoritem);
                this.maybeRenderCracks(p_316608_, p_316832_, p_316312_, itemstack);
                return;
            }
        }
    }

    private void maybeRenderColoredLayer(PoseStack p_330741_, MultiBufferSource p_330339_, int p_332179_, ItemStack p_331250_, AnimalArmorItem p_330867_) {
        if (p_331250_.is(ItemTags.DYEABLE)) {
            int i = DyedItemColor.getOrDefault(p_331250_, 0);
            if (FastColor.ARGB32.alpha(i) == 0) {
                return;
            }

            ResourceLocation resourcelocation = p_330867_.getOverlayTexture();
            if (resourcelocation == null) {
                return;
            }

            this.model
                .renderToBuffer(
                    p_330741_,
                    p_330339_.getBuffer(RenderType.entityCutoutNoCull(resourcelocation)),
                    p_332179_,
                    OverlayTexture.NO_OVERLAY,
                    FastColor.ARGB32.opaque(i)
                );
        }
    }

    private void maybeRenderCracks(PoseStack p_331222_, MultiBufferSource p_331637_, int p_330931_, ItemStack p_331187_) {
        Crackiness.Level crackiness$level = Crackiness.WOLF_ARMOR.byDamage(p_331187_);
        if (crackiness$level != Crackiness.Level.NONE) {
            ResourceLocation resourcelocation = ARMOR_CRACK_LOCATIONS.get(crackiness$level);
            VertexConsumer vertexconsumer = p_331637_.getBuffer(RenderType.entityTranslucent(resourcelocation));
            this.model.renderToBuffer(p_331222_, vertexconsumer, p_330931_, OverlayTexture.NO_OVERLAY);
        }
    }
}
