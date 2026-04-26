package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BeehiveBlock extends BaseEntityBlock {
    public static final MapCodec<BeehiveBlock> CODEC = simpleCodec(BeehiveBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HONEY_LEVEL = BlockStateProperties.LEVEL_HONEY;
    public static final int MAX_HONEY_LEVELS = 5;
    private static final int SHEARED_HONEYCOMB_COUNT = 3;

    @Override
    public MapCodec<BeehiveBlock> codec() {
        return CODEC;
    }

    public BeehiveBlock(BlockBehaviour.Properties p_49568_) {
        super(p_49568_);
        this.registerDefaultState(this.stateDefinition.any().setValue(HONEY_LEVEL, Integer.valueOf(0)).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_49618_) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_49620_, Level p_49621_, BlockPos p_49622_) {
        return p_49620_.getValue(HONEY_LEVEL);
    }

    @Override
    public void playerDestroy(Level p_49584_, Player p_49585_, BlockPos p_49586_, BlockState p_49587_, @Nullable BlockEntity p_49588_, ItemStack p_49589_) {
        super.playerDestroy(p_49584_, p_49585_, p_49586_, p_49587_, p_49588_, p_49589_);
        if (!p_49584_.isClientSide && p_49588_ instanceof BeehiveBlockEntity beehiveblockentity) {
            if (!EnchantmentHelper.hasTag(p_49589_, EnchantmentTags.PREVENTS_BEE_SPAWNS_WHEN_MINING)) {
                beehiveblockentity.emptyAllLivingFromHive(p_49585_, p_49587_, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
                p_49584_.updateNeighbourForOutputSignal(p_49586_, this);
                this.angerNearbyBees(p_49584_, p_49586_);
            }

            CriteriaTriggers.BEE_NEST_DESTROYED.trigger((ServerPlayer)p_49585_, p_49587_, p_49589_, beehiveblockentity.getOccupantCount());
        }
    }

    private void angerNearbyBees(Level p_49650_, BlockPos p_49651_) {
        AABB aabb = new AABB(p_49651_).inflate(8.0, 6.0, 8.0);
        List<Bee> list = p_49650_.getEntitiesOfClass(Bee.class, aabb);
        if (!list.isEmpty()) {
            List<Player> list1 = p_49650_.getEntitiesOfClass(Player.class, aabb);
            if (list1.isEmpty()) {
                return;
            }

            for (Bee bee : list) {
                if (bee.getTarget() == null) {
                    Player player = Util.getRandom(list1, p_49650_.random);
                    bee.setTarget(player);
                }
            }
        }
    }

    public static void dropHoneycomb(Level p_49601_, BlockPos p_49602_) {
        popResource(p_49601_, p_49602_, new ItemStack(Items.HONEYCOMB, 3));
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316844_, BlockState p_316365_, Level p_316306_, BlockPos p_316497_, Player p_316824_, InteractionHand p_316436_, BlockHitResult p_316125_
    ) {
        int i = p_316365_.getValue(HONEY_LEVEL);
        boolean flag = false;
        if (i >= 5) {
            Item item = p_316844_.getItem();
            if (p_316844_.canPerformAction(net.neoforged.neoforge.common.ItemAbilities.SHEARS_HARVEST)) {
                p_316306_.playSound(p_316824_, p_316824_.getX(), p_316824_.getY(), p_316824_.getZ(), SoundEvents.BEEHIVE_SHEAR, SoundSource.BLOCKS, 1.0F, 1.0F);
                dropHoneycomb(p_316306_, p_316497_);
                p_316844_.hurtAndBreak(1, p_316824_, LivingEntity.getSlotForHand(p_316436_));
                flag = true;
                p_316306_.gameEvent(p_316824_, GameEvent.SHEAR, p_316497_);
            } else if (p_316844_.is(Items.GLASS_BOTTLE)) {
                p_316844_.shrink(1);
                p_316306_.playSound(p_316824_, p_316824_.getX(), p_316824_.getY(), p_316824_.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                if (p_316844_.isEmpty()) {
                    p_316824_.setItemInHand(p_316436_, new ItemStack(Items.HONEY_BOTTLE));
                } else if (!p_316824_.getInventory().add(new ItemStack(Items.HONEY_BOTTLE))) {
                    p_316824_.drop(new ItemStack(Items.HONEY_BOTTLE), false);
                }

                flag = true;
                p_316306_.gameEvent(p_316824_, GameEvent.FLUID_PICKUP, p_316497_);
            }

            if (!p_316306_.isClientSide() && flag) {
                p_316824_.awardStat(Stats.ITEM_USED.get(item));
            }
        }

        if (flag) {
            if (!CampfireBlock.isSmokeyPos(p_316306_, p_316497_)) {
                if (this.hiveContainsBees(p_316306_, p_316497_)) {
                    this.angerNearbyBees(p_316306_, p_316497_);
                }

                this.releaseBeesAndResetHoneyLevel(p_316306_, p_316365_, p_316497_, p_316824_, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            } else {
                this.resetHoneyLevel(p_316306_, p_316365_, p_316497_);
            }

            return ItemInteractionResult.sidedSuccess(p_316306_.isClientSide);
        } else {
            return super.useItemOn(p_316844_, p_316365_, p_316306_, p_316497_, p_316824_, p_316436_, p_316125_);
        }
    }

    private boolean hiveContainsBees(Level p_49655_, BlockPos p_49656_) {
        return p_49655_.getBlockEntity(p_49656_) instanceof BeehiveBlockEntity beehiveblockentity ? !beehiveblockentity.isEmpty() : false;
    }

    public void releaseBeesAndResetHoneyLevel(
        Level p_49595_, BlockState p_49596_, BlockPos p_49597_, @Nullable Player p_49598_, BeehiveBlockEntity.BeeReleaseStatus p_49599_
    ) {
        this.resetHoneyLevel(p_49595_, p_49596_, p_49597_);
        if (p_49595_.getBlockEntity(p_49597_) instanceof BeehiveBlockEntity beehiveblockentity) {
            beehiveblockentity.emptyAllLivingFromHive(p_49598_, p_49596_, p_49599_);
        }
    }

    public void resetHoneyLevel(Level p_49591_, BlockState p_49592_, BlockPos p_49593_) {
        p_49591_.setBlock(p_49593_, p_49592_.setValue(HONEY_LEVEL, Integer.valueOf(0)), 3);
    }

    @Override
    public void animateTick(BlockState p_220773_, Level p_220774_, BlockPos p_220775_, RandomSource p_220776_) {
        if (p_220773_.getValue(HONEY_LEVEL) >= 5) {
            for (int i = 0; i < p_220776_.nextInt(1) + 1; i++) {
                this.trySpawnDripParticles(p_220774_, p_220775_, p_220773_);
            }
        }
    }

    private void trySpawnDripParticles(Level p_49604_, BlockPos p_49605_, BlockState p_49606_) {
        if (p_49606_.getFluidState().isEmpty() && !(p_49604_.random.nextFloat() < 0.3F)) {
            VoxelShape voxelshape = p_49606_.getCollisionShape(p_49604_, p_49605_);
            double d0 = voxelshape.max(Direction.Axis.Y);
            if (d0 >= 1.0 && !p_49606_.is(BlockTags.IMPERMEABLE)) {
                double d1 = voxelshape.min(Direction.Axis.Y);
                if (d1 > 0.0) {
                    this.spawnParticle(p_49604_, p_49605_, voxelshape, (double)p_49605_.getY() + d1 - 0.05);
                } else {
                    BlockPos blockpos = p_49605_.below();
                    BlockState blockstate = p_49604_.getBlockState(blockpos);
                    VoxelShape voxelshape1 = blockstate.getCollisionShape(p_49604_, blockpos);
                    double d2 = voxelshape1.max(Direction.Axis.Y);
                    if ((d2 < 1.0 || !blockstate.isCollisionShapeFullBlock(p_49604_, blockpos)) && blockstate.getFluidState().isEmpty()) {
                        this.spawnParticle(p_49604_, p_49605_, voxelshape, (double)p_49605_.getY() - 0.05);
                    }
                }
            }
        }
    }

    private void spawnParticle(Level p_49613_, BlockPos p_49614_, VoxelShape p_49615_, double p_49616_) {
        this.spawnFluidParticle(
            p_49613_,
            (double)p_49614_.getX() + p_49615_.min(Direction.Axis.X),
            (double)p_49614_.getX() + p_49615_.max(Direction.Axis.X),
            (double)p_49614_.getZ() + p_49615_.min(Direction.Axis.Z),
            (double)p_49614_.getZ() + p_49615_.max(Direction.Axis.Z),
            p_49616_
        );
    }

    private void spawnFluidParticle(Level p_49577_, double p_49578_, double p_49579_, double p_49580_, double p_49581_, double p_49582_) {
        p_49577_.addParticle(
            ParticleTypes.DRIPPING_HONEY,
            Mth.lerp(p_49577_.random.nextDouble(), p_49578_, p_49579_),
            p_49582_,
            Mth.lerp(p_49577_.random.nextDouble(), p_49580_, p_49581_),
            0.0,
            0.0,
            0.0
        );
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_49573_) {
        return this.defaultBlockState().setValue(FACING, p_49573_.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_49646_) {
        p_49646_.add(HONEY_LEVEL, FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_49653_) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p_152184_, BlockState p_152185_) {
        return new BeehiveBlockEntity(p_152184_, p_152185_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_152180_, BlockState p_152181_, BlockEntityType<T> p_152182_) {
        return p_152180_.isClientSide ? null : createTickerHelper(p_152182_, BlockEntityType.BEEHIVE, BeehiveBlockEntity::serverTick);
    }

    @Override
    public BlockState playerWillDestroy(Level p_49608_, BlockPos p_49609_, BlockState p_49610_, Player p_49611_) {
        if (!p_49608_.isClientSide
            && p_49611_.isCreative()
            && p_49608_.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)
            && p_49608_.getBlockEntity(p_49609_) instanceof BeehiveBlockEntity beehiveblockentity) {
            int i = p_49610_.getValue(HONEY_LEVEL);
            boolean flag = !beehiveblockentity.isEmpty();
            if (flag || i > 0) {
                ItemStack itemstack = new ItemStack(this);
                itemstack.applyComponents(beehiveblockentity.collectComponents());
                itemstack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(HONEY_LEVEL, i));
                ItemEntity itementity = new ItemEntity(p_49608_, (double)p_49609_.getX(), (double)p_49609_.getY(), (double)p_49609_.getZ(), itemstack);
                itementity.setDefaultPickUpDelay();
                p_49608_.addFreshEntity(itementity);
            }
        }

        return super.playerWillDestroy(p_49608_, p_49609_, p_49610_, p_49611_);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState p_49636_, LootParams.Builder p_287581_) {
        Entity entity = p_287581_.getOptionalParameter(LootContextParams.THIS_ENTITY);
        if (entity instanceof PrimedTnt
            || entity instanceof Creeper
            || entity instanceof WitherSkull
            || entity instanceof WitherBoss
            || entity instanceof MinecartTNT) {
            BlockEntity blockentity = p_287581_.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
            if (blockentity instanceof BeehiveBlockEntity beehiveblockentity) {
                beehiveblockentity.emptyAllLivingFromHive(null, p_49636_, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
            }
        }

        return super.getDrops(p_49636_, p_287581_);
    }

    @Override
    protected BlockState updateShape(BlockState p_49639_, Direction p_49640_, BlockState p_49641_, LevelAccessor p_49642_, BlockPos p_49643_, BlockPos p_49644_) {
        if (p_49642_.getBlockState(p_49644_).getBlock() instanceof FireBlock
            && p_49642_.getBlockEntity(p_49643_) instanceof BeehiveBlockEntity beehiveblockentity) {
            beehiveblockentity.emptyAllLivingFromHive(null, p_49639_, BeehiveBlockEntity.BeeReleaseStatus.EMERGENCY);
        }

        return super.updateShape(p_49639_, p_49640_, p_49641_, p_49642_, p_49643_, p_49644_);
    }

    @Override
    public BlockState rotate(BlockState p_304785_, Rotation p_304624_) {
        return p_304785_.setValue(FACING, p_304624_.rotate(p_304785_.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState p_304677_, Mirror p_304660_) {
        return p_304677_.rotate(p_304660_.getRotation(p_304677_.getValue(FACING)));
    }
}
