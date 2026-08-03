package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.condition.LocationCondition;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry guard for {@link SpawnChunkLoader#window}.
 *
 * <p>The whole non-blocking spawn refine rests on one claim: the chunks the
 * loader tickets and waits for are the chunks {@code refineSpawnPreloaded}
 * actually reads. If the window is ever too small, the refine silently skips
 * columns (a spawn that used to work stops working); if the spiral radius grows
 * without the window following, the same thing happens quietly. Both directions
 * are pure integer geometry, so both are checkable here — unlike the concurrency
 * and chunk-generation behaviour, which no unit test can reach.
 *
 * <p>Deliberately walks negative coordinates too: {@code >> 4} is an arithmetic
 * shift and rounds toward negative infinity, which is what makes the "every
 * in-chunk offset behaves the same" claim true on both sides of the origin.
 */
class SpawnChunkLoaderWindowTest {

    private static final int R = LocationCondition.SEARCH_RADIUS;

    private static Set<Long> windowOf(int blockX, int blockZ) {
        Set<Long> out = new HashSet<>();
        for (long packed : SpawnChunkLoader.window(blockX >> 4, blockZ >> 4)) out.add(packed);
        return out;
    }

    @Test
    void windowIsThreeByThreeForTheShippedRadius() {
        assertEquals(16, R, "window sizing below assumes a 16-block spiral");
        assertEquals(9, SpawnChunkLoader.window(0, 0).length);
    }

    @Test
    void windowIsCentredOnTheGivenChunk() {
        Set<Long> window = windowOf(0, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                assertTrue(window.contains(ChunkPos.asLong(dx, dz)),
                    "expected chunk " + dx + "," + dz + " in the window");
            }
        }
    }

    /**
     * The spiral itself — every position out to the full radius — must land
     * inside the window, from any in-chunk offset, on either side of the origin.
     */
    @Test
    void everySpiralPositionFallsInsideTheWindow() {
        for (int baseX : new int[] {-4096, -16, 0, 16, 4096}) {
            for (int offX = 0; offX < 16; offX++) {
                for (int offZ = 0; offZ < 16; offZ++) {
                    int centreX = baseX + offX;
                    int centreZ = baseX + offZ;
                    Set<Long> window = windowOf(centreX, centreZ);
                    for (int dx = -R; dx <= R; dx++) {
                        for (int dz = -R; dz <= R; dz++) {
                            long chunk = ChunkPos.asLong((centreX + dx) >> 4, (centreZ + dz) >> 4);
                            assertTrue(window.contains(chunk),
                                "spiral position " + dx + "," + dz + " from centre "
                                    + centreX + "," + centreZ + " escapes the preload window");
                        }
                    }
                }
            }
        }
    }

    /**
     * The land test reads one block beyond the spiral for its 3x3 clearance
     * check. Everything out to radius-1 keeps that read inside the window; only
     * the outermost shell can reach past it, and the preloaded refine skips
     * those columns rather than dragging another chunk column into generation.
     * Pinning both halves here so the trade-off cannot drift unnoticed.
     */
    @Test
    void clearanceReadsStayInsideTheWindowExceptOnTheOutermostShell() {
        for (int offX = 0; offX < 16; offX++) {
            for (int offZ = 0; offZ < 16; offZ++) {
                int centreX = offX;
                int centreZ = offZ;
                Set<Long> window = windowOf(centreX, centreZ);
                for (int dx = -(R - 1); dx <= R - 1; dx++) {
                    for (int dz = -(R - 1); dz <= R - 1; dz++) {
                        for (int nx = -1; nx <= 1; nx++) {
                            for (int nz = -1; nz <= 1; nz++) {
                                long chunk = ChunkPos.asLong(
                                    (centreX + dx + nx) >> 4, (centreZ + dz + nz) >> 4);
                                assertTrue(window.contains(chunk),
                                    "clearance read at " + (dx + nx) + "," + (dz + nz)
                                        + " from centre " + centreX + "," + centreZ
                                        + " escapes the preload window");
                            }
                        }
                    }
                }
            }
        }

        // And the documented gap: a centre at the far edge of its chunk, at the
        // outermost shell, reading one block further, does leave the window.
        int centreX = 15;
        Set<Long> window = windowOf(centreX, 0);
        assertFalse(window.contains(ChunkPos.asLong((centreX + R + 1) >> 4, 0)),
            "the known outermost-shell overspill is supposed to fall outside the window");
    }
}
