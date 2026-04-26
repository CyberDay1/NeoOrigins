package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BaseSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SpawnerRenderer implements BlockEntityRenderer<SpawnerBlockEntity> {
    private final EntityRenderDispatcher entityRenderer;

    public SpawnerRenderer(BlockEntityRendererProvider.Context p_173673_) {
        this.entityRenderer = p_173673_.getEntityRenderer();
    }

    public void render(SpawnerBlockEntity p_112563_, float p_112564_, PoseStack p_112565_, MultiBufferSource p_112566_, int p_112567_, int p_112568_) {
        Level level = p_112563_.getLevel();
        if (level != null) {
            BaseSpawner basespawner = p_112563_.getSpawner();
            Entity entity = basespawner.getOrCreateDisplayEntity(level, p_112563_.getBlockPos());
            if (entity != null) {
                renderEntityInSpawner(p_112564_, p_112565_, p_112566_, p_112567_, entity, this.entityRenderer, basespawner.getoSpin(), basespawner.getSpin());
            }
        }
    }

    public static void renderEntityInSpawner(
        float p_311943_,
        PoseStack p_312805_,
        MultiBufferSource p_312394_,
        int p_311996_,
        Entity p_312223_,
        EntityRenderDispatcher p_312703_,
        double p_312192_,
        double p_312929_
    ) {
        p_312805_.pushPose();
        p_312805_.translate(0.5F, 0.0F, 0.5F);
        float f = 0.53125F;
        float f1 = Math.max(p_312223_.getBbWidth(), p_312223_.getBbHeight());
        if ((double)f1 > 1.0) {
            f /= f1;
        }

        p_312805_.translate(0.0F, 0.4F, 0.0F);
        p_312805_.mulPose(Axis.YP.rotationDegrees((float)Mth.lerp((double)p_311943_, p_312192_, p_312929_) * 10.0F));
        p_312805_.translate(0.0F, -0.2F, 0.0F);
        p_312805_.mulPose(Axis.XP.rotationDegrees(-30.0F));
        p_312805_.scale(f, f, f);
        p_312703_.render(p_312223_, 0.0, 0.0, 0.0, 0.0F, p_311943_, p_312805_, p_312394_, p_311996_);
        p_312805_.popPose();
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(SpawnerBlockEntity blockEntity) {
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        return new net.minecraft.world.phys.AABB(pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
    }
}
