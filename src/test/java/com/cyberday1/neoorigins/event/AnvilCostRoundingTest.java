package com.cyberday1.neoorigins.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * MOD_ANVIL_COST starts from the vanilla level cost and its result becomes the
 * new cost, as on 1.21.1. Efficient Repairs halves it, rounding to nearest with
 * a floor of 1, and an addition modifier adds levels rather than a multiplier.
 */
class AnvilCostRoundingTest {

    @Test
    void halvesRoundingToNearestWithAFloorOfOne() {
        assertEquals(2, CraftingPowerEvents.anvilCostFromModifier(3 * 0.5f));
        assertEquals(5, CraftingPowerEvents.anvilCostFromModifier(10 * 0.5f));
        assertEquals(1, CraftingPowerEvents.anvilCostFromModifier(1 * 0.5f));
        assertEquals(1, CraftingPowerEvents.anvilCostFromModifier(1 * 0.0f));
    }

    @Test
    void additionAddsLevelsToTheVanillaCost() {
        assertEquals(7, CraftingPowerEvents.anvilCostFromModifier(10 + -3f));
        assertEquals(1, CraftingPowerEvents.anvilCostFromModifier(2 + -5f));
    }
}
