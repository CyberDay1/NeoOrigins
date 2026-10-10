package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ticks land on whole pixels, evenly to within one pixel, and follow the old density rule. */
class ResourceBarTicksTest {

    private static final int FRAMED_FILL = 69;

    @Test
    void gapsDifferByAtMostOnePixel() {
        for (int width : new int[] {60, FRAMED_FILL}) {
            for (int segments = 2; segments * 2 <= width; segments++) {
                for (int step : new int[] {1, 5, 25}) {
                    int max = segments * step;
                    int[] ticks = ResourceBarTicks.positions(0, max, step, width);
                    assertEquals(segments - 1, ticks.length, "width " + width + ", " + segments + " segments");
                    int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, prev = 0;
                    for (int i = 0; i <= ticks.length; i++) {
                        int next = i < ticks.length ? ticks[i] : width;
                        int gap = next - prev;
                        lo = Math.min(lo, gap);
                        hi = Math.max(hi, gap);
                        prev = next;
                    }
                    assertTrue(hi - lo <= 1, "width " + width + ", " + segments + " segments: gaps " + lo + ".." + hi);
                }
            }
        }
    }

    @Test
    void ticksStayInsideTheTrack() {
        for (int max = 10; max <= 400; max++) {
            for (int p : ResourceBarTicks.positions(0, max, ResourceBarTicks.MINOR_STEP, FRAMED_FILL)) {
                assertTrue(p > 0 && p < FRAMED_FILL, "max " + max + " put a tick at " + p);
            }
        }
    }

    @Test
    void hundredPointBarOnTheFramedFill() {
        // 69 px / 4 = 17.25: quarters at the nearest pixel.
        assertArrayEquals(new int[] {17, 35, 52}, ResourceBarTicks.positions(0, 100, 25, FRAMED_FILL));
        assertEquals(19, ResourceBarTicks.positions(0, 100, 5, FRAMED_FILL).length);
    }

    @Test
    void minIsTheOrigin() {
        assertArrayEquals(ResourceBarTicks.positions(0, 100, 25, FRAMED_FILL),
            ResourceBarTicks.positions(50, 150, 25, FRAMED_FILL));
    }

    @Test
    void tooDenseSeriesIsDropped() {
        // 300 units over 69 px is 1.15 px per minor step: unreadable, so no minor ticks,
        // while the majors (5.75 px apart) still draw. Same rule as the old renderer.
        assertEquals(0, ResourceBarTicks.positions(0, 300, 5, FRAMED_FILL).length);
        assertEquals(11, ResourceBarTicks.positions(0, 300, 25, FRAMED_FILL).length);
    }

    @Test
    void emptyOrBackwardsRangeHasNoTicks() {
        assertEquals(0, ResourceBarTicks.positions(10, 10, 5, FRAMED_FILL).length);
        assertEquals(0, ResourceBarTicks.positions(10, 0, 5, FRAMED_FILL).length);
        assertEquals(0, ResourceBarTicks.positions(0, 100, 0, FRAMED_FILL).length);
    }

    @Test
    void heightsOnTheSixPixelFill() {
        assertEquals(2, ResourceBarTicks.height(6, true));
        assertEquals(1, ResourceBarTicks.height(6, false));
        // the old 10 px box keeps its 3 and 2
        assertEquals(3, ResourceBarTicks.height(10, true));
        assertEquals(2, ResourceBarTicks.height(10, false));
    }
}
