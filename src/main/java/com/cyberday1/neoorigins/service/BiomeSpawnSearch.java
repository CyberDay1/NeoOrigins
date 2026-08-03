package com.cyberday1.neoorigins.service;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Minecraft-side glue around {@link BoundedBiomeSpiral}: turns a
 * {@link ServerLevel} plus a biome predicate into an immutable snapshot that
 * can safely be handed to a worker thread, and runs the spiral against it.
 *
 * <p><b>Thread safety contract.</b> {@link #capture} must be called on the
 * server thread; {@link #run} may be called from any thread. Everything the
 * snapshot holds is either immutable or already used concurrently by vanilla
 * worldgen:
 * <ul>
 *   <li>{@link BiomeSource#possibleBiomes()} is memoised behind a
 *       {@code Suppliers.memoize} and returns an immutable set — we copy the
 *       filtered subset anyway.</li>
 *   <li>{@link Climate.Sampler} comes from {@code randomState()} and is what
 *       chunk generation samples from worker threads every tick.</li>
 *   <li>{@link BiomeSource#getNoiseBiome} is a pure function of the sampler and
 *       the coordinates.</li>
 * </ul>
 * No {@code ServerPlayer}, no level mutation, no chunk access happens here —
 * chunk loading and block reads stay on the server thread in
 * {@code LocationCondition#refineSpawn}.
 */
public final class BiomeSpawnSearch {

    private BiomeSpawnSearch() {}

    private static final AtomicBoolean NEVER_CANCELLED = new AtomicBoolean(false);

    /**
     * Immutable, thread-confinement-free description of one biome search.
     *
     * @param biomeSource   the dimension's biome source (stateless w.r.t. sampling)
     * @param climate       the dimension's climate sampler
     * @param matches       pre-filtered set of acceptable biome holders; identity-compared, exactly as vanilla does
     * @param originX/Y/Z   block-space search centre
     * @param minY/maxY     inclusive vertical band
     */
    public record Query(BiomeSource biomeSource,
                        Climate.Sampler climate,
                        Set<Holder<Biome>> matches,
                        int originX, int originY, int originZ,
                        int minY, int maxY) {
    }

    /**
     * Snapshots everything the search needs off a live level. Call on the server thread.
     *
     * <p>Returns empty when no biome in the dimension's palette satisfies the
     * predicate — this preserves vanilla's fast path (
     * {@code BiomeSource#findClosestBiome3d} returns null immediately for an
     * empty candidate set), so "the pack asks for a biome this dimension does
     * not have" stays a zero-cost miss rather than a full spiral walk.
     */
    public static Optional<Query> capture(ServerLevel level, BlockPos searchOrigin, Predicate<Holder<Biome>> predicate) {
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Set<Holder<Biome>> matches = source.possibleBiomes().stream()
            .filter(predicate)
            .collect(Collectors.toUnmodifiableSet());
        if (matches.isEmpty()) return Optional.empty();

        // Mirrors vanilla's band. 26.1 renamed the height accessors:
        // BiomeSource#findClosestBiome3d feeds Mth.outFromOrigin with
        // (level.getMinY() + 1, level.getMaxY() + 1), and outFromOrigin's
        // upper bound is inclusive. Numerically identical to the 1.21.1
        // branch's minBuildHeight + 1 .. maxBuildHeight (getMaxY() is
        // inclusive here where getMaxBuildHeight() was exclusive there),
        // so the search band is the same -63..321 for the overworld.
        final int minY = level.getMinY() + 1;
        final int maxY = level.getMaxY() + 1;

        // Vanilla feeds the origin Y through Mth.outFromOrigin, which CLAMPS it
        // into [minY, maxY]; BoundedBiomeSpiral instead returns no levels at all
        // when the origin sits outside the band (see verticalLevels). That
        // difference is invisible on the 1.21.1 branch, whose search centre is
        // getSharedSpawnPos() (Y ~64), but this branch has no getSharedSpawnPos()
        // and searches from BlockPos.ZERO — and both the Nether and the End have
        // getMinY() == 0, so their bands start at Y = 1 and an unclamped Y = 0
        // origin would make every search in them a zero-sample miss (blazeling,
        // enderian, cave/forest dragon). Clamp here so the spiral sees exactly
        // what vanilla would have seen, and BoundedBiomeSpiral stays byte-identical
        // to lead.
        final int originY = Math.max(minY, Math.min(maxY, searchOrigin.getY()));

        return Optional.of(new Query(
            source,
            level.getChunkSource().randomState().sampler(),
            matches,
            searchOrigin.getX(), originY, searchOrigin.getZ(),
            minY, maxY));
    }

    /**
     * Runs the bounded spiral. Safe to call off the server thread.
     *
     * @param cancelled polled periodically; flipping it true aborts the walk
     */
    public static BoundedBiomeSpiral.Result run(Query query, AtomicBoolean cancelled) {
        return BoundedBiomeSpiral.search(
            query.originX(), query.originY(), query.originZ(),
            query.minY(), query.maxY(),
            BoundedBiomeSpiral.RADIUS_BLOCKS,
            BoundedBiomeSpiral.HORIZONTAL_STEP,
            BoundedBiomeSpiral.VERTICAL_STEP,
            BoundedBiomeSpiral.BUDGET_MILLIS,
            System::currentTimeMillis,
            cancelled::get,
            (x, y, z) -> query.matches().contains(query.biomeSource().getNoiseBiome(
                QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), query.climate())));
    }

    /**
     * Synchronous convenience for the legacy {@code LocationCondition#locateSpawn}
     * entry point. Still bounded and still budgeted — it just has nothing to
     * cancel it. Prefer {@link AsyncSpawnLocator} for anything on a hot path.
     */
    public static Optional<BlockPos> findSync(ServerLevel level, BlockPos searchOrigin, Predicate<Holder<Biome>> predicate) {
        Optional<Query> query = capture(level, searchOrigin, predicate);
        if (query.isEmpty()) return Optional.empty();
        BoundedBiomeSpiral.Result result = run(query.get(), NEVER_CANCELLED);
        if (!result.found()) return Optional.empty();
        return Optional.of(new BlockPos(result.x(), result.y(), result.z()));
    }
}
