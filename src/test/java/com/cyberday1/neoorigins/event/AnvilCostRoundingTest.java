package com.cyberday1.neoorigins.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Efficient Repairs halves the anvil level cost. 1.21.1 rounds to nearest; 26.x
 * truncated, so a 3-level job cost 1 here and 2 there. Both now round.
 */
class AnvilCostRoundingTest {

    @Test
    void halvesRoundingToNearestWithAFloorOfOne() {
        assertEquals(2, CraftingPowerEvents.scaledAnvilCost(3, 0.5f));
        assertEquals(5, CraftingPowerEvents.scaledAnvilCost(10, 0.5f));
        assertEquals(1, CraftingPowerEvents.scaledAnvilCost(1, 0.5f));
        assertEquals(1, CraftingPowerEvents.scaledAnvilCost(1, 0.0f));
    }
}
