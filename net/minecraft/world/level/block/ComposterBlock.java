package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ComposterBlock extends Block implements WorldlyContainerHolder {
    public static final MapCodec<ComposterBlock> CODEC = simpleCodec(ComposterBlock::new);
    public static final int READY = 8;
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 7;
    public static final IntegerProperty LEVEL = BlockStateProperties.LEVEL_COMPOSTER;
    /** @deprecated Neo: Use the {@link net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps#COMPOSTABLES compostable} data map instead */
    @Deprecated
    public static final Object2FloatMap<ItemLike> COMPOSTABLES = new Object2FloatOpenHashMap<>();
    private static final int AABB_SIDE_THICKNESS = 2;
    private static final VoxelShape OUTER_SHAPE = Shapes.block();
    private static final VoxelShape[] SHAPES = Util.make(new VoxelShape[9], p_51967_ -> {
        for (int i = 0; i < 8; i++) {
            p_51967_[i] = Shapes.join(OUTER_SHAPE, Block.box(2.0, (double)Math.max(2, 1 + i * 2), 2.0, 14.0, 16.0, 14.0), BooleanOp.ONLY_FIRST);
        }

        p_51967_[8] = p_51967_[7];
    });

    @Override
    public MapCodec<ComposterBlock> codec() {
        return CODEC;
    }

    public static void bootStrap() {
        COMPOSTABLES.defaultReturnValue(-1.0F);
        float f = 0.3F;
        float f1 = 0.5F;
        float f2 = 0.65F;
        float f3 = 0.85F;
        float f4 = 1.0F;
        add(0.3F, Items.JUNGLE_LEAVES);
        add(0.3F, Items.OAK_LEAVES);
        add(0.3F, Items.SPRUCE_LEAVES);
        add(0.3F, Items.DARK_OAK_LEAVES);
        add(0.3F, Items.ACACIA_LEAVES);
        add(0.3F, Items.CHERRY_LEAVES);
        add(0.3F, Items.BIRCH_LEAVES);
        add(0.3F, Items.AZALEA_LEAVES);
        add(0.3F, Items.MANGROVE_LEAVES);
        add(0.3F, Items.OAK_SAPLING);
        add(0.3F, Items.SPRUCE_SAPLING);
        add(0.3F, Items.BIRCH_SAPLING);
        add(0.3F, Items.JUNGLE_SAPLING);
        add(0.3F, Items.ACACIA_SAPLING);
        add(0.3F, Items.CHERRY_SAPLING);
        add(0.3F, Items.DARK_OAK_SAPLING);
        add(0.3F, Items.MANGROVE_PROPAGULE);
        add(0.3F, Items.BEETROOT_SEEDS);
        add(0.3F, Items.DRIED_KELP);
        add(0.3F, Items.SHORT_GRASS);
        add(0.3F, Items.KELP);
        add(0.3F, Items.MELON_SEEDS);
        add(0.3F, Items.PUMPKIN_SEEDS);
        add(0.3F, Items.SEAGRASS);
        add(0.3F, Items.SWEET_BERRIES);
        add(0.3F, Items.GLOW_BERRIES);
        add(0.3F, Items.WHEAT_SEEDS);
        add(0.3F, Items.MOSS_CARPET);
        add(0.3F, Items.PINK_PETALS);
        add(0.3F, Items.SMALL_DRIPLEAF);
        add(0.3F, Items.HANGING_ROOTS);
        add(0.3F, Items.MANGROVE_ROOTS);
        add(0.3F, Items.TORCHFLOWER_SEEDS);
        add(0.3F, Items.PITCHER_POD);
        add(0.5F, Items.DRIED_KELP_BLOCK);
        add(0.5F, Items.TALL_GRASS);
        add(0.5F, Items.FLOWERING_AZALEA_LEAVES);
        add(0.5F, Items.CACTUS);
        add(0.5F, Items.SUGAR_CANE);
        add(0.5F, Items.VINE);
        add(0.5F, Items.NETHER_SPROUTS);
        add(0.5F, Items.WEEPING_VINES);
        add(0.5F, Items.TWISTING_VINES);
        add(0.5F, Items.MELON_SLICE);
        add(0.5F, Items.GLOW_LICHEN);
        add(0.65F, Items.SEA_PICKLE);
        add(0.65F, Items.LILY_PAD);
        add(0.65F, Items.PUMPKIN);
        add(0.65F, Items.CARVED_PUMPKIN);
        add(0.65F, Items.MELON);
        add(0.65F, Items.APPLE);
        add(0.65F, Items.BEETROOT);
        add(0.65F, Items.CARROT);
        add(0.65F, Items.COCOA_BEANS);
        add(0.65F, Items.POTATO);
        add(0.65F, Items.WHEAT);
        add(0.65F, Items.BROWN_MUSHROOM);
        add(0.65F, Items.RED_MUSHROOM);
        add(0.65F, Items.MUSHROOM_STEM);
        add(0.65F, Items.CRIMSON_FUNGUS);
        add(0.65F, Items.WARPED_FUNGUS);
        add(0.65F, Items.NETHER_WART);
        add(0.65F, Items.CRIMSON_ROOTS);
        add(0.65F, Items.WARPED_ROOTS);
        add(0.65F, Items.SHROOMLIGHT);
        add(0.65F, Items.DANDELION);
        add(0.65F, Items.POPPY);
        add(0.65F, Items.BLUE_ORCHID);
        add(0.65F, Items.ALLIUM);
        add(0.65F, Items.AZURE_BLUET);
        add(0.65F, Items.RED_TULIP);
        add(0.65F, Items.ORANGE_TULIP);
        add(0.65F, Items.WHITE_TULIP);
        add(0.65F, Items.PINK_TULIP);
        add(0.65F, Items.OXEYE_DAISY);
        add(0.65F, Items.CORNFLOWER);
        add(0.65F, Items.LILY_OF_THE_VALLEY);
        add(0.65F, Items.WITHER_ROSE);
        add(0.65F, Items.FERN);
        add(0.65F, Items.SUNFLOWER);
        add(0.65F, Items.LILAC);
        add(0.65F, Items.ROSE_BUSH);
        add(0.65F, Items.PEONY);
        add(0.65F, Items.LARGE_FERN);
        add(0.65F, Items.SPORE_BLOSSOM);
        add(0.65F, Items.AZALEA);
        add(0.65F, Items.MOSS_BLOCK);
        add(0.65F, Items.BIG_DRIPLEAF);
        add(0.85F, Items.HAY_BLOCK);
        add(0.85F, Items.BROWN_MUSHROOM_BLOCK);
        add(0.85F, Items.RED_MUSHROOM_BLOCK);
        add(0.85F, Items.NETHER_WART_BLOCK);
        add(0.85F, Items.WARPED_WART_BLOCK);
        add(0.85F, Items.FLOWERING_AZALEA);
        add(0.85F, Items.BREAD);
        add(0.85F, Items.BAKED_POTATO);
        add(0.85F, Items.COOKIE);
        add(0.85F, Items.TORCHFLOWER);
        add(0.85F, Items.PITCHER_PLANT);
        add(1.0F, Items.CAKE);
        add(1.0F, Items.PUMPKIN_PIE);
    }

    private static void add(float p_51921_, ItemLike p_51922_) {
        COMPOSTABLES.put(p_51922_.asItem(), p_51921_);
    }

    public ComposterBlock(BlockBehaviour.Properties p_51919_) {
        super(p_51919_);
        this.registerDefaultState(this.stateDefinition.any().setValue(LEVEL, Integer.valueOf(0)));
    }

    public static void handleFill(Level p_51924_, BlockPos p_51925_, boolean p_51926_) {
        BlockState blockstate = p_51924_.getBlockState(p_51925_);
        p_51924_.playLocalSound(p_51925_, p_51926_ ? SoundEvents.COMPOSTER_FILL_SUCCESS : SoundEvents.COMPOSTER_FILL, SoundSource.BLOCKS, 1.0F, 1.0F, false);
        double d0 = blockstate.getShape(p_51924_, p_51925_).max(Direction.Axis.Y, 0.5, 0.5) + 0.03125;
        double d1 = 0.13125F;
        double d2 = 0.7375F;
        RandomSource randomsource = p_51924_.getRandom();

        for (int i = 0; i < 10; i++) {
            double d3 = randomsource.nextGaussian() * 0.02;
            double d4 = randomsource.nextGaussian() * 0.02;
            double d5 = randomsource.nextGaussian() * 0.02;
            p_51924_.addParticle(
                ParticleTypes.COMPOSTER,
                (double)p_51925_.getX() + 0.13125F + 0.7375F * (double)randomsource.nextFloat(),
                (double)p_51925_.getY() + d0 + (double)randomsource.nextFloat() * (1.0 - d0),
                (double)p_51925_.getZ() + 0.13125F + 0.7375F * (double)randomsource.nextFloat(),
                d3,
                d4,
                d5
            );
        }
    }

    @Override
    protected VoxelShape getShape(BlockState p_51973_, BlockGetter p_51974_, BlockPos p_51975_, CollisionContext p_51976_) {
        return SHAPES[p_51973_.getValue(LEVEL)];
    }

    @Override
    protected VoxelShape getInteractionShape(BlockState p_51969_, BlockGetter p_51970_, BlockPos p_51971_) {
        return OUTER_SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState p_51990_, BlockGetter p_51991_, BlockPos p_51992_, CollisionContext p_51993_) {
        return SHAPES[0];
    }

    @Override
    protected void onPlace(BlockState p_51978_, Level p_51979_, BlockPos p_51980_, BlockState p_51981_, boolean p_51982_) {
        if (p_51978_.getValue(LEVEL) == 7) {
            p_51979_.scheduleTick(p_51980_, p_51978_.getBlock(), 20);
        }
        // Neo: Invalidate composter capabilities when a composter is added
        if (!p_51981_.is(this)) p_51979_.invalidateCapabilities(p_51980_);
    }

    @Override
    protected void onRemove(BlockState p_60515_, Level p_60516_, BlockPos p_60517_, BlockState p_60518_, boolean p_60519_) {
        super.onRemove(p_60515_, p_60516_, p_60517_, p_60518_, p_60519_);
        // Neo: Invalidate composter capabilities when a composter is removed
        if (!p_60515_.is(p_60518_.getBlock())) p_60516_.invalidateCapabilities(p_60517_);
    }

    @Override
    protected ItemInteractionResult useItemOn(
        ItemStack p_316332_, BlockState p_316118_, Level p_316624_, BlockPos p_316660_, Player p_316715_, InteractionHand p_316472_, BlockHitResult p_316606_
    ) {
        int i = p_316118_.getValue(LEVEL);
        if (i < 8 && getValue(p_316332_) > 0) {
            if (i < 7 && !p_316624_.isClientSide) {
                BlockState blockstate = addItem(p_316715_, p_316118_, p_316624_, p_316660_, p_316332_);
                p_316624_.levelEvent(1500, p_316660_, p_316118_ != blockstate ? 1 : 0);
                p_316715_.awardStat(Stats.ITEM_USED.get(p_316332_.getItem()));
                p_316332_.consume(1, p_316715_);
            }

            return ItemInteractionResult.sidedSuccess(p_316624_.isClientSide);
        } else {
            return super.useItemOn(p_316332_, p_316118_, p_316624_, p_316660_, p_316715_, p_316472_, p_316606_);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState p_316361_, Level p_316271_, BlockPos p_316647_, Player p_316633_, BlockHitResult p_316555_) {
        int i = p_316361_.getValue(LEVEL);
        if (i == 8) {
            extractProduce(p_316633_, p_316361_, p_316271_, p_316647_);
            return InteractionResult.sidedSuccess(p_316271_.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    public static BlockState insertItem(Entity p_270919_, BlockState p_270087_, ServerLevel p_270284_, ItemStack p_270253_, BlockPos p_270678_) {
        int i = p_270087_.getValue(LEVEL);
        if (i < 7 && getValue(p_270253_) > 0) {
            BlockState blockstate = addItem(p_270919_, p_270087_, p_270284_, p_270678_, p_270253_);
            p_270253_.shrink(1);
            return blockstate;
        } else {
            return p_270087_;
        }
    }

    public static BlockState extractProduce(Entity p_270467_, BlockState p_51999_, Level p_52000_, BlockPos p_52001_) {
        if (!p_52000_.isClientSide) {
            Vec3 vec3 = Vec3.atLowerCornerWithOffset(p_52001_, 0.5, 1.01, 0.5).offsetRandom(p_52000_.random, 0.7F);
            ItemEntity itementity = new ItemEntity(p_52000_, vec3.x(), vec3.y(), vec3.z(), new ItemStack(Items.BONE_MEAL));
            itementity.setDefaultPickUpDelay();
            p_52000_.addFreshEntity(itementity);
        }

        BlockState blockstate = empty(p_270467_, p_51999_, p_52000_, p_52001_);
        p_52000_.playSound(null, p_52001_, SoundEvents.COMPOSTER_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        return blockstate;
    }

    static BlockState empty(@Nullable Entity p_270236_, BlockState p_270873_, LevelAccessor p_270963_, BlockPos p_270211_) {
        BlockState blockstate = p_270873_.setValue(LEVEL, Integer.valueOf(0));
        p_270963_.setBlock(p_270211_, blockstate, 3);
        p_270963_.gameEvent(GameEvent.BLOCK_CHANGE, p_270211_, GameEvent.Context.of(p_270236_, blockstate));
        return blockstate;
    }

    static BlockState addItem(@Nullable Entity p_270464_, BlockState p_270603_, LevelAccessor p_270151_, BlockPos p_270547_, ItemStack p_270354_) {
        int i = p_270603_.getValue(LEVEL);
        float f = getValue(p_270354_);
        if ((i != 0 || !(f > 0.0F)) && !(p_270151_.getRandom().nextDouble() < (double)f)) {
            return p_270603_;
        } else {
            int j = i + 1;
            BlockState blockstate = p_270603_.setValue(LEVEL, Integer.valueOf(j));
            p_270151_.setBlock(p_270547_, blockstate, 3);
            p_270151_.gameEvent(GameEvent.BLOCK_CHANGE, p_270547_, GameEvent.Context.of(p_270464_, blockstate));
            if (j == 7) {
                p_270151_.scheduleTick(p_270547_, p_270603_.getBlock(), 20);
            }

            return blockstate;
        }
    }

    @Override
    protected void tick(BlockState p_221015_, ServerLevel p_221016_, BlockPos p_221017_, RandomSource p_221018_) {
        if (p_221015_.getValue(LEVEL) == 7) {
            p_221016_.setBlock(p_221017_, p_221015_.cycle(LEVEL), 3);
            p_221016_.playSound(null, p_221017_, SoundEvents.COMPOSTER_READY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState p_51928_) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState p_51945_, Level p_51946_, BlockPos p_51947_) {
        return p_51945_.getValue(LEVEL);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> p_51965_) {
        p_51965_.add(LEVEL);
    }

    @Override
    protected boolean isPathfindable(BlockState p_51940_, PathComputationType p_51943_) {
        return false;
    }

    @Override
    public WorldlyContainer getContainer(BlockState p_51956_, LevelAccessor p_51957_, BlockPos p_51958_) {
        int i = p_51956_.getValue(LEVEL);
        if (i == 8) {
            return new ComposterBlock.OutputContainer(p_51956_, p_51957_, p_51958_, new ItemStack(Items.BONE_MEAL));
        } else {
            return (WorldlyContainer)(i < 7 ? new ComposterBlock.InputContainer(p_51956_, p_51957_, p_51958_) : new ComposterBlock.EmptyContainer());
        }
    }

    static class EmptyContainer extends SimpleContainer implements WorldlyContainer {
        public EmptyContainer() {
            super(0);
        }

        @Override
        public int[] getSlotsForFace(Direction p_52012_) {
            return new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int p_52008_, ItemStack p_52009_, @Nullable Direction p_52010_) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int p_52014_, ItemStack p_52015_, Direction p_52016_) {
            return false;
        }
    }

    static class InputContainer extends SimpleContainer implements WorldlyContainer {
        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public InputContainer(BlockState p_52022_, LevelAccessor p_52023_, BlockPos p_52024_) {
            super(1);
            this.state = p_52022_;
            this.level = p_52023_;
            this.pos = p_52024_;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction p_52032_) {
            return p_52032_ == Direction.UP ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int p_52028_, ItemStack p_52029_, @Nullable Direction p_52030_) {
            return !this.changed && p_52030_ == Direction.UP && getValue(p_52029_) > 0f;
        }

        @Override
        public boolean canTakeItemThroughFace(int p_52034_, ItemStack p_52035_, Direction p_52036_) {
            return false;
        }

        @Override
        public void setChanged() {
            ItemStack itemstack = this.getItem(0);
            if (!itemstack.isEmpty()) {
                this.changed = true;
                BlockState blockstate = ComposterBlock.addItem(null, this.state, this.level, this.pos, itemstack);
                this.level.levelEvent(1500, this.pos, blockstate != this.state ? 1 : 0);
                this.removeItemNoUpdate(0);
            }
        }
    }

    static class OutputContainer extends SimpleContainer implements WorldlyContainer {
        private final BlockState state;
        private final LevelAccessor level;
        private final BlockPos pos;
        private boolean changed;

        public OutputContainer(BlockState p_52042_, LevelAccessor p_52043_, BlockPos p_52044_, ItemStack p_52045_) {
            super(p_52045_);
            this.state = p_52042_;
            this.level = p_52043_;
            this.pos = p_52044_;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int[] getSlotsForFace(Direction p_52053_) {
            return p_52053_ == Direction.DOWN ? new int[]{0} : new int[0];
        }

        @Override
        public boolean canPlaceItemThroughFace(int p_52049_, ItemStack p_52050_, @Nullable Direction p_52051_) {
            return false;
        }

        @Override
        public boolean canTakeItemThroughFace(int p_52055_, ItemStack p_52056_, Direction p_52057_) {
            return !this.changed && p_52057_ == Direction.DOWN && p_52056_.is(Items.BONE_MEAL);
        }

        @Override
        public void setChanged() {
            ComposterBlock.empty(null, this.state, this.level, this.pos);
            this.changed = true;
        }
    }

    public static float getValue(ItemStack item) {
        var value = item.getItemHolder().getData(net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps.COMPOSTABLES);
        if (value != null) return value.chance();
        return -1f;
    }
}
