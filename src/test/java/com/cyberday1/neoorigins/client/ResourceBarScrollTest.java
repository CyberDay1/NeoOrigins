package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The scroll offset wraps on the loop width, runs on game time and snaps to screen pixels. */
class ResourceBarScrollTest {

    @Test
    void oneSecondOfTicksMovesBySpeed() {
        assertEquals(16.0, ResourceBarScroll.offset(20, 16f, 125, 1.0), 1e-9);
    }

    @Test
    void wrapsOnLoopWidthNotTextureWidth() {
        // 125 px at 25 px/s is one loop in 5 s: the strip is back where it started.
        assertEquals(0.0, ResourceBarScroll.offset(100, 25f, 125, 1.0), 1e-9);
        assertEquals(1.0, ResourceBarScroll.offset(100, 25.2f, 125, 1.0), 1e-3);
    }

    @Test
    void negativeSpeedStaysInRange() {
        assertEquals(109.0, ResourceBarScroll.offset(20, -16f, 125, 1.0), 1e-9);
    }

    @Test
    void partialTickMovesBetweenTicks() {
        // GUI scale 4: a quarter GUI pixel is one screen pixel, so half a tick shows.
        double a = ResourceBarScroll.offset(100.0, 16f, 125, 4.0);
        double b = ResourceBarScroll.offset(100.5, 16f, 125, 4.0);
        assertEquals(0.25, b - a, 1e-9);
    }

    @Test
    void snapsToWholeScreenPixels() {
        for (int i = 0; i < 400; i++) {
            double off = ResourceBarScroll.offset(1234567.0 + i * 0.137, 13f, 125, 3.0);
            double screen = off * 3.0;
            assertEquals(Math.rint(screen), screen, 1e-6, "offset " + off + " is not on a screen pixel");
            assertTrue(off >= 0 && off < 125);
        }
    }

    @Test
    void largeGameTimeStillMovesBetweenTicks() {
        double base = 2_000_000_000.0; // about three years of game time
        double a = ResourceBarScroll.offset(base, 16f, 125, 4.0);
        double b = ResourceBarScroll.offset(base + 0.5, 16f, 125, 4.0);
        assertEquals(0.25, (b - a + 125) % 125, 1e-6);
    }
}
