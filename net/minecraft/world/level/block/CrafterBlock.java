package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.FrontAndTop;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeCache;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class CrafterBlock extends BaseEntityBlock {
    public static final MapCodec<CrafterBlock> CODEC = simpleCodec(CrafterBlock::new);
    public static final BooleanProperty CRAFTING = BlockStateProperties.CRAFTING;
    public static final BooleanProperty TRIGGERED = BlockStateProperties.TRIGGERED;
    private static final EnumProperty<FrontAndTop> ORIENTATION = BlockStateProperties.ORIENTATION;
    private static final int MAX_CRAFTING_TICKS = 6;
    private static final int CRAFTING_TICK_DELAY = 4;
    private static final RecipeCache RECIPE_CACHE = new RecipeCache(10);
    private static final int CRAFTER_ADVANCEMENT_DIAMETER = 17;

    public CrafterBlock(BlockBehaviour.Properties p_307674_) {
        super(p_307674_);
        this.registerDefaultState(
            this.stateDefinition
                .any()
                .setValue(ORIENTATION, FrontAndTop.NORTH_UP)
                .setValue(TRIGGERED, Boolean.valueOf(false))
                .setValue(CRAFTING, Boolean.valueOf(false))
        );
    }

    @Override
    protected MapCodec<CrafterBlock> codec() {
        return CODEC;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_307445_) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_307633_, Level p_307264_, BlockPos p_307557_) {
        return p_307264_.getBlockEntity(p_307557_) instanceof CrafterBlockEntity crafterblockentity ? crafterblockentity.getRedstoneSignal() : 0;
    }

    @Override
    protected void neighborChanged(BlockState p_307205_, Level p_307451_, BlockPos p_307272_, Block p_307542_, BlockPos p_307508_, boolean p_307540_) {
        boolean flag = p_307451_.hasNeighborSignal(p_307272_);
        boolean flag1 = p_307205_.getValue(TRIGGERED);
        BlockEntity blockentity = p_307451_.getBlockEntity(p_307272_);
        if (flag && !flag1) {
            p_307451_.scheduleTick(p_307272_, this, 4);
            p_307451_.setBlock(p_307272_, p_307205_.setValue(TRIGGERED, Boolean.valueOf(true)), 2);
            this.setBlockEntityTriggered(blockentity, true);
        } else if (!flag && flag1) {
            p_307451_.setBlock(p_307272_, p_307205_.setValue(TRIGGERED, Boolean.valueOf(false)).setValue(CRAFTING, Boolean.valueOf(false)), 2);
            this.setBlockEntityTriggered(blockentity, false);
        }
    }

    @Override
    protected void tick(BlockState p_307471_, ServerLevel p_307641_, BlockPos p_307339_, RandomSource p_307565_) {
        this.dispenseFrom(p_307471_, p_307641_, p_307339_);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level p_307308_, BlockState p_307639_, BlockEntityType<T> p_307651_) {
        return p_307308_.isClientSide ? null : createTickerHelper(p_307651_, BlockEntityType.CRAFTER, CrafterBlockEntity::serverTick);
    }

    private void setBlockEntityTriggered(@Nullable BlockEntity p_307610_, boolean p_307577_) {
        if (p_307610_ instanceof CrafterBlockEntity crafterblockentity) {
            crafterblockentity.setTriggered(p_307577_);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos p_307381_, BlockState p_307601_) {
        CrafterBlockEntity crafterblockentity = new CrafterBlockEntity(p_307381_, p_307601_);
        crafterblockentity.setTriggered(p_307601_.hasProperty(TRIGGERED) && p_307601_.getValue(TRIGGERED));
        return crafterblockentity;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_307251_) {
        Direction direction = p_307251_.getNearestLookingDirection().getOpposite();

        Direction direction1 = switch (direction) {
            case DOWN -> p_307251_.getHorizontalDirection().getOpposite();
            case UP -> p_307251_.getHorizontalDirection();
            case NORTH, SOUTH, WEST, EAST -> Direction.UP;
        };
        return this.defaultBlockState()
            .setValue(ORIENTATION, FrontAndTop.fromFrontAndTop(direction, direction1))
            .setValue(TRIGGERED, Boolean.valueOf(p_307251_.getLevel().hasNeighborSignal(p_307251_.getClickedPos())));
    }

    @Override
    public void setPlacedBy(Level p_307219_, BlockPos p_307681_, BlockState p_307383_, LivingEntity p_307647_, ItemStack p_307190_) {
        if (p_307383_.getValue(TRIGGERED)) {
            p_307219_.scheduleTick(p_307681_, this, 4);
        }
    }

    @Override
    protected void onRemove(BlockState p_307425_, Level p_307460_, BlockPos p_307342_, BlockState p_307466_, boolean p_307349_) {
        Containers.dropContentsOnDestroy(p_307425_, p_307466_, p_307460_, p_307342_);
        super.onRemove(p_307425_, p_307460_, p_307342_, p_307466_, p_307349_);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_307454_, Level p_307255_, BlockPos p_307303_, Player p_307670_, BlockHitResult p_307546_) {
        if (p_307255_.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockEntity blockentity = p_307255_.getBlockEntity(p_307303_);
            if (blockentity instanceof CrafterBlockEntity) {
                p_307670_.openMenu((CrafterBlockEntity)blockentity);
            }

            return InteractionResult.CONSUME;
        }
    }

    protected void dispenseFrom(BlockState p_307495_, ServerLevel p_307310_, BlockPos p_307672_) {
        if (p_307310_.getBlockEntity(p_307672_) instanceof CrafterBlockEntity crafterblockentity) {
            CraftingInput craftinginput = crafterblockentity.asCraftInput();
            Optional<RecipeHolder<CraftingRecipe>> optional = getPotentialResults(p_307310_, craftinginput);
            if (optional.isEmpty()) {
                p_307310_.levelEvent(1050, p_307672_, 0);
            } else {
                RecipeHolder<CraftingRecipe> recipeholder = optional.get();
                ItemStack itemstack = recipeholder.value().assemble(craftinginput, p_307310_.registryAccess());
                if (itemstack.isEmpty()) {
                    p_307310_.levelEvent(1050, p_307672_, 0);
                } else {
                    crafterblockentity.setCraftingTicksRemaining(6);
                    p_307310_.setBlock(p_307672_, p_307495_.setValue(CRAFTING, Boolean.valueOf(true)), 2);
                    itemstack.onCraftedBySystem(p_307310_);
                    this.dispenseItem(p_307310_, p_307672_, crafterblockentity, itemstack, p_307495_, recipeholder);

                    for (ItemStack itemstack1 : recipeholder.value().getRemainingItems(craftinginput)) {
                        if (!itemstack1.isEmpty()) {
                            this.dispenseItem(p_307310_, p_307672_, crafterblockentity, itemstack1, p_307495_, recipeholder);
                        }
                    }

                    crafterblockentity.getItems().forEach(p_307295_ -> {
                        if (!p_307295_.isEmpty()) {
                            p_307295_.shrink(1);
                        }
                    });
                    crafterblockentity.setChanged();
                }
            }
        }
    }

    public static Optional<RecipeHolder<CraftingRecipe>> getPotentialResults(Level p_307625_, CraftingInput p_345096_) {
        return RECIPE_CACHE.get(p_307625_, p_345096_);
    }

    private void dispenseItem(
        ServerLevel p_335887_,
        BlockPos p_307620_,
        CrafterBlockEntity p_307387_,
        ItemStack p_307296_,
        BlockState p_307501_,
        RecipeHolder<CraftingRecipe> p_335494_
    ) {
        Direction direction = p_307501_.getValue(ORIENTATION).front();
        Container container = HopperBlockEntity.getContainerAt(p_335887_, p_307620_.relative(direction));
        ItemStack itemstack = p_307296_.copy();
        if (container != null && (container instanceof CrafterBlockEntity || p_307296_.getCount() > container.getMaxStackSize(p_307296_))) {
            while (!itemstack.isEmpty()) {
                ItemStack itemstack2 = itemstack.copyWithCount(1);
                ItemStack itemstack1 = HopperBlockEntity.addItem(p_307387_, container, itemstack2, direction.getOpposite());
                if (!itemstack1.isEmpty()) {
                    break;
                }

                itemstack.shrink(1);
            }
        } else if (container != null) {
            while (!itemstack.isEmpty()) {
                int i = itemstack.getCount();
                itemstack = HopperBlockEntity.addItem(p_307387_, container, itemstack, direction.getOpposite());
                if (i == itemstack.getCount()) {
                    break;
                }
            }
        } else {
            itemstack = net.neoforged.neoforge.items.VanillaInventoryCodeHooks.insertCrafterOutput(p_335887_, p_307620_, p_307387_, itemstack);
        }

        if (!itemstack.isEmpty()) {
            Vec3 vec3 = Vec3.atCenterOf(p_307620_);
            Vec3 vec31 = vec3.relative(direction, 0.7);
            DefaultDispenseItemBehavior.spawnItem(p_335887_, itemstack, 6, direction, vec31);

            for (ServerPlayer serverplayer : p_335887_.getEntitiesOfClass(ServerPlayer.class, AABB.ofSize(vec3, 17.0, 17.0, 17.0))) {
                CriteriaTriggers.CRAFTER_RECIPE_CRAFTED.trigger(serverplayer, p_335494_.id(), p_307387_.getItems());
            }

            p_335887_.levelEvent(1049, p_307620_, 0);
            p_335887_.levelEvent(2010, p_307620_, direction.get3DDataValue());
        }
    }

    @Override
    protected RenderShape getRenderShape(BlockState p_307427_) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState p_307240_, Rotation p_307431_) {
        return p_307240_.setValue(ORIENTATION, p_307431_.rotation().rotate(p_307240_.getValue(ORIENTATION)));
    }

    @Override
    protected BlockState mirror(BlockState p_307514_, Mirror p_307198_) {
        return p_307514_.setValue(ORIENTATION, p_307198_.rotation().rotate(p_307514_.getValue(ORIENTATION)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_307200_) {
        p_307200_.add(ORIENTATION, TRIGGERED, CRAFTING);
    }
}
