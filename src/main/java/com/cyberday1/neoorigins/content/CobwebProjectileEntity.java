package com.cyberday1.neoorigins.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Arachnid cobweb projectile — throwable that places a cobweb block at the
 * impact point. Block hits place on the face the projectile struck; entity
 * hits place at the entity's current block position so vanilla cobweb
 * slowdown can apply.
 */
public class CobwebProjectileEntity extends ThrowableItemProjectile {

    public CobwebProjectileEntity(EntityType<? extends CobwebProjectileEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return Items.COBWEB;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (this.level().isClientSide) return;
        BlockPos target = resolvePlacePos(result);
        if (target != null) {
            var state = this.level().getBlockState(target);
            if (state.canBeReplaced()) {
                this.level().setBlock(target, Blocks.COBWEB.defaultBlockState(), 3);
            }
        }
        this.discard();
    }

    private BlockPos resolvePlacePos(HitResult result) {
        if (result instanceof BlockHitResult bhr) {
            return bhr.getBlockPos().relative(bhr.getDirection());
        }
        if (result instanceof EntityHitResult ehr) {
            return ehr.getEntity().blockPosition();
        }
        return null;
    }
}
