package com.cyberday1.neoorigins.service;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/**
 * A bounded, cancellable re-implementation of vanilla's
 * {@code BiomeSource#findClosestBiome3d} spiral.
 *
 * <p><b>Why this exists.</b> Vanilla's version is all-or-nothing: it walks the
 * entire square spiral with no deadline, no cancellation and no sample cap,
 * then returns. Origin {@code spawn_location} specs used to drive it with
 * radius 12800 / horizontal step 16 / vertical step 32, which expands to
 * {@code floorDiv(12800, 16) = 800} cells of spiral radius — {@code (2*800+1)^2}
 * = 2,563,201 horizontal positions, each sampled at ~12 vertical levels, so up
 * to ~30.7 million climate samples in one synchronous call. Under vanilla
 * worldgen the spiral short-circuits within a few hundred blocks and nobody
 * notices; with a third-party biome mod that pushes (say) oceans far from world
 * spawn, the full walk runs and blows past the 60-second server watchdog, which
 * kills the server. See {@link AsyncSpawnLocator} for the off-thread wiring.
 *
 * <p>This class is deliberately free of any Minecraft types: it is pure integer
 * geometry plus a {@link Sampler} callback, an injectable clock and an
 * injectable cancellation flag, so the bounding and give-up logic is unit
 * testable without a game runtime.
 *
 * <p>Ordering matches vanilla's intent (nearest-first) but not its exact path:
 * vanilla traces a true spiral, this walks concentric Chebyshev rings. Both
 * visit ring <i>r</i> completely before ring <i>r+1</i>, so the "closest match
 * wins" property is preserved to ring granularity; only the tie-break order
 * within a ring differs.
 */
public final class BoundedBiomeSpiral {

    private BoundedBiomeSpiral() {}

    /**
     * Search radius in blocks. Matches vanilla {@code /locate biome}, which is
     * the closest thing to a sanctioned ceiling for "how far may a biome search
     * reasonably reach". The old value was 12800.
     */
    public static final int RADIUS_BLOCKS = 6400;

    /**
     * Horizontal sampling step in blocks — also vanilla {@code /locate biome}'s
     * value. Biomes are stored at 4-block (quart) resolution, so 32 is already
     * a coarse-but-honest sampling of the noise field. The old value was 16,
     * which quadrupled the horizontal position count for no practical gain
     * (biome regions are far larger than 32 blocks).
     */
    public static final int HORIZONTAL_STEP = 32;

    /** Vertical sampling step in blocks — vanilla {@code /locate biome}'s value. Old value was 32. */
    public static final int VERTICAL_STEP = 64;

    /**
     * Wall-clock budget for one search. Generous, because the search no longer
     * runs on the server thread — this is only the "the biome genuinely is not
     * out there, stop burning a core" backstop. A full 6400/32/64 overworld
     * walk is ~1.1M samples and finishes well inside this on any machine that
     * can run a server at all.
     */
    public static final long BUDGET_MILLIS = 5_000L;

    /**
     * How often the deadline / cancellation flag is consulted inside the
     * innermost loop. Power of two so the check is a mask, not a modulo. The
     * flags are additionally checked once per ring, which keeps small searches
     * responsive (and makes the budget path testable with tiny radii).
     */
    static final int CHECK_INTERVAL = 1024;

    /** Why the search stopped. */
    public enum Outcome {
        /** A matching sample was found; {@code x/y/z} on the result are meaningful. */
        FOUND,
        /** The whole bounded volume was walked without a match. */
        EXHAUSTED,
        /** {@link #BUDGET_MILLIS} elapsed first. */
        TIMED_OUT,
        /** The caller's cancellation flag went true (player logged out, server stopping, superseded). */
        CANCELLED
    }

    /** Search outcome plus the matching block position and the sample count actually spent. */
    public record Result(Outcome outcome, int x, int y, int z, long samples) {
        public boolean found() {
            return outcome == Outcome.FOUND;
        }
    }

    /** Tests whether the biome at a block position satisfies the caller's predicate. */
    @FunctionalInterface
    public interface Sampler {
        boolean matches(int blockX, int blockY, int blockZ);
    }

    /** Receives one horizontal cell offset; returns true to stop the walk. */
    @FunctionalInterface
    interface OffsetVisitor {
        boolean visit(int cellDx, int cellDz);
    }

    /** Spiral radius in cells, mirroring vanilla's {@code Math.floorDiv(radius, step)}. */
    public static int cellRadius(int radiusBlocks, int horizontalStep) {
        if (horizontalStep < 1) throw new IllegalArgumentException("horizontalStep must be >= 1");
        return Math.floorDiv(radiusBlocks, horizontalStep);
    }

    /** Number of horizontal positions a spiral of the given cell radius visits: {@code (2r+1)^2}. */
    public static long horizontalCellCount(int cellRadius) {
        if (cellRadius < 0) return 0L;
        long side = 2L * cellRadius + 1L;
        return side * side;
    }

    /** Upper bound on climate samples for a given configuration — the number this class caps. */
    public static long worstCaseSamples(int radiusBlocks, int horizontalStep, int verticalLevelCount) {
        return horizontalCellCount(cellRadius(radiusBlocks, horizontalStep)) * Math.max(0, verticalLevelCount);
    }

    /**
     * Vertical sample levels, ordered outward from {@code originY} and clamped
     * to {@code [minY, maxY]}. Equivalent in spirit to {@code Mth.outFromOrigin}
     * (which vanilla feeds the spiral): the origin level first, then alternating
     * up/down at multiples of {@code step}, skipping anything out of range and
     * stopping once both directions have left the band.
     *
     * <p>Returns an empty array when {@code originY} is outside the band, which
     * matches {@code Mth.outFromOrigin}'s empty-stream case.
     */
    public static int[] verticalLevels(int originY, int minY, int maxY, int step) {
        if (step < 1) throw new IllegalArgumentException("step must be >= 1");
        if (minY > maxY || originY < minY || originY > maxY) return new int[0];

        int span = maxY - minY;
        int capacity = span / step + 3;
        int[] out = new int[capacity];
        int n = 0;
        out[n++] = originY;
        for (int d = step; ; d += step) {
            boolean any = false;
            int up = originY + d;
            if (up <= maxY) {
                if (n == out.length) out = grow(out);
                out[n++] = up;
                any = true;
            }
            int down = originY - d;
            if (down >= minY) {
                if (n == out.length) out = grow(out);
                out[n++] = down;
                any = true;
            }
            if (!any) break;
        }
        int[] trimmed = new int[n];
        System.arraycopy(out, 0, trimmed, 0, n);
        return trimmed;
    }

    private static int[] grow(int[] src) {
        int[] bigger = new int[src.length * 2 + 1];
        System.arraycopy(src, 0, bigger, 0, src.length);
        return bigger;
    }

    /**
     * Walks one Chebyshev ring of cell offsets at radius {@code r}, calling
     * {@code visitor} exactly {@code 8r} times (once for {@code r == 0}) with no
     * duplicates. Returns true if the visitor asked to stop.
     */
    static boolean forEachRing(int r, OffsetVisitor visitor) {
        if (r < 0) return false;
        if (r == 0) return visitor.visit(0, 0);
        for (int dx = -r; dx <= r; dx++) {
            if (visitor.visit(dx, -r)) return true;
            if (visitor.visit(dx, r)) return true;
        }
        for (int dz = -r + 1; dz <= r - 1; dz++) {
            if (visitor.visit(-r, dz)) return true;
            if (visitor.visit(r, dz)) return true;
        }
        return false;
    }

    /**
     * Runs the bounded search.
     *
     * @param originX/originY/originZ block-space search centre (the dimension's shared spawn, normally)
     * @param minY/maxY              inclusive vertical band, normally {@code minBuildHeight+1 .. maxBuildHeight}
     * @param millisClock            monotonic millisecond clock — inject a fake in tests
     * @param cancelled              polled periodically; true aborts with {@link Outcome#CANCELLED}
     * @param sampler                the biome test; called from whatever thread invoked this method
     */
    public static Result search(int originX, int originY, int originZ,
                                int minY, int maxY,
                                int radiusBlocks, int horizontalStep, int verticalStep,
                                long budgetMillis,
                                LongSupplier millisClock,
                                BooleanSupplier cancelled,
                                Sampler sampler) {
        final int cells = cellRadius(radiusBlocks, horizontalStep);
        final int[] levels = verticalLevels(originY, minY, maxY, verticalStep);
        if (cells < 0 || levels.length == 0) {
            return new Result(Outcome.EXHAUSTED, 0, 0, 0, 0L);
        }

        final long deadline = millisClock.getAsLong() + budgetMillis;
        final long[] samples = {0L};
        final int[] hit = new int[3];
        final Outcome[] outcome = {Outcome.EXHAUSTED};

        final OffsetVisitor visitor = (cellDx, cellDz) -> {
            final int blockX = originX + cellDx * horizontalStep;
            final int blockZ = originZ + cellDz * horizontalStep;
            for (int y : levels) {
                if (samples[0] > 0 && (samples[0] & (CHECK_INTERVAL - 1)) == 0) {
                    if (cancelled.getAsBoolean()) {
                        outcome[0] = Outcome.CANCELLED;
                        return true;
                    }
                    if (millisClock.getAsLong() >= deadline) {
                        outcome[0] = Outcome.TIMED_OUT;
                        return true;
                    }
                }
                samples[0]++;
                if (sampler.matches(blockX, y, blockZ)) {
                    hit[0] = blockX;
                    hit[1] = y;
                    hit[2] = blockZ;
                    outcome[0] = Outcome.FOUND;
                    return true;
                }
            }
            return false;
        };

        for (int r = 0; r <= cells; r++) {
            // Per-ring check as well as the per-CHECK_INTERVAL one: keeps a
            // search that is cheap per ring (or a test with a tiny radius)
            // responsive to the deadline and to cancellation.
            if (cancelled.getAsBoolean()) {
                outcome[0] = Outcome.CANCELLED;
                break;
            }
            if (millisClock.getAsLong() >= deadline) {
                outcome[0] = Outcome.TIMED_OUT;
                break;
            }
            if (forEachRing(r, visitor)) break;
        }

        return new Result(outcome[0], hit[0], hit[1], hit[2], samples[0]);
    }
}
