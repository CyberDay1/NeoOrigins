package com.cyberday1.neoorigins.api.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A reusable predicate over a player's current location. All fields are
 * optional. {@code dimension} / {@code structure} / {@code structure_tag}
 * combine with AND; biome fields ({@code biome}, {@code biome_tag},
 * {@code biomes}) combine with OR — any biome match satisfies the biome
 * requirement.
 *
 * <p>Evaluated server-side: structure lookups rely on
 * {@code ServerLevel.structureManager()} which is not populated on the client.
 */
public record LocationCondition(
    Optional<Identifier> dimension,
    Optional<Identifier> biome,
    Optional<Identifier> biomeTag,
    List<Identifier> biomes,
    Optional<Identifier> structure,
    Optional<Identifier> structureTag,
    boolean allowWaterSurface,
    boolean allowOceanFloor
) {
    public static final Codec<LocationCondition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        Identifier.CODEC.optionalFieldOf("dimension").forGetter(LocationCondition::dimension),
        Identifier.CODEC.optionalFieldOf("biome").forGetter(LocationCondition::biome),
        Identifier.CODEC.optionalFieldOf("biome_tag").forGetter(LocationCondition::biomeTag),
        Identifier.CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(LocationCondition::biomes),
        Identifier.CODEC.optionalFieldOf("structure").forGetter(LocationCondition::structure),
        Identifier.CODEC.optionalFieldOf("structure_tag").forGetter(LocationCondition::structureTag),
        Codec.BOOL.optionalFieldOf("allow_water_surface", false).forGetter(LocationCondition::allowWaterSurface),
        Codec.BOOL.optionalFieldOf("allow_ocean_floor", false).forGetter(LocationCondition::allowOceanFloor)
    ).apply(inst, LocationCondition::new));

    public boolean isEmpty() {
        return dimension.isEmpty() && biome.isEmpty() && biomeTag.isEmpty() && biomes.isEmpty()
            && structure.isEmpty() && structureTag.isEmpty();
    }

    /** True when any biome spec is configured. */
    private boolean hasBiomeFilter() {
        return biome.isPresent() || biomeTag.isPresent() || !biomes.isEmpty();
    }

    /** Tests the player's current server-side location against this condition. */
    public boolean test(ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = player.blockPosition();

        if (dimension.isPresent() && !level.dimension().identifier().equals(dimension.get())) return false;

        if (hasBiomeFilter()) {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            boolean matched = false;
            if (biome.isPresent()) {
                var key = biomeHolder.unwrapKey();
                if (key.isPresent() && key.get().identifier().equals(biome.get())) matched = true;
            }
            if (!matched && biomeTag.isPresent()) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                if (biomeHolder.is(tag)) matched = true;
            }
            if (!matched && !biomes.isEmpty()) {
                var key = biomeHolder.unwrapKey();
                if (key.isPresent()) {
                    Identifier current = key.get().identifier();
                    for (Identifier allowed : biomes) {
                        if (allowed.equals(current)) { matched = true; break; }
                    }
                }
            }
            if (!matched) return false;
        }

        if (structure.isPresent()) {
            var structureHolder = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(structure.get());
            if (structureHolder.isEmpty()) return false;
            if (!level.structureManager().getStructureWithPieceAt(pos, structureHolder.get().value()).isValid()) return false;
        }
        if (structureTag.isPresent()) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, structureTag.get());
            if (!level.structureManager().getStructureWithPieceAt(pos, tag).isValid()) return false;
        }

        return true;
    }

    /**
     * Human-readable one-liner for origin info screens. Returns empty
     * string when the condition carries no location filters.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code dimension: minecraft:the_end} + {@code biomes: [end_highlands, ...]}<br>
     *       → {@code "Spawns in: The End — End Highlands, End Midlands, End Barrens, Small End Islands"}</li>
     *   <li>{@code dimension: minecraft:the_nether}<br>
     *       → {@code "Spawns in: The Nether"}</li>
     *   <li>{@code biome_tag: minecraft:is_ocean}<br>
     *       → {@code "Spawns in: Ocean biomes"}</li>
     * </ul>
     */
    public String formatSummary() {
        if (isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Spawns in: ");
        boolean hasDim = dimension.isPresent();
        if (hasDim) sb.append(humanizeDimension(dimension.get()));

        List<String> detail = new ArrayList<>();
        if (biome.isPresent()) detail.add(humanize(biome.get()));
        if (biomeTag.isPresent()) detail.add(humanize(biomeTag.get()) + " biomes");
        for (Identifier b : biomes) detail.add(humanize(b));
        if (structure.isPresent()) detail.add(humanize(structure.get()));
        if (structureTag.isPresent()) detail.add(humanize(structureTag.get()) + " structures");

        if (!detail.isEmpty()) {
            if (hasDim) sb.append(" — ");
            sb.append(String.join(", ", detail));
        }
        return sb.toString();
    }

    private static String humanizeDimension(Identifier id) {
        String path = id.getPath();
        return switch (path) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> humanize(id);
        };
    }

    private static String humanize(Identifier id) {
        String path = id.getPath();
        if (path.startsWith("is_")) path = path.substring(3);
        StringBuilder out = new StringBuilder();
        for (String w : path.split("_")) {
            if (w.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(w.charAt(0)));
            out.append(w.substring(1));
        }
        return out.toString();
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
            target = player.level().getServer() != null ? player.level().getServer().getLevel(dimKey) : null;
            if (target == null) return Optional.empty();
        } else {
            target = player.level();
        }

        // Search from the dimension's world origin — structure generation is
        // centered on (0,0,0), so the closest match from there is the canonical
        // "spawn-area" structure for that dimension. Matches the 1.21.1 branch's
        // getSharedSpawnPos() intent now that 26.1 removed that accessor.
        BlockPos searchOrigin = BlockPos.ZERO;
        BlockPos found = null;

        if (structure.isPresent() || structureTag.isPresent()) {
            if (structureTag.isPresent()) {
                TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, structureTag.get());
                found = target.findNearestMapStructure(tag, searchOrigin, 6400, false);
            } else {
                Registry<Structure> reg = target.registryAccess().lookupOrThrow(Registries.STRUCTURE);
                var strHolderOpt = reg.get(structure.get());
                if (strHolderOpt.isEmpty()) return Optional.empty();
                HolderSet<Structure> set = HolderSet.direct(strHolderOpt.get());
                var pair = target.getChunkSource().getGenerator().findNearestMapStructure(target, set, searchOrigin, 6400, false);
                if (pair != null) found = pair.getFirst();
            }
            if (found == null) return Optional.empty();
        } else if (hasBiomeFilter()) {
            var pair = target.findClosestBiome3d(holder -> {
                if (biome.isPresent()) {
                    var key = holder.unwrapKey();
                    if (key.isPresent() && key.get().identifier().equals(biome.get())) return true;
                }
                if (biomeTag.isPresent()) {
                    TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                    if (holder.is(tag)) return true;
                }
                if (!biomes.isEmpty()) {
                    var key = holder.unwrapKey();
                    if (key.isPresent()) {
                        Identifier current = key.get().identifier();
                        for (Identifier allowed : biomes) {
                            if (allowed.equals(current)) return true;
                        }
                    }
                }
                return false;
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
        int topYTmp = minY + target.dimensionType().logicalHeight() - 1;
        // For ceiling dimensions (Nether): start the scan well below the
        // bedrock roof. Bedrock generates with noise, so 1-block air pockets
        // exist within the top ~5 blocks; without this margin the player
        // can spawn sandwiched in a roof-bedrock pocket (reported case).
        // 16-block margin is safely below the bedrock zone in vanilla Nether.
        if (target.dimensionType().hasCeiling()) topYTmp -= 16;
        final int topY = topYTmp;

        // For aquatic origins (allow_ocean_floor / allow_water_surface), try
        // the water passes FIRST. If the 5x5 search around the biome-locate
        // center happens to include a tiny island column, Pass 1 (land) would
        // otherwise grab it and spawn the player on dry land — where any
        // sun-damage power on the same origin (e.g. abyssal_daylight_damage)
        // immediately starts ticking. Aquatic origins should land in water.
        if (allowOceanFloor) {
            Optional<Vec3> floor = findColumn(target, found, minY, topY, LocationCondition::isOceanFloorColumn);
            if (floor.isPresent()) return Optional.of(new SpawnTarget(target, floor.get()));
        }
        if (allowWaterSurface) {
            Optional<Vec3> surface = findColumn(target, found, minY, topY, LocationCondition::isWaterSurfaceColumn);
            if (surface.isPresent()) return Optional.of(new SpawnTarget(target, surface.get()));
        }

        // Land column fallback — default for land-based origins, and the
        // last-resort for aquatic origins if no water column was found
        // (shouldn't happen for ocean biomes, but covers misconfigured packs).
        Optional<Vec3> land = findColumn(target, found, minY, topY, LocationCondition::isLandColumn);
        if (land.isPresent()) return Optional.of(new SpawnTarget(target, land.get()));

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
        // Center column: solid floor + 2-block air clearance, no fluids.
        if (!isSafeStandingCell(level, x, y, z)) return false;
        // Require a 3x3 safe area (8 surrounding columns also pass the
        // standing-cell check) so the player doesn't spawn on a 1-block
        // pillar over lava, in a 1-wide crevice, or with their head
        // clipping into an overhang.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (!isSafeStandingCell(level, x + dx, y, z + dz)) return false;
            }
        }
        // Ceiling dimensions (Nether) have no sky below the bedrock roof —
        // canSeeSky is always false for any playable column, so the sky
        // check would reject every candidate (reported case: Blazeling
        // spawn_location: minecraft:the_nether silently failed). The
        // top-down scan already gives us the highest solid+air+air, which
        // is the surface of the natural netherrack terrain below the ceiling.
        if (level.dimensionType().hasCeiling()) return true;
        // Require the head block to see sky so we don't pick a cave air-pocket
        // as "land". In an ocean biome the top-down scan would otherwise find
        // the first solid+air+air somewhere deep underground (reported case:
        // cold_ocean spawn landed at y=-53 in a cave) because no real land
        // column exists in the water column above. canSeeSky is a cheap
        // heightmap check — true only when the position is above the
        // MOTION_BLOCKING heightmap for that XZ, which water contributes to.
        return level.canSeeSky(new BlockPos(x, y + 1, z));
    }

    /**
     * One cell of the 3x3 land-spawn check: solid (non-fluid, non-lava)
     * floor at y-1, air at feet (y) and head (y+1), and no lava anywhere
     * in the floor/feet/head triple. Used to reject spawn columns that
     * sit on lava, in a 1-block-wide crevice, or under an overhang.
     */
    private static boolean isSafeStandingCell(ServerLevel level, int x, int y, int z) {
        BlockPos floorPos = new BlockPos(x, y - 1, z);
        BlockPos feetPos  = new BlockPos(x, y,     z);
        BlockPos headPos  = new BlockPos(x, y + 1, z);
        BlockState floor = level.getBlockState(floorPos);
        BlockState feet  = level.getBlockState(feetPos);
        BlockState head  = level.getBlockState(headPos);
        if (!floor.isSolid()) return false;
        if (!feet.isAir() || !head.isAir()) return false;
        if (level.getFluidState(floorPos).is(FluidTags.LAVA)) return false;
        if (level.getFluidState(feetPos).is(FluidTags.LAVA))  return false;
        if (level.getFluidState(headPos).is(FluidTags.LAVA))  return false;
        return true;
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
