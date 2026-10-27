package com.cyberday1.neoorigins.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A bare-hand tool (Miner's Stone Fists, Mason's Stone Hands) and a break-speed
 * bonus (Mining Expert, Practiced Swing) are two {@code BreakSpeed} listeners at the
 * same priority, so the bus may run them in either order. The bare-hand listener
 * used to replace the running speed, which dropped the bonus whenever it ran second.
 */
class BareHandBreakSpeedTest {

    private static final float STONE_PICKAXE_ON_STONE = 4.0f;
    private static final float MINING_EXPERT = 1.5f;

    @Test
    void bonusSurvivesEitherListenerOrder() {
        float empty = 1.0f; // vanilla's empty-hand base speed
        float bonusFirst = BareHandToolEvents.bareHandSpeed(empty * MINING_EXPERT, STONE_PICKAXE_ON_STONE);
        float bareFirst = BareHandToolEvents.bareHandSpeed(empty, STONE_PICKAXE_ON_STONE) * MINING_EXPERT;
        assertEquals(6.0f, bonusFirst, 1e-6);
        assertEquals(bonusFirst, bareFirst, 1e-6);
    }

    @Test
    void vanillaFactorsStillApplyLikeAHeldTool() {
        // Haste II (x1.4) already in the running speed carries through, as it would
        // with a real stone pickaxe in hand.
        assertEquals(5.6f, BareHandToolEvents.bareHandSpeed(1.4f, STONE_PICKAXE_ON_STONE), 1e-5);
        // Mining off the ground divides by 5 first; the tool still multiplies it.
        assertEquals(0.8f, BareHandToolEvents.bareHandSpeed(0.2f, STONE_PICKAXE_ON_STONE), 1e-6);
    }

    @Test
    void aBlockTheToolDoesNotSpeedUpIsLeftAlone() {
        assertEquals(1.5f, BareHandToolEvents.bareHandSpeed(1.5f, 1.0f), 1e-6);
    }
}
