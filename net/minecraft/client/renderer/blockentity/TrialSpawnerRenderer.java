package net.minecraft.client.renderer.blockentity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.TrialSpawnerBlockEntity;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class TrialSpawnerRenderer implements BlockEntityRenderer<TrialSpawnerBlockEntity> {
    private final EntityRenderDispatcher entityRenderer;

    public TrialSpawnerRenderer(BlockEntityRendererProvider.Context p_312398_) {
        this.entityRenderer = p_312398_.getEntityRenderer();
    }

    public void render(TrialSpawnerBlockEntity p_312217_, float p_312274_, PoseStack p_312684_, MultiBufferSource p_312816_, int p_312320_, int p_312349_) {
        Level level = p_312217_.getLevel();
        if (level != null) {
            TrialSpawner trialspawner = p_312217_.getTrialSpawner();
            TrialSpawnerData trialspawnerdata = trialspawner.getData();
            Entity entity = trialspawnerdata.getOrCreateDisplayEntity(trialspawner, level, trialspawner.getState());
            if (entity != null) {
                SpawnerRenderer.renderEntityInSpawner(
                    p_312274_, p_312684_, p_312816_, p_312320_, entity, this.entityRenderer, trialspawnerdata.getOSpin(), trialspawnerdata.getSpin()
                );
            }
        }
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(TrialSpawnerBlockEntity blockEntity) {
        net.minecraft.core.BlockPos pos = blockEntity.getBlockPos();
        return new net.minecraft.world.phys.AABB(pos.getX() - 1.0, pos.getY() - 1.0, pos.getZ() - 1.0, pos.getX() + 2.0, pos.getY() + 2.0, pos.getZ() + 2.0);
    }
}
