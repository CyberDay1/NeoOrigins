package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultClientData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VaultRenderer implements BlockEntityRenderer<VaultBlockEntity> {
    private final ItemRenderer itemRenderer;
    private final RandomSource random = RandomSource.create();

    public VaultRenderer(BlockEntityRendererProvider.Context p_324525_) {
        this.itemRenderer = p_324525_.getItemRenderer();
    }

    public void render(VaultBlockEntity p_323921_, float p_324166_, PoseStack p_324316_, MultiBufferSource p_323716_, int p_324311_, int p_324178_) {
        if (VaultBlockEntity.Client.shouldDisplayActiveEffects(p_323921_.getSharedData())) {
            Level level = p_323921_.getLevel();
            if (level != null) {
                ItemStack itemstack = p_323921_.getSharedData().getDisplayItem();
                if (!itemstack.isEmpty()) {
                    this.random.setSeed((long)ItemEntityRenderer.getSeedForItemStack(itemstack));
                    VaultClientData vaultclientdata = p_323921_.getClientData();
                    renderItemInside(
                        p_324166_,
                        level,
                        p_324316_,
                        p_323716_,
                        p_324311_,
                        itemstack,
                        this.itemRenderer,
                        vaultclientdata.previousSpin(),
                        vaultclientdata.currentSpin(),
                        this.random
                    );
                }
            }
        }
    }

    public static void renderItemInside(
        float p_324463_,
        Level p_323997_,
        PoseStack p_323756_,
        MultiBufferSource p_323535_,
        int p_324189_,
        ItemStack p_324326_,
        ItemRenderer p_324590_,
        float p_324476_,
        float p_324237_,
        RandomSource p_323851_
    ) {
        p_323756_.pushPose();
        p_323756_.translate(0.5F, 0.4F, 0.5F);
        p_323756_.mulPose(Axis.YP.rotationDegrees(Mth.rotLerp(p_324463_, p_324476_, p_324237_)));
        ItemEntityRenderer.renderMultipleFromCount(p_324590_, p_323756_, p_323535_, p_324189_, p_324326_, p_323851_, p_323997_);
        p_323756_.popPose();
    }
}
