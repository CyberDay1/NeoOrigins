package net.minecraft.client.renderer.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.OminousItemSpawner;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class OminousItemSpawnerRenderer extends EntityRenderer<OminousItemSpawner> {
    private static final float ROTATION_SPEED = 40.0F;
    private static final int TICKS_SCALING = 50;
    private final ItemRenderer itemRenderer;

    protected OminousItemSpawnerRenderer(EntityRendererProvider.Context p_338603_) {
        super(p_338603_);
        this.itemRenderer = p_338603_.getItemRenderer();
    }

    public ResourceLocation getTextureLocation(OminousItemSpawner p_338515_) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    public void render(OminousItemSpawner p_338815_, float p_338631_, float p_338539_, PoseStack p_338440_, MultiBufferSource p_338413_, int p_338541_) {
        ItemStack itemstack = p_338815_.getItem();
        if (!itemstack.isEmpty()) {
            p_338440_.pushPose();
            if (p_338815_.tickCount <= 50) {
                float f = Math.min((float)p_338815_.tickCount + p_338539_, 50.0F) / 50.0F;
                p_338440_.scale(f, f, f);
            }

            Level level = p_338815_.level();
            float f1 = Mth.wrapDegrees((float)(level.getGameTime() - 1L)) * 40.0F;
            float f2 = Mth.wrapDegrees((float)level.getGameTime()) * 40.0F;
            p_338440_.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(p_338539_, f1, f2)));
            ItemEntityRenderer.renderMultipleFromCount(this.itemRenderer, p_338440_, p_338413_, 15728880, itemstack, level.random, level);
            p_338440_.popPose();
        }
    }
}
