package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.EndPlatformFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EndPortalBlock extends BaseEntityBlock implements Portal {
    public static final MapCodec<EndPortalBlock> CODEC = simpleCodec(EndPortalBlock::new);
    protected static final VoxelShape SHAPE = Block.box(0.0, 6.0, 0.0, 16.0, 12.0, 16.0);

    @Override
    public MapCodec<EndPortalBlock> codec() {
        return CODEC;
    }

    public EndPortalBlock(BlockBehaviour.Properties p_53017_) {
        super(p_53017_);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_153196_, BlockState p_153197_) {
        return new TheEndPortalBlockEntity(p_153196_, p_153197_);
    }

    @Override
    protected VoxelShape getShape(BlockState p_53038_, BlockGetter p_53039_, BlockPos p_53040_, CollisionContext p_53041_) {
        return SHAPE;
    }

    @Override
    protected void entityInside(BlockState p_53025_, Level p_53026_, BlockPos p_53027_, Entity p_53028_) {
        if (p_53028_.canUsePortal(false)
            && Shapes.joinIsNotEmpty(
                Shapes.create(p_53028_.getBoundingBox().move((double)(-p_53027_.getX()), (double)(-p_53027_.getY()), (double)(-p_53027_.getZ()))),
                p_53025_.getShape(p_53026_, p_53027_),
                BooleanOp.AND
            )) {
            if (!p_53026_.isClientSide && p_53026_.dimension() == Level.END && p_53028_ instanceof ServerPlayer serverplayer && !serverplayer.seenCredits) {
                serverplayer.showEndCredits();
                return;
            }

            p_53028_.setAsInsidePortal(this, p_53027_);
        }
    }

    @Override
    public DimensionTransition getPortalDestination(ServerLevel p_351032_, Entity p_350274_, BlockPos p_350589_) {
        ResourceKey<Level> resourcekey = p_351032_.dimension() == Level.END ? Level.OVERWORLD : Level.END;
        ServerLevel serverlevel = p_351032_.getServer().getLevel(resourcekey);
        if (serverlevel == null) {
            return null;
        } else {
            boolean flag = resourcekey == Level.END;
            BlockPos blockpos = flag ? ServerLevel.END_SPAWN_POINT : serverlevel.getSharedSpawnPos();
            Vec3 vec3 = blockpos.getBottomCenter();
            float f = p_350274_.getYRot();
            if (flag) {
                EndPlatformFeature.createEndPlatform(serverlevel, BlockPos.containing(vec3).below(), true);
                f = Direction.WEST.toYRot();
                if (p_350274_ instanceof ServerPlayer) {
                    vec3 = vec3.subtract(0.0, 1.0, 0.0);
                }
            } else {
                if (p_350274_ instanceof ServerPlayer serverplayer) {
                    return serverplayer.findRespawnPositionAndUseSpawnBlock(false, DimensionTransition.DO_NOTHING);
                }

                vec3 = p_350274_.adjustSpawnLocation(serverlevel, blockpos).getBottomCenter();
            }

            return new DimensionTransition(
                serverlevel,
                vec3,
                p_350274_.getDeltaMovement(),
                f,
                p_350274_.getXRot(),
                DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET)
            );
        }
    }

    @Override
    public void animateTick(BlockState p_221102_, Level p_221103_, BlockPos p_221104_, RandomSource p_221105_) {
        double d0 = (double)p_221104_.getX() + p_221105_.nextDouble();
        double d1 = (double)p_221104_.getY() + 0.8;
        double d2 = (double)p_221104_.getZ() + p_221105_.nextDouble();
        p_221103_.addParticle(ParticleTypes.SMOKE, d0, d1, d2, 0.0, 0.0, 0.0);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304508_, BlockPos p_53022_, BlockState p_53023_) {
        return ItemStack.EMPTY;
    }

    @Override
    protected boolean canBeReplaced(BlockState p_53035_, Fluid p_53036_) {
        return false;
    }
}
