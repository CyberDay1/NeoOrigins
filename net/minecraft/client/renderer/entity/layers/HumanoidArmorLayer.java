package net.minecraft.client.renderer.entity.layers;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.DyedItemColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class HumanoidArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>, A extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private static final Map<String, ResourceLocation> ARMOR_LOCATION_CACHE = Maps.newHashMap();
    private final A innerModel;
    private final A outerModel;
    private final TextureAtlas armorTrimAtlas;

    public HumanoidArmorLayer(RenderLayerParent<T, M> p_267286_, A p_267110_, A p_267150_, ModelManager p_267238_) {
        super(p_267286_);
        this.innerModel = p_267110_;
        this.outerModel = p_267150_;
        this.armorTrimAtlas = p_267238_.getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    public void render(
        PoseStack p_117096_,
        MultiBufferSource p_117097_,
        int p_117098_,
        T p_117099_,
        float p_117100_,
        float p_117101_,
        float p_117102_,
        float p_117103_,
        float p_117104_,
        float p_117105_
    ) {
        this.renderArmorPiece(p_117096_, p_117097_, p_117099_, EquipmentSlot.CHEST, p_117098_, this.getArmorModel(EquipmentSlot.CHEST), p_117100_, p_117101_, p_117102_, p_117103_, p_117104_, p_117105_);
        this.renderArmorPiece(p_117096_, p_117097_, p_117099_, EquipmentSlot.LEGS, p_117098_, this.getArmorModel(EquipmentSlot.LEGS), p_117100_, p_117101_, p_117102_, p_117103_, p_117104_, p_117105_);
        this.renderArmorPiece(p_117096_, p_117097_, p_117099_, EquipmentSlot.FEET, p_117098_, this.getArmorModel(EquipmentSlot.FEET), p_117100_, p_117101_, p_117102_, p_117103_, p_117104_, p_117105_);
        this.renderArmorPiece(p_117096_, p_117097_, p_117099_, EquipmentSlot.HEAD, p_117098_, this.getArmorModel(EquipmentSlot.HEAD), p_117100_, p_117101_, p_117102_, p_117103_, p_117104_, p_117105_);
    }

    /** @deprecated Neo: use {@link #renderArmorPiece(PoseStack, MultiBufferSource, LivingEntity, EquipmentSlot, int, HumanoidModel, float, float, float, float, float, float)} instead. */
    @Deprecated
    private void renderArmorPiece(PoseStack p_117119_, MultiBufferSource p_117120_, T p_117121_, EquipmentSlot p_117122_, int p_117123_, A p_117124_) {
        this.renderArmorPiece(p_117119_, p_117120_, p_117121_, p_117122_, p_117123_, p_117124_, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private void renderArmorPiece(PoseStack p_117119_, MultiBufferSource p_117120_, T p_117121_, EquipmentSlot p_117122_, int p_117123_, A p_117124_, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack itemstack = p_117121_.getItemBySlot(p_117122_);
        if (itemstack.getItem() instanceof ArmorItem armoritem) {
            if (armoritem.getEquipmentSlot() == p_117122_) {
                this.getParentModel().copyPropertiesTo(p_117124_);
                this.setPartVisibility(p_117124_, p_117122_);
                net.minecraft.client.model.Model model = getArmorModelHook(p_117121_, itemstack, p_117122_, p_117124_);
                boolean flag = this.usesInnerModel(p_117122_);
                ArmorMaterial armormaterial = armoritem.getMaterial().value();

                net.neoforged.neoforge.client.extensions.common.IClientItemExtensions extensions = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemstack);
                extensions.setupModelAnimations(p_117121_, itemstack, p_117122_, model, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
                int fallbackColor = extensions.getDefaultDyeColor(itemstack);
                for (int layerIdx = 0; layerIdx < armormaterial.layers().size(); layerIdx++) {
                    ArmorMaterial.Layer armormaterial$layer = armormaterial.layers().get(layerIdx);
                    int j = extensions.getArmorLayerTintColor(itemstack, p_117121_, armormaterial$layer, layerIdx, fallbackColor);
                    if (j != 0) {
                        var texture = net.neoforged.neoforge.client.ClientHooks.getArmorTexture(p_117121_, itemstack, armormaterial$layer, flag, p_117122_);
                        this.renderModel(p_117119_, p_117120_, p_117123_, model, j, texture);
                    }
                }

                ArmorTrim armortrim = itemstack.get(DataComponents.TRIM);
                if (armortrim != null) {
                    this.renderTrim(armoritem.getMaterial(), p_117119_, p_117120_, p_117123_, armortrim, model, flag);
                }

                if (itemstack.hasFoil()) {
                    this.renderGlint(p_117119_, p_117120_, p_117123_, model);
                }
            }
        }
    }

    protected void setPartVisibility(A p_117126_, EquipmentSlot p_117127_) {
        p_117126_.setAllVisible(false);
        switch (p_117127_) {
            case HEAD:
                p_117126_.head.visible = true;
                p_117126_.hat.visible = true;
                break;
            case CHEST:
                p_117126_.body.visible = true;
                p_117126_.rightArm.visible = true;
                p_117126_.leftArm.visible = true;
                break;
            case LEGS:
                p_117126_.body.visible = true;
                p_117126_.rightLeg.visible = true;
                p_117126_.leftLeg.visible = true;
                break;
            case FEET:
                p_117126_.rightLeg.visible = true;
                p_117126_.leftLeg.visible = true;
        }
    }

    private void renderModel(PoseStack p_289664_, MultiBufferSource p_289689_, int p_289681_, A p_289658_, int p_350798_, ResourceLocation p_324344_) {
        renderModel(p_289664_, p_289689_, p_289681_, (net.minecraft.client.model.Model) p_289658_, p_350798_, p_324344_);
    }
    private void renderModel(PoseStack p_289664_, MultiBufferSource p_289689_, int p_289681_, net.minecraft.client.model.Model p_289658_, int p_350798_, ResourceLocation p_324344_) {
        VertexConsumer vertexconsumer = p_289689_.getBuffer(RenderType.armorCutoutNoCull(p_324344_));
        p_289658_.renderToBuffer(p_289664_, vertexconsumer, p_289681_, OverlayTexture.NO_OVERLAY, p_350798_);
    }

    private void renderTrim(
        Holder<ArmorMaterial> p_323506_, PoseStack p_289687_, MultiBufferSource p_289643_, int p_289683_, ArmorTrim p_289692_, A p_289663_, boolean p_289651_
    ) {
        renderTrim(p_323506_, p_289687_, p_289643_, p_289683_, p_289692_, (net.minecraft.client.model.Model) p_289663_, p_289651_);
    }
    private void renderTrim(
        Holder<ArmorMaterial> p_323506_, PoseStack p_289687_, MultiBufferSource p_289643_, int p_289683_, ArmorTrim p_289692_, net.minecraft.client.model.Model p_289663_, boolean p_289651_
    ) {
        TextureAtlasSprite textureatlassprite = this.armorTrimAtlas
            .getSprite(p_289651_ ? p_289692_.innerTexture(p_323506_) : p_289692_.outerTexture(p_323506_));
        VertexConsumer vertexconsumer = textureatlassprite.wrap(p_289643_.getBuffer(Sheets.armorTrimsSheet(p_289692_.pattern().value().decal())));
        p_289663_.renderToBuffer(p_289687_, vertexconsumer, p_289683_, OverlayTexture.NO_OVERLAY);
    }

    private void renderGlint(PoseStack p_289673_, MultiBufferSource p_289654_, int p_289649_, A p_289659_) {
        renderGlint(p_289673_, p_289654_, p_289649_, (net.minecraft.client.model.Model) p_289659_);
    }
    private void renderGlint(PoseStack p_289673_, MultiBufferSource p_289654_, int p_289649_, net.minecraft.client.model.Model p_289659_) {
        p_289659_.renderToBuffer(p_289673_, p_289654_.getBuffer(RenderType.armorEntityGlint()), p_289649_, OverlayTexture.NO_OVERLAY);
    }

    private A getArmorModel(EquipmentSlot p_117079_) {
        return this.usesInnerModel(p_117079_) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot p_117129_) {
        return p_117129_ == EquipmentSlot.LEGS;
    }

    /**
     * Hook to allow item-sensitive armor model. for HumanoidArmorLayer.
     */
    protected net.minecraft.client.model.Model getArmorModelHook(T entity, ItemStack itemStack, EquipmentSlot slot, A model) {
        return net.neoforged.neoforge.client.ClientHooks.getArmorModel(entity, itemStack, slot, model);
    }
}
