package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The tint_end gradient hits both colours exactly at the track ends and holds still as the bar fills. */
class ResourceBarGradientTest {

    private static final int W = 69;
    private static final int ICE = 0x9FE4FF;
    private static final int FROST = 0xE8FAFF;

    @Test
    void endpointsAreTheTwoColours() {
        assertEquals(0xFF000000 | ICE, ResourceBarGradient.at(ICE, FROST, 0, W));
        assertEquals(0xFF000000 | FROST, ResourceBarGradient.at(ICE, FROST, W - 1, W));
    }

    @Test
    void alphaInTheInputsIsIgnored() {
        assertEquals(0xFF000000 | ICE, ResourceBarGradient.at(0x80000000 | ICE, 0xFF000000 | FROST, 0, W));
        assertEquals(0xFF000000 | FROST, ResourceBarGradient.at(0x80000000 | ICE, 0xFF000000 | FROST, W - 1, W));
    }

    @Test
    void midpointIsHalfway() {
        // column 34 of 0..68 is exactly half: 254 / 2 = 127 on every channel
        assertEquals(0xFF7F7F7F, ResourceBarGradient.at(0x000000, 0xFEFEFE, 34, W));
    }

    @Test
    void sameColourBothEndsIsFlat() {
        for (int c = 0; c < W; c++) {
            assertEquals(0xFF000000 | ICE, ResourceBarGradient.at(ICE, ICE, c, W));
        }
    }

    @Test
    void outOfRangeColumnsClampToTheEnds() {
        assertEquals(0xFF000000 | ICE, ResourceBarGradient.at(ICE, FROST, -3, W));
        assertEquals(0xFF000000 | FROST, ResourceBarGradient.at(ICE, FROST, W + 5, W));
    }
}
