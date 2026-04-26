package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.levelgen.structure.Structure;

public record LocationPredicate(
    Optional<LocationPredicate.PositionPredicate> position,
    Optional<HolderSet<Biome>> biomes,
    Optional<HolderSet<Structure>> structures,
    Optional<ResourceKey<Level>> dimension,
    Optional<Boolean> smokey,
    Optional<LightPredicate> light,
    Optional<BlockPredicate> block,
    Optional<FluidPredicate> fluid,
    Optional<Boolean> canSeeSky
) {
    public static final Codec<LocationPredicate> CODEC = RecordCodecBuilder.create(
        p_297907_ -> p_297907_.group(
                    LocationPredicate.PositionPredicate.CODEC.optionalFieldOf("position").forGetter(LocationPredicate::position),
                    RegistryCodecs.homogeneousList(Registries.BIOME).optionalFieldOf("biomes").forGetter(LocationPredicate::biomes),
                    RegistryCodecs.homogeneousList(Registries.STRUCTURE).optionalFieldOf("structures").forGetter(LocationPredicate::structures),
                    ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(LocationPredicate::dimension),
                    Codec.BOOL.optionalFieldOf("smokey").forGetter(LocationPredicate::smokey),
                    LightPredicate.CODEC.optionalFieldOf("light").forGetter(LocationPredicate::light),
                    BlockPredicate.CODEC.optionalFieldOf("block").forGetter(LocationPredicate::block),
                    FluidPredicate.CODEC.optionalFieldOf("fluid").forGetter(LocationPredicate::fluid),
                    Codec.BOOL.optionalFieldOf("can_see_sky").forGetter(LocationPredicate::canSeeSky)
                )
                .apply(p_297907_, LocationPredicate::new)
    );

    public boolean matches(ServerLevel p_52618_, double p_52619_, double p_52620_, double p_52621_) {
        if (this.position.isPresent() && !this.position.get().matches(p_52619_, p_52620_, p_52621_)) {
            return false;
        } else if (this.dimension.isPresent() && this.dimension.get() != p_52618_.dimension()) {
            return false;
        } else {
            BlockPos blockpos = BlockPos.containing(p_52619_, p_52620_, p_52621_);
            boolean flag = p_52618_.isLoaded(blockpos);
            if (!this.biomes.isPresent() || flag && this.biomes.get().contains(p_52618_.getBiome(blockpos))) {
                if (!this.structures.isPresent() || flag && p_52618_.structureManager().getStructureWithPieceAt(blockpos, this.structures.get()).isValid()) {
                    if (!this.smokey.isPresent() || flag && this.smokey.get() == CampfireBlock.isSmokeyPos(p_52618_, blockpos)) {
                        if (this.light.isPresent() && !this.light.get().matches(p_52618_, blockpos)) {
                            return false;
                        } else if (this.block.isPresent() && !this.block.get().matches(p_52618_, blockpos)) {
                            return false;
                        } else {
                            return this.fluid.isPresent() && !this.fluid.get().matches(p_52618_, blockpos)
                                ? false
                                : !this.canSeeSky.isPresent() || this.canSeeSky.get() == p_52618_.canSeeSky(blockpos);
                        }
                    } else {
                        return false;
                    }
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
    }

    public static class Builder {
        private MinMaxBounds.Doubles x = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles y = MinMaxBounds.Doubles.ANY;
        private MinMaxBounds.Doubles z = MinMaxBounds.Doubles.ANY;
        private Optional<HolderSet<Biome>> biomes = Optional.empty();
        private Optional<HolderSet<Structure>> structures = Optional.empty();
        private Optional<ResourceKey<Level>> dimension = Optional.empty();
        private Optional<Boolean> smokey = Optional.empty();
        private Optional<LightPredicate> light = Optional.empty();
        private Optional<BlockPredicate> block = Optional.empty();
        private Optional<FluidPredicate> fluid = Optional.empty();
        private Optional<Boolean> canSeeSky = Optional.empty();

        public static LocationPredicate.Builder location() {
            return new LocationPredicate.Builder();
        }

        public static LocationPredicate.Builder inBiome(Holder<Biome> p_332168_) {
            return location().setBiomes(HolderSet.direct(p_332168_));
        }

        public static LocationPredicate.Builder inDimension(ResourceKey<Level> p_298871_) {
            return location().setDimension(p_298871_);
        }

        public static LocationPredicate.Builder inStructure(Holder<Structure> p_330610_) {
            return location().setStructures(HolderSet.direct(p_330610_));
        }

        public static LocationPredicate.Builder atYLocation(MinMaxBounds.Doubles p_298783_) {
            return location().setY(p_298783_);
        }

        public LocationPredicate.Builder setX(MinMaxBounds.Doubles p_153971_) {
            this.x = p_153971_;
            return this;
        }

        public LocationPredicate.Builder setY(MinMaxBounds.Doubles p_153975_) {
            this.y = p_153975_;
            return this;
        }

        public LocationPredicate.Builder setZ(MinMaxBounds.Doubles p_153979_) {
            this.z = p_153979_;
            return this;
        }

        public LocationPredicate.Builder setBiomes(HolderSet<Biome> p_330686_) {
            this.biomes = Optional.of(p_330686_);
            return this;
        }

        public LocationPredicate.Builder setStructures(HolderSet<Structure> p_332189_) {
            this.structures = Optional.of(p_332189_);
            return this;
        }

        public LocationPredicate.Builder setDimension(ResourceKey<Level> p_153977_) {
            this.dimension = Optional.of(p_153977_);
            return this;
        }

        public LocationPredicate.Builder setLight(LightPredicate.Builder p_298888_) {
            this.light = Optional.of(p_298888_.build());
            return this;
        }

        public LocationPredicate.Builder setBlock(BlockPredicate.Builder p_298335_) {
            this.block = Optional.of(p_298335_.build());
            return this;
        }

        public LocationPredicate.Builder setFluid(FluidPredicate.Builder p_299307_) {
            this.fluid = Optional.of(p_299307_.build());
            return this;
        }

        public LocationPredicate.Builder setSmokey(boolean p_299155_) {
            this.smokey = Optional.of(p_299155_);
            return this;
        }

        public LocationPredicate.Builder setCanSeeSky(boolean p_345443_) {
            this.canSeeSky = Optional.of(p_345443_);
            return this;
        }

        public LocationPredicate build() {
            Optional<LocationPredicate.PositionPredicate> optional = LocationPredicate.PositionPredicate.of(this.x, this.y, this.z);
            return new LocationPredicate(
                optional, this.biomes, this.structures, this.dimension, this.smokey, this.light, this.block, this.fluid, this.canSeeSky
            );
        }
    }

    static record PositionPredicate(MinMaxBounds.Doubles x, MinMaxBounds.Doubles y, MinMaxBounds.Doubles z) {
        public static final Codec<LocationPredicate.PositionPredicate> CODEC = RecordCodecBuilder.create(
            p_337379_ -> p_337379_.group(
                        MinMaxBounds.Doubles.CODEC.optionalFieldOf("x", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::x),
                        MinMaxBounds.Doubles.CODEC.optionalFieldOf("y", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::y),
                        MinMaxBounds.Doubles.CODEC.optionalFieldOf("z", MinMaxBounds.Doubles.ANY).forGetter(LocationPredicate.PositionPredicate::z)
                    )
                    .apply(p_337379_, LocationPredicate.PositionPredicate::new)
        );

        static Optional<LocationPredicate.PositionPredicate> of(MinMaxBounds.Doubles p_298771_, MinMaxBounds.Doubles p_298418_, MinMaxBounds.Doubles p_299133_) {
            return p_298771_.isAny() && p_298418_.isAny() && p_299133_.isAny()
                ? Optional.empty()
                : Optional.of(new LocationPredicate.PositionPredicate(p_298771_, p_298418_, p_299133_));
        }

        public boolean matches(double p_298782_, double p_299123_, double p_298955_) {
            return this.x.matches(p_298782_) && this.y.matches(p_299123_) && this.z.matches(p_298955_);
        }
    }
}
