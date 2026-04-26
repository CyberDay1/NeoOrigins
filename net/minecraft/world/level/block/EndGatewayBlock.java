package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.TheEndGatewayBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public class EndGatewayBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndGatewayBlock> CODEC = simpleCodec(EndGatewayBlock::new);

    @Override
    public MapCodec<EndGatewayBlock> codec() {
        return CODEC;
    }

    public EndGatewayBlock(BlockBehaviour.Properties p_52999_) {
        super(p_52999_);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_153193_, BlockState p_153194_) {
        return new TheEndGatewayBlockEntity(p_153193_, p_153194_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_153189_, BlockState p_153190_, BlockEntityType<T> p_153191_) {
        return createTickerHelper(
            p_153191_, BlockEntityType.END_GATEWAY, p_153189_.isClientSide ? TheEndGatewayBlockEntity::beamAnimationTick : TheEndGatewayBlockEntity::portalTick
        );
    }

    @Override
    public void animateTick(BlockState p_221097_, Level p_221098_, BlockPos p_221099_, RandomSource p_221100_) {
        BlockEntity blockentity = p_221098_.getBlockEntity(p_221099_);
        if (blockentity instanceof TheEndGatewayBlockEntity) {
            int i = ((TheEndGatewayBlockEntity)blockentity).getParticleAmount();

            for (int j = 0; j < i; j++) {
                double d0 = (double)p_221099_.getX() + p_221100_.nextDouble();
                double d1 = (double)p_221099_.getY() + p_221100_.nextDouble();
                double d2 = (double)p_221099_.getZ() + p_221100_.nextDouble();
                double d3 = (p_221100_.nextDouble() - 0.5) * 0.5;
                double d4 = (p_221100_.nextDouble() - 0.5) * 0.5;
                double d5 = (p_221100_.nextDouble() - 0.5) * 0.5;
                int k = p_221100_.nextInt(2) * 2 - 1;
                if (p_221100_.nextBoolean()) {
                    d2 = (double)p_221099_.getZ() + 0.5 + 0.25 * (double)k;
                    d5 = (double)(p_221100_.nextFloat() * 2.0F * (float)k);
                } else {
                    d0 = (double)p_221099_.getX() + 0.5 + 0.25 * (double)k;
                    d3 = (double)(p_221100_.nextFloat() * 2.0F * (float)k);
                }

                p_221098_.addParticle(ParticleTypes.PORTAL, d0, d1, d2, d3, d4, d5);
            }
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304768_, BlockPos p_53004_, BlockState p_53005_) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState p_53012_, Fluid p_53013_) {
        return false;
    }

    @Override
    protected void entityInside(BlockState p_350647_, Level p_350785_, BlockPos p_350610_, Entity p_350849_) {
        if (p_350849_.canUsePortal(false)
            && !p_350785_.isClientSide
            && p_350785_.getBlockEntity(p_350610_) instanceof TheEndGatewayBlockEntity theendgatewayblockentity
            && !theendgatewayblockentity.isCoolingDown()) {
            p_350849_.setAsInsidePortal(this, p_350610_);
            TheEndGatewayBlockEntity.triggerCooldown(p_350785_, p_350610_, p_350647_, theendgatewayblockentity);
        }
    }

    @Nullable
    @Override
    public DimensionTransition getPortalDestination(ServerLevel p_350958_, Entity p_350650_, BlockPos p_350525_) {
        if (p_350958_.getBlockEntity(p_350525_) instanceof TheEndGatewayBlockEntity theendgatewayblockentity) {
            Vec3 vec3 = theendgatewayblockentity.getPortalPosition(p_350958_, p_350525_);
            return vec3 != null
                ? new DimensionTransition(
                    p_350958_, vec3, calculateExitMovement(p_350650_), p_350650_.getYRot(), p_350650_.getXRot(), DimensionTransition.PLACE_PORTAL_TICKET
                )
                : null;
        } else {
            return null;
        }
    }

    private static Vec3 calculateExitMovement(Entity p_352063_) {
        return p_352063_ instanceof ThrownEnderpearl ? new Vec3(0.0, -1.0, 0.0) : p_352063_.getDeltaMovement();
    }
}
