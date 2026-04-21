package com.cyberday1.neoorigins.api.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * A reusable predicate over a player's current location. All fields are
 * optional and combine with AND — a condition is satisfied only when every
 * present field matches.
 *
 * <p>Evaluated server-side: structure lookups rely on
 * {@code ServerLevel.structureManager()} which is not populated on the client.
 */
public record LocationCondition(
    Optional<ResourceLocation> dimension,
    Optional<ResourceLocation> biome,
    Optional<ResourceLocation> biomeTag,
    Optional<ResourceLocation> structure,
    Optional<ResourceLocation> structureTag,
    boolean allowWaterSurface,
    boolean allowOceanFloor
) {
    public static final Codec<LocationCondition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(LocationCondition::dimension),
        ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(LocationCondition::biome),
        ResourceLocation.CODEC.optionalFieldOf("biome_tag").forGetter(LocationCondition::biomeTag),
        ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(LocationCondition::structure),
        ResourceLocation.CODEC.optionalFieldOf("structure_tag").forGetter(LocationCondition::structureTag),
        Codec.BOOL.optionalFieldOf("allow_water_surface", false).forGetter(LocationCondition::allowWaterSurface),
        Codec.BOOL.optionalFieldOf("allow_ocean_floor", false).forGetter(LocationCondition::allowOceanFloor)
    ).apply(inst, LocationCondition::new));

    public boolean isEmpty() {
        return dimension.isEmpty() && biome.isEmpty() && biomeTag.isEmpty()
            && structure.isEmpty() && structureTag.isEmpty();
    }

    /** Tests the player's current server-side location against this condition. */
    public boolean test(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();

        if (dimension.isPresent() && !level.dimension().location().equals(dimension.get())) return false;

        if (biome.isPresent() || biomeTag.isPresent()) {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            if (biome.isPresent()) {
                var key = biomeHolder.unwrapKey();
                if (key.isEmpty() || !key.get().location().equals(biome.get())) return false;
            }
            if (biomeTag.isPresent()) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                if (!biomeHolder.is(tag)) return false;
            }
        }

        if (structure.isPresent()) {
            Structure str = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure.get());
            if (str == null) return false;
            if (!level.structureManager().getStructureWithPieceAt(pos, str).isValid()) return false;
        }
        if (structureTag.isPresent()) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, structureTag.get());
            if (!level.structureManager().getStructureWithPieceAt(pos, tag).isValid()) return false;
        }

        return true;
    }

    /** Resolved (level, position) pair returned by {@link #locateSpawn}. */
    public record SpawnTarget(ServerLevel level, Vec3 pos) {}

    /**
     * Searches for a position matching this spec, starting from the target
     * dimension's shared spawn. Structure match takes precedence over biome
     * when both are specified (structure search already constrains location).
     *
     * <p>Returns empty when the spec is empty, when the target dimension is
     * not loaded, or when no matching biome/structure is found within the
     * search radius.
     */
    public Optional<SpawnTarget> locateSpawn(ServerPlayer player) {
        if (isEmpty()) return Optional.empty();

        ServerLevel target;
        if (dimension.isPresent()) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension.get());
            target = player.server.getLevel(dimKey);
            if (target == null) return Optional.empty();
        } else {
            target = player.serverLevel();
        }

        BlockPos searchOrigin = target.getSharedSpawnPos();
        BlockPos found = null;

        if (structure.isPresent() || structureTag.isPresent()) {
            if (structureTag.isPresent()) {
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, structureTag.get());
                found = target.findNearestMapStructure(tag, searchOrigin, 6400, false);
            } else {
                Registry<Structure> reg = target.registryAccess().registryOrThrow(Registries.STRUCTURE);
                Structure str = reg.get(structure.get());
                if (str == null) return Optional.empty();
                HolderSet<Structure> set = HolderSet.direct(reg.wrapAsHolder(str));
                var pair = target.getChunkSource().getGenerator().findNearestMapStructure(target, set, searchOrigin, 6400, false);
                if (pair != null) found = pair.getFirst();
            }
            if (found == null) return Optional.empty();
        } else if (biome.isPresent() || biomeTag.isPresent()) {
            var pair = target.findClosestBiome3d(holder -> {
                if (biome.isPresent()) {
                    var key = holder.unwrapKey();
                    if (key.isEmpty() || !key.get().location().equals(biome.get())) return false;
                }
                if (biomeTag.isPresent()) {
                    TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                    if (!holder.is(tag)) return false;
                }
                return true;
            }, searchOrigin, 6400, 32, 64);
            if (pair == null) return Optional.empty();
            found = pair.getFirst();
        } else {
            found = searchOrigin;
        }

        // Force-load the chunk at `found` so its structures have actually
        // placed blocks and the heightmap is populated. On a fresh world
        // that's never had the target dimension visited, the chunk may not
        // yet be generated.
        target.getChunk(found.getX() >> 4, found.getZ() >> 4);

        // Use logicalHeight so ceiling dimensions (Nether) don't scan the
        // dead-air layer above the bedrock roof.
        final int minY = target.dimensionType().minY();
        final int topY = minY + target.dimensionType().logicalHeight() - 1;

        // Pass 1: strict land column — solid floor, two air blocks above.
        Optional<Vec3> land = findColumn(target, found, minY, topY, LocationCondition::isLandColumn);
        if (land.isPresent()) return Optional.of(new SpawnTarget(target, land.get()));

        // Pass 2: submerged spawn on the ocean/lake floor (water-breathing
        // origins). Same column shape as land, but feet+head are water.
        if (allowOceanFloor) {
            Optional<Vec3> floor = findColumn(target, found, minY, topY, LocationCondition::isOceanFloorColumn);
            if (floor.isPresent()) return Optional.of(new SpawnTarget(target, floor.get()));
        }

        // Pass 3: water surface — feet submerged in the topmost water block,
        // head in open air. Doesn't require any solid floor beneath.
        if (allowWaterSurface) {
            Optional<Vec3> surface = findColumn(target, found, minY, topY, LocationCondition::isWaterSurfaceColumn);
            if (surface.isPresent()) return Optional.of(new SpawnTarget(target, surface.get()));
        }

        return Optional.empty();
    }

    /**
     * Scans a 5x5 XZ area centered on {@code found} (center-first), and for
     * each column scans top-down from {@code topY} to {@code minY} looking
     * for the highest Y satisfying {@code test}. Returns the (x+0.5, y,
     * z+0.5) spawn position, or empty if no column matches.
     */
    private static Optional<Vec3> findColumn(ServerLevel target, BlockPos found, int minY, int topY, ColumnTest test) {
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                int tryX = found.getX() + (dx % 2 == 0 ? dx / 2 : -((dx + 1) / 2));
                int tryZ = found.getZ() + (dz % 2 == 0 ? dz / 2 : -((dz + 1) / 2));
                target.getChunk(tryX >> 4, tryZ >> 4);
                for (int y = topY; y > minY; y--) {
                    if (test.matches(target, tryX, y, tryZ)) {
                        return Optional.of(new Vec3(tryX + 0.5, y, tryZ + 0.5));
                    }
                }
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface ColumnTest {
        boolean matches(ServerLevel level, int x, int y, int z);
    }

    private static boolean isLandColumn(ServerLevel level, int x, int y, int z) {
        BlockState floor = level.getBlockState(new BlockPos(x, y - 1, z));
        BlockState feet  = level.getBlockState(new BlockPos(x, y,     z));
        BlockState head  = level.getBlockState(new BlockPos(x, y + 1, z));
        return floor.isSolid() && feet.isAir() && head.isAir();
    }

    private static boolean isOceanFloorColumn(ServerLevel level, int x, int y, int z) {
        BlockState floor = level.getBlockState(new BlockPos(x, y - 1, z));
        return floor.isSolid()
            && level.getFluidState(new BlockPos(x, y,     z)).is(FluidTags.WATER)
            && level.getFluidState(new BlockPos(x, y + 1, z)).is(FluidTags.WATER);
    }

    private static boolean isWaterSurfaceColumn(ServerLevel level, int x, int y, int z) {
        return level.getFluidState(new BlockPos(x, y, z)).is(FluidTags.WATER)
            && level.getBlockState(new BlockPos(x, y + 1, z)).isAir();
    }
}
