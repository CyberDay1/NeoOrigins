package net.minecraft.world.level.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class FlowerPotBlock extends Block {
    public static final MapCodec<FlowerPotBlock> CODEC = RecordCodecBuilder.mapCodec(
        p_344655_ -> p_344655_.group(BuiltInRegistries.BLOCK.byNameCodec().fieldOf("potted").forGetter(p_304928_ -> p_304928_.potted), propertiesCodec())
                .apply(p_344655_, FlowerPotBlock::new)
    );
    private static final Map<Block, Block> POTTED_BY_CONTENT = Maps.newHashMap();
    public static final float AABB_SIZE = 3.0F;
    protected static final VoxelShape SHAPE = Block.box(5.0, 0.0, 5.0, 11.0, 6.0, 11.0);
    /** Neo: Field accesses are redirected to {@link #getPotted()} with a coremod. */
    private final Block potted;

    @Override
    public MapCodec<FlowerPotBlock> codec() {
        return CODEC;
    }

    @Deprecated // Mods should use the constructor below
    public FlowerPotBlock(Block p_53528_, BlockBehaviour.Properties p_53529_) {
        this(Blocks.FLOWER_POT == null ? null : () -> (FlowerPotBlock) Blocks.FLOWER_POT, () -> p_53528_, p_53529_);
        if (Blocks.FLOWER_POT != null) {
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(p_53528_), () -> this);
        }
    }

    /**
     * For mod use, eliminates the need to extend this class, and prevents modded
     * flower pots from altering vanilla behavior.
     *
     * @param emptyPot The empty pot for this pot, or null for self.
     */
    public FlowerPotBlock(@org.jetbrains.annotations.Nullable java.util.function.Supplier<FlowerPotBlock> emptyPot, java.util.function.Supplier<? extends Block> p_53528_, BlockBehaviour.Properties properties) {
        super(properties);
        this.potted = null; // Unused, redirected by coremod
        this.flowerDelegate = p_53528_;
        if (emptyPot == null) {
            this.fullPots = Maps.newHashMap();
            this.emptyPot = null;
        } else {
            this.fullPots = java.util.Collections.emptyMap();
            this.emptyPot = emptyPot;
        }
    }

    @Override
    protected VoxelShape getShape(BlockState p_53556_, BlockGetter p_53557_, BlockPos p_53558_, CollisionContext p_53559_) {
        return SHAPE;
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316610_, BlockState p_316240_, Level p_316456_, BlockPos p_316502_, Player p_316491_, InteractionHand p_316444_, BlockHitResult p_316826_
    ) {
        BlockState blockstate = (p_316610_.getItem() instanceof BlockItem blockitem
                ? getEmptyPot().fullPots.getOrDefault(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(blockitem.getBlock()), () -> Blocks.AIR).get()
                : Blocks.AIR)
            .defaultBlockState();
        if (blockstate.isAir()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (!this.isEmpty()) {
            return ItemInteractionResult.CONSUME;
        } else {
            p_316456_.setBlock(p_316502_, blockstate, 3);
            p_316456_.gameEvent(p_316491_, GameEvent.BLOCK_CHANGE, p_316502_);
            p_316491_.awardStat(Stats.POT_FLOWER);
            p_316610_.consume(1, p_316491_);
            return ItemInteractionResult.sidedSuccess(p_316456_.isClientSide);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316363_, Level p_316655_, BlockPos p_316654_, Player p_316338_, BlockHitResult p_316518_) {
        if (this.isEmpty()) {
            return InteractionResult.CONSUME;
        } else {
            ItemStack itemstack = new ItemStack(this.potted);
            if (!p_316338_.addItem(itemstack)) {
                p_316338_.drop(itemstack, false);
            }

            p_316655_.setBlock(p_316654_, getEmptyPot().defaultBlockState(), 3);
            p_316655_.gameEvent(p_316338_, GameEvent.BLOCK_CHANGE, p_316654_);
            return InteractionResult.sidedSuccess(p_316655_.isClientSide);
        }
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader p_304505_, BlockPos p_53532_, BlockState p_53533_) {
        return this.isEmpty() ? super.getCloneItemStack(p_304505_, p_53532_, p_53533_) : new ItemStack(this.potted);
    }

    private boolean isEmpty() {
        return this.potted == Blocks.AIR;
    }

    @Override
    protected BlockState updateShape(BlockState p_53547_, Direction p_53548_, BlockState p_53549_, LevelAccessor p_53550_, BlockPos p_53551_, BlockPos p_53552_) {
        return p_53548_ == Direction.DOWN && !p_53547_.canSurvive(p_53550_, p_53551_)
            ? Blocks.AIR.defaultBlockState()
            : super.updateShape(p_53547_, p_53548_, p_53549_, p_53550_, p_53551_, p_53552_);
    }

    public Block getPotted() {
        return flowerDelegate.get();
    }

    @Override
    protected boolean isPathfindable(BlockState p_53535_, PathComputationType p_53538_) {
        return false;
    }

    // Neo: Maps flower blocks to the filled flower pot equivalent
    private final Map<net.minecraft.resources.ResourceLocation, java.util.function.Supplier<? extends Block>> fullPots;

    @org.jetbrains.annotations.Nullable
    private final java.util.function.Supplier<FlowerPotBlock> emptyPot;

    private final java.util.function.Supplier<? extends Block> flowerDelegate;

    public FlowerPotBlock getEmptyPot() {
         return emptyPot == null ? this : emptyPot.get();
    }

    /**
     * Maps the given flower to the filled pot it is for.
     * Call this on the empty pot block. Attempting to call this on a filled pot will throw an exception.
     *
     * @param flower The ResourceLocation of the flower block. Not flower item
     * @param fullPot The filled flower pot to map the flower block to
     */
    public void addPlant(net.minecraft.resources.ResourceLocation flower, java.util.function.Supplier<? extends Block> fullPot) {
         if (getEmptyPot() != this) {
              throw new IllegalArgumentException("Cannot add plant to non-empty pot: " + this + " (Please call addPlant on the empty pot instead)");
         }
         fullPots.put(flower, fullPot);
    }

    /**
     * Returns all the filled pots that can be spawned from filling this pot. (If this pot is filled, returned map will be empty)
     */
    public Map<net.minecraft.resources.ResourceLocation, java.util.function.Supplier<? extends Block>> getFullPotsView() {
        return java.util.Collections.unmodifiableMap(fullPots);
    }
}
