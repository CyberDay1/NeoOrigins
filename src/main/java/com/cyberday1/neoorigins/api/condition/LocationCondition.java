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
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
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
    Optional<ResourceLocation> structureTag
) {
    public static final Codec<LocationCondition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(LocationCondition::dimension),
        ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(LocationCondition::biome),
        ResourceLocation.CODEC.optionalFieldOf("biome_tag").forGetter(LocationCondition::biomeTag),
        ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(LocationCondition::structure),
        ResourceLocation.CODEC.optionalFieldOf("structure_tag").forGetter(LocationCondition::structureTag)
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

        // Scan from the dimension's top downward for the highest solid block
        // with two air blocks above it. Don't rely on
        // MOTION_BLOCKING_NO_LEAVES as the starting Y — on ungenerated
        // chunks it returns minY and the scan exits before finding anything.
        // If the exact column at `found` has no safe floor (e.g. The End
        // structure chunk center lands in a gap between islands), sweep a
        // 5x5 XZ area looking for a safe spot. Use logicalHeight so ceiling
        // dimensions (Nether) don't place the player on the bedrock roof.
        final int minY = target.dimensionType().minY();
        final int topY = minY + target.dimensionType().logicalHeight() - 1;
        for (int dx = 0; dx <= 4; dx++) {
            for (int dz = 0; dz <= 4; dz++) {
                int tryX = found.getX() + (dx % 2 == 0 ? dx / 2 : -((dx + 1) / 2));
                int tryZ = found.getZ() + (dz % 2 == 0 ? dz / 2 : -((dz + 1) / 2));
                target.getChunk(tryX >> 4, tryZ >> 4);
                for (int y = topY; y > minY; y--) {
                    BlockPos floorPos = new BlockPos(tryX, y - 1, tryZ);
                    BlockPos feetPos  = new BlockPos(tryX, y,     tryZ);
                    BlockPos headPos  = new BlockPos(tryX, y + 1, tryZ);
                    if (target.getBlockState(floorPos).isSolid()
                        && target.getBlockState(feetPos).isAir()
                        && target.getBlockState(headPos).isAir()) {
                        Vec3 spawnPos = new Vec3(tryX + 0.5, y, tryZ + 0.5);
                        return Optional.of(new SpawnTarget(target, spawnPos));
                    }
                }
            }
        }
        return Optional.empty();
    }
}
