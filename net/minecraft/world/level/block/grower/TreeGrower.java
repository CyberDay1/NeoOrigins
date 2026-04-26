package net.minecraft.world.level.block.grower;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

public final class TreeGrower {
    private static final Map<String, TreeGrower> GROWERS = new Object2ObjectArrayMap<>();
    public static final Codec<TreeGrower> CODEC = Codec.stringResolver(p_304625_ -> p_304625_.name, GROWERS::get);
    public static final TreeGrower OAK = new TreeGrower(
        "oak",
        0.1F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.OAK),
        Optional.of(TreeFeatures.FANCY_OAK),
        Optional.of(TreeFeatures.OAK_BEES_005),
        Optional.of(TreeFeatures.FANCY_OAK_BEES_005)
    );
    public static final TreeGrower SPRUCE = new TreeGrower(
        "spruce",
        0.5F,
        Optional.of(TreeFeatures.MEGA_SPRUCE),
        Optional.of(TreeFeatures.MEGA_PINE),
        Optional.of(TreeFeatures.SPRUCE),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower MANGROVE = new TreeGrower(
        "mangrove",
        0.85F,
        Optional.empty(),
        Optional.empty(),
        Optional.of(TreeFeatures.MANGROVE),
        Optional.of(TreeFeatures.TALL_MANGROVE),
        Optional.empty(),
        Optional.empty()
    );
    public static final TreeGrower AZALEA = new TreeGrower("azalea", Optional.empty(), Optional.of(TreeFeatures.AZALEA_TREE), Optional.empty());
    public static final TreeGrower BIRCH = new TreeGrower("birch", Optional.empty(), Optional.of(TreeFeatures.BIRCH), Optional.of(TreeFeatures.BIRCH_BEES_005));
    public static final TreeGrower JUNGLE = new TreeGrower(
        "jungle", Optional.of(TreeFeatures.MEGA_JUNGLE_TREE), Optional.of(TreeFeatures.JUNGLE_TREE_NO_VINE), Optional.empty()
    );
    public static final TreeGrower ACACIA = new TreeGrower("acacia", Optional.empty(), Optional.of(TreeFeatures.ACACIA), Optional.empty());
    public static final TreeGrower CHERRY = new TreeGrower(
        "cherry", Optional.empty(), Optional.of(TreeFeatures.CHERRY), Optional.of(TreeFeatures.CHERRY_BEES_005)
    );
    public static final TreeGrower DARK_OAK = new TreeGrower("dark_oak", Optional.of(TreeFeatures.DARK_OAK), Optional.empty(), Optional.empty());
    private final String name;
    private final float secondaryChance;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> megaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryMegaTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> tree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryTree;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> flowers;
    private final Optional<ResourceKey<ConfiguredFeature<?, ?>>> secondaryFlowers;

    public TreeGrower(
        String p_304408_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304634_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304477_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304753_
    ) {
        this(p_304408_, 0.0F, p_304634_, Optional.empty(), p_304477_, Optional.empty(), p_304753_, Optional.empty());
    }

    public TreeGrower(
        String p_304522_,
        float p_304600_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304738_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304561_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304433_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304821_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304558_,
        Optional<ResourceKey<ConfiguredFeature<?, ?>>> p_304488_
    ) {
        this.name = p_304522_;
        this.secondaryChance = p_304600_;
        this.megaTree = p_304738_;
        this.secondaryMegaTree = p_304561_;
        this.tree = p_304433_;
        this.secondaryTree = p_304821_;
        this.flowers = p_304558_;
        this.secondaryFlowers = p_304488_;
        GROWERS.put(p_304522_, this);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource p_304525_, boolean p_304410_) {
        if (p_304525_.nextFloat() < this.secondaryChance) {
            if (p_304410_ && this.secondaryFlowers.isPresent()) {
                return this.secondaryFlowers.get();
            }

            if (this.secondaryTree.isPresent()) {
                return this.secondaryTree.get();
            }
        }

        return p_304410_ && this.flowers.isPresent() ? this.flowers.get() : this.tree.orElse(null);
    }

    @Nullable
    private ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource p_304575_) {
        return this.secondaryMegaTree.isPresent() && p_304575_.nextFloat() < this.secondaryChance ? this.secondaryMegaTree.get() : this.megaTree.orElse(null);
    }

    public boolean growTree(ServerLevel p_304396_, ChunkGenerator p_304672_, BlockPos p_304643_, BlockState p_304439_, RandomSource p_304893_) {
        ResourceKey<ConfiguredFeature<?, ?>> resourcekey = this.getConfiguredMegaFeature(p_304893_);
        if (resourcekey != null) {
            Holder<ConfiguredFeature<?, ?>> holder = p_304396_.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(resourcekey)
                .orElse(null);
            var event = net.neoforged.neoforge.event.EventHooks.fireBlockGrowFeature(p_304396_, p_304893_, p_304643_, holder);
            holder = event.getFeature();
            if (event.isCanceled()) return false;
            if (holder != null) {
                for (int i = 0; i >= -1; i--) {
                    for (int j = 0; j >= -1; j--) {
                        if (isTwoByTwoSapling(p_304439_, p_304396_, p_304643_, i, j)) {
                            ConfiguredFeature<?, ?> configuredfeature = holder.value();
                            BlockState blockstate = Blocks.AIR.defaultBlockState();
                            p_304396_.setBlock(p_304643_.offset(i, 0, j), blockstate, 4);
                            p_304396_.setBlock(p_304643_.offset(i + 1, 0, j), blockstate, 4);
                            p_304396_.setBlock(p_304643_.offset(i, 0, j + 1), blockstate, 4);
                            p_304396_.setBlock(p_304643_.offset(i + 1, 0, j + 1), blockstate, 4);
                            if (configuredfeature.place(p_304396_, p_304672_, p_304893_, p_304643_.offset(i, 0, j))) {
                                return true;
                            }

                            p_304396_.setBlock(p_304643_.offset(i, 0, j), p_304439_, 4);
                            p_304396_.setBlock(p_304643_.offset(i + 1, 0, j), p_304439_, 4);
                            p_304396_.setBlock(p_304643_.offset(i, 0, j + 1), p_304439_, 4);
                            p_304396_.setBlock(p_304643_.offset(i + 1, 0, j + 1), p_304439_, 4);
                            return false;
                        }
                    }
                }
            }
        }

        ResourceKey<ConfiguredFeature<?, ?>> resourcekey1 = this.getConfiguredFeature(p_304893_, this.hasFlowers(p_304396_, p_304643_));
        if (resourcekey1 == null) {
            return false;
        } else {
            Holder<ConfiguredFeature<?, ?>> holder1 = p_304396_.registryAccess()
                .registryOrThrow(Registries.CONFIGURED_FEATURE)
                .getHolder(resourcekey1)
                .orElse(null);
            var event = net.neoforged.neoforge.event.EventHooks.fireBlockGrowFeature(p_304396_, p_304893_, p_304643_, holder1);
            holder1 = event.getFeature();
            if (event.isCanceled()) return false;
            if (holder1 == null) {
                return false;
            } else {
                ConfiguredFeature<?, ?> configuredfeature1 = holder1.value();
                BlockState blockstate1 = p_304396_.getFluidState(p_304643_).createLegacyBlock();
                p_304396_.setBlock(p_304643_, blockstate1, 4);
                if (configuredfeature1.place(p_304396_, p_304672_, p_304893_, p_304643_)) {
                    if (p_304396_.getBlockState(p_304643_) == blockstate1) {
                        p_304396_.sendBlockUpdated(p_304643_, p_304439_, blockstate1, 2);
                    }

                    return true;
                } else {
                    p_304396_.setBlock(p_304643_, p_304439_, 4);
                    return false;
                }
            }
        }
    }

    private static boolean isTwoByTwoSapling(BlockState p_304497_, BlockGetter p_304772_, BlockPos p_304920_, int p_304941_, int p_304932_) {
        Block block = p_304497_.getBlock();
        return p_304772_.getBlockState(p_304920_.offset(p_304941_, 0, p_304932_)).is(block)
            && p_304772_.getBlockState(p_304920_.offset(p_304941_ + 1, 0, p_304932_)).is(block)
            && p_304772_.getBlockState(p_304920_.offset(p_304941_, 0, p_304932_ + 1)).is(block)
            && p_304772_.getBlockState(p_304920_.offset(p_304941_ + 1, 0, p_304932_ + 1)).is(block);
    }

    private boolean hasFlowers(LevelAccessor p_304555_, BlockPos p_304465_) {
        for (BlockPos blockpos : BlockPos.MutableBlockPos.betweenClosed(p_304465_.below().north(2).west(2), p_304465_.above().south(2).east(2))) {
            if (p_304555_.getBlockState(blockpos).is(BlockTags.FLOWERS)) {
                return true;
            }
        }

        return false;
    }
}
