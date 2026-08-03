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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A reusable predicate over a player's current location. {@code dimension} /
 * {@code structure} / {@code structure_tag} combine with AND; biome fields
 * ({@code biome}, {@code biome_tag}, {@code biomes}) combine with OR — any
 * biome match satisfies the biome requirement.
 *
 * <p>Evaluated server-side: structure lookups rely on
 * {@code ServerLevel.structureManager()} which is not populated on the client.
 */
public record LocationCondition(
    Optional<ResourceLocation> dimension,
    Optional<ResourceLocation> biome,
    Optional<ResourceLocation> biomeTag,
    List<ResourceLocation> biomes,
    Optional<ResourceLocation> structure,
    Optional<ResourceLocation> structureTag,
    boolean allowWaterSurface,
    boolean allowOceanFloor,
    Optional<Integer> minY,
    Optional<Integer> maxY,
    Optional<Boolean> canSeeSky
) {
    public static final Codec<LocationCondition> CODEC = RecordCodecBuilder.create(inst -> inst.group(
        ResourceLocation.CODEC.optionalFieldOf("dimension").forGetter(LocationCondition::dimension),
        ResourceLocation.CODEC.optionalFieldOf("biome").forGetter(LocationCondition::biome),
        ResourceLocation.CODEC.optionalFieldOf("biome_tag").forGetter(LocationCondition::biomeTag),
        ResourceLocation.CODEC.listOf().optionalFieldOf("biomes", List.of()).forGetter(LocationCondition::biomes),
        ResourceLocation.CODEC.optionalFieldOf("structure").forGetter(LocationCondition::structure),
        ResourceLocation.CODEC.optionalFieldOf("structure_tag").forGetter(LocationCondition::structureTag),
        Codec.BOOL.optionalFieldOf("allow_water_surface", false).forGetter(LocationCondition::allowWaterSurface),
        Codec.BOOL.optionalFieldOf("allow_ocean_floor", false).forGetter(LocationCondition::allowOceanFloor),
        Codec.INT.optionalFieldOf("min_y").forGetter(LocationCondition::minY),
        Codec.INT.optionalFieldOf("max_y").forGetter(LocationCondition::maxY),
        Codec.BOOL.optionalFieldOf("can_see_sky").forGetter(LocationCondition::canSeeSky)
    ).apply(inst, LocationCondition::new));

    public boolean isEmpty() {
        return dimension.isEmpty() && biome.isEmpty() && biomeTag.isEmpty() && biomes.isEmpty()
            && structure.isEmpty() && structureTag.isEmpty();
    }

    private boolean hasBiomeFilter() {
        return biome.isPresent() || biomeTag.isPresent() || !biomes.isEmpty();
    }

    /** Tests the player's current server-side location against this condition. */
    public boolean test(ServerPlayer player) {
        return test(player.serverLevel(), player.blockPosition());
    }

    /**
     * Position-based variant — same dimension/biome/structure logic as
     * {@link #test(ServerPlayer)} but for an arbitrary level + position
     * (used by mob-origin spawn rules, which have no player context).
     */
    public boolean test(ServerLevel level, BlockPos pos) {
        if (dimension.isPresent() && !level.dimension().location().equals(dimension.get())) return false;

        if (hasBiomeFilter()) {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            boolean matched = false;
            if (biome.isPresent()) {
                var key = biomeHolder.unwrapKey();
                if (key.isPresent() && key.get().location().equals(biome.get())) matched = true;
            }
            if (!matched && biomeTag.isPresent()) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                if (biomeHolder.is(tag)) matched = true;
            }
            if (!matched && !biomes.isEmpty()) {
                var key = biomeHolder.unwrapKey();
                if (key.isPresent()) {
                    ResourceLocation current = key.get().location();
                    for (ResourceLocation allowed : biomes) {
                        if (allowed.equals(current)) { matched = true; break; }
                    }
                }
            }
            if (!matched) return false;
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

    /**
     * Human-readable one-liner for origin info screens. Returns empty
     * string when the condition carries no location filters.
     */
    public String formatSummary() {
        if (isEmpty()) return "";
        StringBuilder sb = new StringBuilder("Spawns in: ");
        boolean hasDim = dimension.isPresent();
        if (hasDim) sb.append(humanizeDimension(dimension.get()));

        List<String> detail = new ArrayList<>();
        if (biome.isPresent()) detail.add(humanize(biome.get()));
        if (biomeTag.isPresent()) detail.add(humanize(biomeTag.get()) + " biomes");
        for (ResourceLocation b : biomes) detail.add(humanize(b));
        if (structure.isPresent()) detail.add(humanize(structure.get()));
        if (structureTag.isPresent()) detail.add(humanize(structureTag.get()) + " structures");

        if (!detail.isEmpty()) {
            if (hasDim) sb.append(" — ");
            sb.append(String.join(", ", detail));
        }
        if (minY.isPresent() || maxY.isPresent()) {
            sb.append(" (Y ");
            if (minY.isPresent() && maxY.isPresent()) {
                sb.append(minY.get()).append(" to ").append(maxY.get());
            } else if (minY.isPresent()) {
                sb.append("above ").append(minY.get());
            } else {
                sb.append("below ").append(maxY.get());
            }
            sb.append(")");
        }
        if (canSeeSky.isPresent() && !canSeeSky.get()) {
            sb.append(" [underground]");
        }
        return sb.toString();
    }

    private static String humanizeDimension(ResourceLocation id) {
        return switch (id.getPath()) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "The Nether";
            case "the_end" -> "The End";
            default -> humanize(id);
        };
    }

    private static String humanize(ResourceLocation id) {
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
     * Resolves the dimension this spec searches in: the explicit {@code dimension}
     * if given, else the player's current level. Empty when the named dimension
     * is not loaded. Cheap — safe to call on the server thread.
     */
    public Optional<ServerLevel> resolveTargetLevel(ServerPlayer player) {
        if (dimension.isPresent()) {
            ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimension.get());
            return Optional.ofNullable(player.server.getLevel(dimKey));
        }
        return Optional.of(player.serverLevel());
    }

    /** True when this spec resolves its centre via a structure search (which takes precedence over biome). */
    public boolean usesStructureSearch() {
        return structure.isPresent() || structureTag.isPresent();
    }

    /**
     * True when resolving this spec requires the (potentially very expensive)
     * biome spiral — i.e. it has a biome filter and no structure filter to
     * short-circuit it. These are the specs worth moving off the server thread.
     */
    public boolean usesBiomeSearch() {
        return !usesStructureSearch() && hasBiomeFilter();
    }

    /**
     * The biome test this spec applies, as a standalone predicate. Extracted so
     * the search can pre-filter {@code BiomeSource#possibleBiomes()} once and
     * then run on a worker thread against the resulting identity set.
     *
     * <p>{@code biome} / {@code biome_tag} / {@code biomes} combine with OR.
     */
    public java.util.function.Predicate<Holder<Biome>> biomeMatcher() {
        return holder -> {
            if (biome.isPresent()) {
                var key = holder.unwrapKey();
                if (key.isPresent() && key.get().location().equals(biome.get())) return true;
            }
            if (biomeTag.isPresent()) {
                TagKey<Biome> tag = TagKey.create(Registries.BIOME, biomeTag.get());
                if (holder.is(tag)) return true;
            }
            if (!biomes.isEmpty()) {
                var key = holder.unwrapKey();
                if (key.isPresent()) {
                    ResourceLocation current = key.get().location();
                    for (ResourceLocation allowed : biomes) {
                        if (allowed.equals(current)) return true;
                    }
                }
            }
            return false;
        };
    }

    /**
     * Structure-search phase. <b>Server thread only</b> — structure lookup can
     * force chunk generation and touches {@code StructureManager} state, neither
     * of which is safe from a worker.
     */
    public Optional<BlockPos> locateStructureCenter(ServerLevel target, BlockPos searchOrigin) {
        BlockPos found = null;
        if (structureTag.isPresent()) {
            TagKey<Structure> tag = TagKey.create(Registries.STRUCTURE, structureTag.get());
            found = target.findNearestMapStructure(tag, searchOrigin, 6400, false);
        } else if (structure.isPresent()) {
            Registry<Structure> reg = target.registryAccess().registryOrThrow(Registries.STRUCTURE);
            Structure str = reg.get(structure.get());
            if (str == null) return Optional.empty();
            HolderSet<Structure> set = HolderSet.direct(reg.wrapAsHolder(str));
            var pair = target.getChunkSource().getGenerator().findNearestMapStructure(target, set, searchOrigin, 6400, false);
            if (pair != null) found = pair.getFirst();
        }
        return Optional.ofNullable(found);
    }

    /**
     * Searches for a position matching this spec, starting from the target
     * dimension's shared spawn. Structure match takes precedence over biome
     * when both are specified (structure search already constrains location).
     *
     * <p>Returns empty when the spec is empty, when the target dimension is
     * not loaded, or when no matching biome/structure is found within the
     * search radius.
     *
     * <p><b>This runs the whole thing synchronously.</b> The biome branch is
     * bounded and budgeted (see {@link com.cyberday1.neoorigins.service.BoundedBiomeSpiral}),
     * so it can no longer hang the server watchdog, but it can still cost real
     * milliseconds on the calling thread. Everything inside NeoOrigins now goes
     * through {@link com.cyberday1.neoorigins.service.AsyncSpawnLocator} instead,
     * which runs the biome branch on a worker and applies the result on the
     * server thread; this method is retained for third-party API callers.
     */
    public Optional<SpawnTarget> locateSpawn(ServerPlayer player) {
        if (isEmpty()) return Optional.empty();

        Optional<ServerLevel> resolved = resolveTargetLevel(player);
        if (resolved.isEmpty()) return Optional.empty();
        ServerLevel target = resolved.get();

        BlockPos searchOrigin = target.getSharedSpawnPos();
        BlockPos found;

        if (usesStructureSearch()) {
            Optional<BlockPos> structureCenter = locateStructureCenter(target, searchOrigin);
            if (structureCenter.isEmpty()) return Optional.empty();
            found = structureCenter.get();
        } else if (hasBiomeFilter()) {
            Optional<BlockPos> biomeCenter = com.cyberday1.neoorigins.service.BiomeSpawnSearch
                .findSync(target, searchOrigin, biomeMatcher());
            if (biomeCenter.isEmpty()) return Optional.empty();
            found = biomeCenter.get();
        } else {
            // Dimension-only spec (no biome/structure filter). Use the
            // dimension's shared spawn pos as the search center. For ceiling
            // dimensions (Nether) the spawn pos is often inside solid
            // netherrack, so the wider column search below will spiral
            // outward to find habitable terrain.
            found = searchOrigin;
        }

        return refineSpawn(target, found);
    }

    /**
     * Second phase: turn a located centre into an actual standable position.
     * <b>Server thread only</b> — force-loads chunks and reads block/fluid
     * states, none of which is safe from a worker thread.
     *
     * <p><b>This blocks.</b> Every chunk it touches that is not already
     * generated is generated inline, on the calling thread, via
     * {@code ServerChunkCache#getChunk}'s {@code managedBlock} spin — seconds
     * apiece on a heavy worldgen pack. Retained for
     * {@link #locateSpawn(ServerPlayer)}, the public synchronous API; everything
     * inside NeoOrigins goes through
     * {@link com.cyberday1.neoorigins.service.SpawnChunkLoader} and then
     * {@link #refineSpawnPreloaded} instead.
     */
    public Optional<SpawnTarget> refineSpawn(ServerLevel target, BlockPos found) {
        // Force-load the chunk at `found` so its structures have actually
        // placed blocks and the heightmap is populated. On a fresh world
        // that's never had the target dimension visited, the chunk may not
        // yet be generated. (The spiral below starts at `found` itself, so
        // ForcingGate would load it anyway; kept explicit for clarity.)
        target.getChunk(found.getX() >> 4, found.getZ() >> 4);
        return refine(target, found, new ForcingGate());
    }

    /**
     * Same refinement, but it never force-loads: columns whose chunks are not
     * already present are skipped instead of generated. Pair it with
     * {@link com.cyberday1.neoorigins.service.SpawnChunkLoader#whenReady}, which
     * gets exactly the chunks this reads generated off the server thread first.
     *
     * <p>With the loader's window in place the two refinements agree, with one
     * documented exception: the outermost (radius 16) shell's 3x3 clearance test
     * can reach one chunk beyond the window, and those positions are skipped
     * here rather than dragging a fourth chunk column into generation. That
     * shell is the desperate last resort of an already-fallback search, and
     * paying nine extra chunk generations to keep it is a bad trade.
     */
    public Optional<SpawnTarget> refineSpawnPreloaded(ServerLevel target, BlockPos found) {
        return refine(target, found, new PreloadedGate());
    }

    private Optional<SpawnTarget> refine(ServerLevel target, BlockPos found, ChunkGate gate) {
        // Use logicalHeight so ceiling dimensions (Nether) don't scan the
        // dead-air layer above the bedrock roof.
        final int dimMinY = target.dimensionType().minY();
        int dimTopY = dimMinY + target.dimensionType().logicalHeight() - 1;
        // For ceiling dimensions (Nether): start the scan well below the
        // bedrock roof. Bedrock generates with noise, so 1-block air pockets
        // exist within the top ~5 blocks; without this margin the player
        // can spawn sandwiched in a roof-bedrock pocket (reported case).
        // 16-block margin is safely below the bedrock zone in vanilla Nether.
        if (target.dimensionType().hasCeiling()) dimTopY -= 16;
        final int dimTopYFinal = dimTopY;

        // Apply optional Y clamps — pack authors can constrain the vertical
        // search band with min_y / max_y (e.g. underground-only origins).
        final int minY = this.minY.map(v -> Math.max(v, dimMinY)).orElse(dimMinY);
        final int topY = this.maxY.map(v -> Math.min(v, dimTopYFinal)).orElse(dimTopYFinal);

        // Resolve the sky-check policy for land columns:
        //   - can_see_sky explicitly set → use that value
        //   - ceiling dimension (Nether) → false (no sky is visible anyway)
        //   - otherwise → true (legacy default: reject caves)
        final boolean requireSky = canSeeSky.orElse(!target.dimensionType().hasCeiling());

        // For aquatic origins (allow_ocean_floor / allow_water_surface), try
        // the water passes FIRST. If the 5x5 search around the biome-locate
        // center happens to include a tiny island column, Pass 1 (land) would
        // otherwise grab it and spawn the player on dry land — where any
        // sun-damage power on the same origin (e.g. abyssal_daylight_damage)
        // immediately starts ticking. Aquatic origins should land in water.
        if (allowOceanFloor) {
            Optional<Vec3> floor = findColumn(target, found, minY, topY, LocationCondition::isOceanFloorColumn, gate);
            if (floor.isPresent()) return Optional.of(new SpawnTarget(target, floor.get()));
        }
        if (allowWaterSurface) {
            Optional<Vec3> surface = findColumn(target, found, minY, topY, LocationCondition::isWaterSurfaceColumn, gate);
            if (surface.isPresent()) return Optional.of(new SpawnTarget(target, surface.get()));
        }

        // Land column fallback — default for land-based origins, and the
        // last-resort for aquatic origins if no water column was found
        // (shouldn't happen for ocean biomes, but covers misconfigured packs).
        Optional<Vec3> land = findColumn(target, found, minY, topY,
            (level, x, y, z) -> isLandColumn(level, x, y, z, requireSky), gate);
        if (land.isPresent()) return Optional.of(new SpawnTarget(target, land.get()));

        return Optional.empty();
    }

    /**
     * Spirals outward from {@code found} (center-first), and for each column
     * scans top-down looking for the highest Y satisfying {@code test}. Returns
     * the (x+0.5, y, z+0.5) spawn position, or empty if no column matches.
     */
    private static Optional<Vec3> findColumn(ServerLevel target, BlockPos found, int minY, int topY,
                                             ColumnTest test, ChunkGate gate) {
        // Spiral outward from the biome-locate center. Underground biomes
        // (lush_caves, dripstone_caves) are located at biome resolution
        // (4x4x4 sections) so the exact position may be inside solid stone.
        // A wider search radius (16 blocks) gives enough coverage to find
        // a cave air pocket near the locate result.
        int radius = SEARCH_RADIUS;
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // shell only
                    int tryX = found.getX() + dx;
                    int tryZ = found.getZ() + dz;
                    // One chunk decision per column instead of the old
                    // getChunk-per-block-position: ServerChunkCache's lookup
                    // cache holds only four entries, and a radius-16 spiral
                    // works over nine chunks, so the per-position calls were
                    // thrashing it rather than riding it.
                    if (!gate.readable(target, tryX, tryZ)) continue;
                    // Bound the column scan by the heightmap. Every one of the
                    // three column tests needs a non-air block at or below the
                    // candidate Y (a solid floor, or water), so nothing above
                    // WORLD_SURFACE can ever match — scanning from the build
                    // limit was ~250 wasted iterations per column in the
                    // overworld, times up to 1089 columns. min() keeps a pack's
                    // max_y override authoritative; min_y is untouched.
                    int startY = Math.min(topY, gate.scanTop(target, tryX, tryZ));
                    for (int y = startY; y > minY; y--) {
                        if (test.matches(target, tryX, y, tryZ)) {
                            return Optional.of(new Vec3(tryX + 0.5, y, tryZ + 0.5));
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Horizontal spiral radius, in blocks, of the standable-column search.
     * Public so {@code SpawnChunkLoader} sizes its preload window off the same
     * number this actually walks.
     */
    public static final int SEARCH_RADIUS = 16;

    @FunctionalInterface
    private interface ColumnTest {
        boolean matches(ServerLevel level, int x, int y, int z);
    }

    /**
     * Decides, per column, whether {@link #findColumn} may read it — and where
     * the read may start. The two implementations are the whole difference
     * between the blocking legacy path and the preloaded one.
     */
    private interface ChunkGate {
        /** True when the 3x3 block footprint at (x, z) can be read. */
        boolean readable(ServerLevel level, int x, int z);

        /** Highest Y worth testing in this column (one above the top non-air block). */
        int scanTop(ServerLevel level, int x, int z);
    }

    /**
     * Legacy behaviour: generate whatever the search touches, on this thread.
     * The {@code seen} set exists purely to stop the old per-block-position
     * {@code getChunk} storm — the set of chunks force-loaded is unchanged.
     */
    private static final class ForcingGate implements ChunkGate {
        private final java.util.Set<Long> seen = new java.util.HashSet<>();

        @Override
        public boolean readable(ServerLevel level, int x, int z) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (seen.add((((long) cx) << 32) | (cz & 0xFFFFFFFFL))) {
                level.getChunk(cx, cz);
            }
            return true;
        }

        @Override
        public int scanTop(ServerLevel level, int x, int z) {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        }
    }

    /**
     * Non-blocking behaviour: read only what is already present. Used after
     * {@code SpawnChunkLoader} has had the destination window generated off the
     * server thread, and as the graceful degradation when that wait times out.
     */
    private static final class PreloadedGate implements ChunkGate {
        @Override
        public boolean readable(ServerLevel level, int x, int z) {
            // The land test reads x±1 / z±1, so the whole 3x3 footprint must be
            // present, not just the centre column's chunk. Checking the four
            // corners covers it — they bound every chunk the footprint spans.
            return present(level, x - 1, z - 1) && present(level, x + 1, z - 1)
                && present(level, x - 1, z + 1) && present(level, x + 1, z + 1);
        }

        @Override
        public int scanTop(ServerLevel level, int x, int z) {
            // Straight off the chunk rather than via Level#getHeight, which
            // first asks hasChunk() — a ticket-level question, not a
            // "is it generated" one, and answering it wrong here would silently
            // collapse the scan to the build floor.
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) return level.getMinBuildHeight();
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
        }

        private static boolean present(ServerLevel level, int x, int z) {
            return level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null;
        }
    }

    private static boolean isLandColumn(ServerLevel level, int x, int y, int z, boolean requireSky) {
        // Center column: solid floor at y-1, air at feet and head, no
        // lava in the column triple. This is the load-bearing safety check.
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
        // 3x3 air clearance — player needs elbow room so they don't spawn
        // wedged in a 1-wide crevice or under a low overhang. Floor only
        // required at center: rough terrain (Nether netherrack, mountain
        // tops) rarely has flat 3x3 surfaces and we'd otherwise reject
        // every valid spawn (reported case: Blazeling failing to spawn).
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos f = new BlockPos(x + dx, y,     z + dz);
                BlockPos h = new BlockPos(x + dx, y + 1, z + dz);
                if (!level.getBlockState(f).isAir() || !level.getBlockState(h).isAir()) return false;
                if (level.getFluidState(f).is(FluidTags.LAVA)) return false;
                if (level.getFluidState(h).is(FluidTags.LAVA)) return false;
            }
        }
        // Reject any lava in the 3x3 floor ring — keeps us off a lava bowl
        // bridged by a 1-block stone island.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level.getFluidState(new BlockPos(x + dx, y - 1, z + dz)).is(FluidTags.LAVA)) return false;
            }
        }
        // Sky check: when requireSky is false (either explicitly via
        // can_see_sky: false, or implicitly for ceiling dimensions like
        // the Nether), skip the sky test so underground / cave columns
        // are valid spawn positions. When true (the default for non-ceiling
        // dimensions), reject cave air-pockets so the player spawns on
        // the surface.
        if (!requireSky) return true;
        return level.canSeeSky(new BlockPos(x, y + 1, z));
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
