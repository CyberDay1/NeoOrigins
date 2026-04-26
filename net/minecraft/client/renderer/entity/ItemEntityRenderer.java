package net.minecraft.client.renderer.entity;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemEntityRenderer extends EntityRenderer<ItemEntity> {
    private static final float ITEM_BUNDLE_OFFSET_SCALE = 0.15F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_X = 0.0F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_Y = 0.0F;
    private static final float FLAT_ITEM_BUNDLE_OFFSET_Z = 0.09375F;
    private final ItemRenderer itemRenderer;
    private final RandomSource random = RandomSource.create();

    public ItemEntityRenderer(EntityRendererProvider.Context p_174198_) {
        super(p_174198_);
        this.itemRenderer = p_174198_.getItemRenderer();
        this.shadowRadius = 0.15F;
        this.shadowStrength = 0.75F;
    }

    public ResourceLocation getTextureLocation(ItemEntity p_115034_) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public void render(ItemEntity p_115036_, float p_115037_, float p_115038_, PoseStack p_115039_, MultiBufferSource p_115040_, int p_115041_) {
        p_115039_.pushPose();
        ItemStack itemstack = p_115036_.getItem();
        this.random.setSeed((long)getSeedForItemStack(itemstack));
        BakedModel bakedmodel = this.itemRenderer.getModel(itemstack, p_115036_.level(), null, p_115036_.getId());
        boolean flag = bakedmodel.isGui3d();
        float f = 0.25F;
        boolean shouldBob = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(itemstack).shouldBobAsEntity(itemstack);
        float f1 = shouldBob ? Mth.sin(((float)p_115036_.getAge() + p_115038_) / 10.0F + p_115036_.bobOffs) * 0.1F + 0.1F : 0;
        float f2 = bakedmodel.getTransforms().getTransform(ItemDisplayContext.GROUND).scale.y();
        p_115039_.translate(0.0F, f1 + 0.25F * f2, 0.0F);
        float f3 = p_115036_.getSpin(p_115038_);
        p_115039_.mulPose(Axis.YP.rotation(f3));
        renderMultipleFromCount(this.itemRenderer, p_115039_, p_115040_, p_115041_, itemstack, bakedmodel, flag, this.random);
        p_115039_.popPose();
        super.render(p_115036_, p_115037_, p_115038_, p_115039_, p_115040_, p_115041_);
    }

    public static int getSeedForItemStack(ItemStack p_324105_) {
        return p_324105_.isEmpty() ? 187 : Item.getId(p_324105_.getItem()) + p_324105_.getDamageValue();
    }

    @VisibleForTesting
    static int getRenderedAmount(int p_324603_) {
        if (p_324603_ <= 1) {
            return 1;
        } else if (p_324603_ <= 16) {
            return 2;
        } else if (p_324603_ <= 32) {
            return 3;
        } else {
            return p_324603_ <= 48 ? 4 : 5;
        }
    }

    public static void renderMultipleFromCount(
        ItemRenderer p_323875_, PoseStack p_323763_, MultiBufferSource p_324606_, int p_323603_, ItemStack p_323969_, RandomSource p_324507_, Level p_323902_
    ) {
        BakedModel bakedmodel = p_323875_.getModel(p_323969_, p_323902_, null, 0);
        renderMultipleFromCount(p_323875_, p_323763_, p_324606_, p_323603_, p_323969_, bakedmodel, bakedmodel.isGui3d(), p_324507_);
    }

    public static void renderMultipleFromCount(
        ItemRenderer p_324541_,
        PoseStack p_323733_,
        MultiBufferSource p_324107_,
        int p_323740_,
        ItemStack p_323718_,
        BakedModel p_324183_,
        boolean p_324462_,
        RandomSource p_324565_
    ) {
        int i = getRenderedAmount(p_323718_.getCount());
        float f = p_324183_.getTransforms().ground.scale.x();
        float f1 = p_324183_.getTransforms().ground.scale.y();
        float f2 = p_324183_.getTransforms().ground.scale.z();
        if (!p_324462_) {
            float f3 = -0.0F * (float)(i - 1) * 0.5F * f;
            float f4 = -0.0F * (float)(i - 1) * 0.5F * f1;
            float f5 = -0.09375F * (float)(i - 1) * 0.5F * f2;
            p_323733_.translate(f3, f4, f5);
        }

        boolean shouldSpread = net.neoforged.neoforge.client.extensions.common.IClientItemExtensions.of(p_323718_).shouldSpreadAsEntity(p_323718_);
        for (int j = 0; j < i; j++) {
            p_323733_.pushPose();
            if (j > 0 && shouldSpread) {
                if (p_324462_) {
                    float f7 = (p_324565_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f9 = (p_324565_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    float f6 = (p_324565_.nextFloat() * 2.0F - 1.0F) * 0.15F;
                    p_323733_.translate(f7, f9, f6);
                } else {
                    float f8 = (p_324565_.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    float f10 = (p_324565_.nextFloat() * 2.0F - 1.0F) * 0.15F * 0.5F;
                    p_323733_.translate(f8, f10, 0.0F);
                }
            }

            p_324541_.render(p_323718_, ItemDisplayContext.GROUND, false, p_323733_, p_324107_, p_323740_, OverlayTexture.NO_OVERLAY, p_324183_);
            p_323733_.popPose();
            if (!p_324462_) {
                p_323733_.translate(0.0F * f, 0.0F * f1, 0.09375F * f2);
            }
        }
    }
}
