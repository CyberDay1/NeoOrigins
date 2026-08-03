package com.cyberday1.neoorigins.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression net for the server-killing {@code spawn_location} biome search.
 *
 * <p>The shipped bug: {@code LocationCondition.locateSpawn} drove vanilla's
 * {@code findClosestBiome3d} with radius 12800 / horizontal step 16 / vertical
 * step 32. That is {@code floorDiv(12800,16) = 800} cells of spiral radius,
 * {@code (2*800+1)^2 = 2,563,201} horizontal positions, each sampled at ~12
 * vertical levels — up to ~30.7 million climate samples, synchronously, on the
 * server thread, with no deadline and no cancellation. Under vanilla worldgen
 * the spiral short-circuits within a few hundred blocks; with a third-party
 * biome mod that pushes oceans far from world spawn it runs to completion and
 * overruns the 60-second watchdog, killing the server.
 *
 * <p>These tests pin the three properties that stop that recurring: the
 * parameters stay bounded, the walk is complete and nearest-first (so clamping
 * the radius did not silently break correctness), and the search actually
 * honours a deadline and a cancellation flag.
 *
 * <p>Pure integer geometry — no Minecraft runtime involved. See the report notes
 * on what this deliberately does <i>not</i> cover.
 */
class BoundedBiomeSpiralTest {

    /** A clock that never advances — for tests that must not time out. */
    private static final LongSupplier FROZEN_CLOCK = () -> 0L;
    private static final BooleanSupplier NEVER_CANCELLED = () -> false;

    // ── Parameter bounding ──────────────────────────────────────────────

    @Test
    void searchParametersStayBounded() {
        // Pin the constants: these are vanilla /locate biome's own values
        // (LocateCommand: findClosestBiome3d(predicate, pos, 6400, 32, 64)),
        // which is the defensible ceiling for "how far may a biome search reach".
        assertEquals(6400, BoundedBiomeSpiral.RADIUS_BLOCKS);
        assertEquals(32, BoundedBiomeSpiral.HORIZONTAL_STEP);
        assertEquals(64, BoundedBiomeSpiral.VERTICAL_STEP);
        assertTrue(BoundedBiomeSpiral.BUDGET_MILLIS > 0,
            "a search with no wall-clock budget is the bug we are fixing");
        assertTrue(BoundedBiomeSpiral.BUDGET_MILLIS <= 30_000L,
            "budget must stay far below the 60s server watchdog even if this ever ran inline");
    }

    @Test
    void cellRadiusMatchesVanillaFloorDiv() {
        assertEquals(800, BoundedBiomeSpiral.cellRadius(12800, 16), "the shipped (broken) parameters");
        assertEquals(200, BoundedBiomeSpiral.cellRadius(6400, 32), "the clamped parameters");
        assertEquals(2_563_201L, BoundedBiomeSpiral.horizontalCellCount(800));
        assertEquals(160_801L, BoundedBiomeSpiral.horizontalCellCount(200));
    }

    @Test
    void clampedParametersAreOrdersOfMagnitudeCheaperThanTheShippedBug() {
        // Overworld band: minBuildHeight + 1 .. maxBuildHeight, centred on a
        // typical spawn Y.
        int oldLevels = BoundedBiomeSpiral.verticalLevels(64, -63, 320, 32).length;
        long oldWorstCase = BoundedBiomeSpiral.worstCaseSamples(12800, 16, oldLevels);

        int newLevels = BoundedBiomeSpiral.verticalLevels(64, -63, 320, BoundedBiomeSpiral.VERTICAL_STEP).length;
        long newWorstCase = BoundedBiomeSpiral.worstCaseSamples(
            BoundedBiomeSpiral.RADIUS_BLOCKS, BoundedBiomeSpiral.HORIZONTAL_STEP, newLevels);

        assertTrue(oldWorstCase > 25_000_000L,
            "sanity: the shipped parameters really were tens of millions of samples, got " + oldWorstCase);
        assertTrue(newWorstCase < 1_500_000L,
            "clamped worst case must stay near vanilla /locate biome's, got " + newWorstCase);
        assertTrue(newWorstCase * 20 < oldWorstCase,
            "expected at least a 20x reduction, got " + oldWorstCase + " -> " + newWorstCase);
    }

    // ── Vertical level generation ───────────────────────────────────────

    @Test
    void verticalLevelsMatchVanillaOutFromOriginForTheOverworld() {
        // Hand-traced against Mth.outFromOrigin(64, -63, 320, 64): the set is
        // identical, which is what matters — only the emission order differs.
        int[] levels = BoundedBiomeSpiral.verticalLevels(64, -63, 320, 64);
        assertEquals(Set.of(64, 128, 0, 192, 256, 320), toSet(levels));
        assertEquals(6, levels.length, "no duplicates");
        assertEquals(64, levels[0], "origin level is sampled first");
    }

    @Test
    void verticalLevelsStayInsideTheBandAndAreOrderedOutward() {
        int[] levels = BoundedBiomeSpiral.verticalLevels(50, 0, 100, 20);
        for (int y : levels) {
            assertTrue(y >= 0 && y <= 100, "level " + y + " escaped the band");
        }
        assertEquals(levels.length, toSet(levels).size(), "no duplicates");
        int previousDistance = -1;
        for (int y : levels) {
            int distance = Math.abs(y - 50);
            assertTrue(distance >= previousDistance,
                "levels must be ordered outward from the origin, saw " + y + " after distance " + previousDistance);
            previousDistance = distance;
        }
    }

    @Test
    void verticalLevelsAreEmptyWhenTheOriginIsOutsideTheBand() {
        assertEquals(0, BoundedBiomeSpiral.verticalLevels(500, 0, 100, 16).length);
        assertEquals(0, BoundedBiomeSpiral.verticalLevels(-500, 0, 100, 16).length);
        assertEquals(0, BoundedBiomeSpiral.verticalLevels(50, 100, 0, 16).length, "inverted band");
    }

    @Test
    void verticalLevelsRejectNonPositiveStep() {
        assertThrows(IllegalArgumentException.class, () -> BoundedBiomeSpiral.verticalLevels(64, 0, 100, 0));
    }

    // ── Ring / spiral geometry ──────────────────────────────────────────

    @Test
    void ringVisitsExactlyItsPerimeterWithNoDuplicates() {
        for (int r = 0; r <= 6; r++) {
            List<Long> visited = new ArrayList<>();
            int ring = r;
            BoundedBiomeSpiral.forEachRing(r, (dx, dz) -> {
                assertEquals(ring, Math.max(Math.abs(dx), Math.abs(dz)),
                    "offset (" + dx + "," + dz + ") is not on ring " + ring);
                visited.add(key(dx, dz));
                return false;
            });
            int expected = r == 0 ? 1 : 8 * r;
            assertEquals(expected, visited.size(), "ring " + r + " perimeter size");
            assertEquals(expected, new HashSet<>(visited).size(), "ring " + r + " has duplicates");
        }
    }

    @Test
    void ringStopsImmediatelyWhenTheVisitorSaysStop() {
        int[] calls = {0};
        boolean stopped = BoundedBiomeSpiral.forEachRing(5, (dx, dz) -> {
            calls[0]++;
            return true;
        });
        assertTrue(stopped);
        assertEquals(1, calls[0]);
    }

    @Test
    void fullWalkCoversTheEntireSquareExactlyOnce() {
        int radius = 12;
        Set<Long> visited = new HashSet<>();
        for (int r = 0; r <= radius; r++) {
            BoundedBiomeSpiral.forEachRing(r, (dx, dz) -> {
                assertTrue(visited.add(key(dx, dz)), "duplicate offset (" + dx + "," + dz + ")");
                return false;
            });
        }
        assertEquals(BoundedBiomeSpiral.horizontalCellCount(radius), visited.size());
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                assertTrue(visited.contains(key(dx, dz)), "missed offset (" + dx + "," + dz + ")");
            }
        }
    }

    // ── Search outcomes ─────────────────────────────────────────────────

    @Test
    void searchFindsAMatchAndStopsEarly() {
        // Target sits 3 cells east of the origin at step 16 => x = 48.
        BoundedBiomeSpiral.Result result = search(200, 1_000_000L, FROZEN_CLOCK, NEVER_CANCELLED,
            (x, y, z) -> x == 48 && z == 0);

        assertEquals(BoundedBiomeSpiral.Outcome.FOUND, result.outcome());
        assertTrue(result.found());
        assertEquals(48, result.x());
        assertEquals(0, result.z());
        assertTrue(result.samples() < 5_000L,
            "should have stopped a few rings in, spent " + result.samples() + " samples");
    }

    @Test
    void searchPrefersTheNearerOfTwoMatches() {
        // One match on ring 2 (x = 32), one on ring 40 (x = 640).
        BoundedBiomeSpiral.Result result = search(200, 1_000_000L, FROZEN_CLOCK, NEVER_CANCELLED,
            (x, y, z) -> z == 0 && (x == 32 || x == 640));
        assertEquals(BoundedBiomeSpiral.Outcome.FOUND, result.outcome());
        assertEquals(32, result.x(), "nearest-first ordering must survive the ring rewrite");
    }

    @Test
    void searchExhaustsExactlyTheBoundedVolumeWhenNothingMatches() {
        int radius = 8;
        int[] levels = BoundedBiomeSpiral.verticalLevels(64, 0, 128, 64);
        BoundedBiomeSpiral.Result result = BoundedBiomeSpiral.search(
            0, 64, 0, 0, 128,
            radius * 16, 16, 64,
            1_000_000L, FROZEN_CLOCK, NEVER_CANCELLED,
            (x, y, z) -> false);

        assertEquals(BoundedBiomeSpiral.Outcome.EXHAUSTED, result.outcome());
        assertFalse(result.found());
        assertEquals(BoundedBiomeSpiral.horizontalCellCount(radius) * levels.length, result.samples(),
            "every cell x level pair sampled exactly once, and not one more");
    }

    @Test
    void searchGivesUpWhenTheBudgetElapses() {
        // Clock advances one unit per read; the per-ring check reads it once
        // per ring, so a 5-unit budget expires within the first handful of rings.
        AtomicLong ticks = new AtomicLong();
        BoundedBiomeSpiral.Result result = search(10_000, 5L, ticks::getAndIncrement, NEVER_CANCELLED,
            (x, y, z) -> false);

        assertEquals(BoundedBiomeSpiral.Outcome.TIMED_OUT, result.outcome());
        assertFalse(result.found());
        assertTrue(result.samples() < BoundedBiomeSpiral.horizontalCellCount(10_000),
            "must have bailed long before exhausting the volume");
    }

    @Test
    void searchAbortsWhenCancelled() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        AtomicLong sampled = new AtomicLong();
        BoundedBiomeSpiral.Result result = search(10_000, 1_000_000L, FROZEN_CLOCK, cancelled::get,
            (x, y, z) -> {
                if (sampled.incrementAndGet() >= 500) cancelled.set(true);
                return false;
            });

        assertEquals(BoundedBiomeSpiral.Outcome.CANCELLED, result.outcome());
        assertFalse(result.found());
        assertTrue(result.samples() < 10_000L,
            "cancellation must be noticed promptly, spent " + result.samples() + " samples");
    }

    @Test
    void searchIsANoOpWhenTheVerticalBandExcludesTheOrigin() {
        BoundedBiomeSpiral.Result result = BoundedBiomeSpiral.search(
            0, 500, 0, 0, 128,
            6400, 32, 64,
            1_000_000L, FROZEN_CLOCK, NEVER_CANCELLED,
            (x, y, z) -> {
                throw new AssertionError("sampler must not be called");
            });
        assertEquals(BoundedBiomeSpiral.Outcome.EXHAUSTED, result.outcome());
        assertEquals(0L, result.samples());
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private static BoundedBiomeSpiral.Result search(int cellRadius, long budget,
                                                    LongSupplier clock, BooleanSupplier cancelled,
                                                    BoundedBiomeSpiral.Sampler sampler) {
        int step = 16;
        return BoundedBiomeSpiral.search(
            0, 64, 0, 0, 128,
            cellRadius * step, step, 64,
            budget, clock, cancelled, sampler);
    }

    private static long key(int dx, int dz) {
        return ((long) dx << 32) ^ (dz & 0xFFFFFFFFL);
    }

    private static Set<Integer> toSet(int[] values) {
        return new HashSet<>(Arrays.stream(values).boxed().toList());
    }
}
