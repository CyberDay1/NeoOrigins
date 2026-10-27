package com.cyberday1.neoorigins.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lucky Trader's 10% discount has to survive the trip back to whole emeralds.
 * Plain rounding to nearest loses it entirely on the small trades: 5 x 0.9 is
 * 4.5, which rounds back up to 5, so costs 2 to 5 were never discounted at all.
 */
class TradeCostTest {

    private static final int MAX = 64;

    private static int discounted(int cost) {
        return TradeCost.rounded(cost * 0.9f, MAX);
    }

    @Test
    void anyCostOfTwoOrMoreDropsByAtLeastOne() {
        for (int cost = 2; cost <= MAX; cost++) {
            assertTrue(discounted(cost) <= cost - 1,
                "cost " + cost + " should lose at least one, got " + discounted(cost));
        }
    }

    @Test
    void theDocumentedExamples() {
        assertEquals(1, discounted(2));
        assertEquals(4, discounted(5));
        assertEquals(9, discounted(10));
        assertEquals(18, discounted(20));
        assertEquals(57, discounted(64));
    }

    /** Rounding to nearest alone is what the fix is for: 4.5 would go back up to 5. */
    @Test
    void theSmallTradesRoundingUsedToSwallow() {
        assertEquals(1, discounted(2));
        assertEquals(2, discounted(3));
        assertEquals(3, discounted(4));
        assertEquals(4, discounted(5));
    }

    @Test
    void anUnmodifiedCostIsUnchanged() {
        for (int cost = 1; cost <= MAX; cost++) {
            assertEquals(cost, TradeCost.rounded(cost, MAX), "unmodified cost " + cost);
        }
    }

    @Test
    void aCostNeverFallsBelowOneOrAboveTheStack() {
        assertEquals(1, discounted(1));
        assertEquals(1, TradeCost.rounded(0.0f, MAX));
        assertEquals(MAX, TradeCost.rounded(999.0f, MAX));
    }
}
