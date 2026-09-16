package com.cyberday1.neoorigins.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #134, the sneak half: a sneaking player with Step Assist on walked off a
 * one-block ledge.
 *
 * <p>The tests come in two halves. The first pins the geometry that makes the bug
 * real and rules out the cheaper fixes; the second pins the guard's four
 * behavioural cases. Neither needs a world, because the only thing our code
 * decides is the number handed to vanilla's probe — everything downstream of that
 * is unmodified vanilla.
 */
// hub: neoorigins/sneak-ledge-guard.md
class SneakLedgeGuardTest {

    /** A player's step-height attribute base, from {@code Attributes.STEP_HEIGHT}. */
    private static final double VANILLA = 0.6;

    /** The same value in the float space vanilla's own local lives in. */
    private static final float VANILLA_F = 0.6F;

    /** {@code class_rogue_parkour} etc. add 0.5 on top of the base. */
    private static final float WITH_STEP_ASSIST = 1.1F;

    /** The fudge {@code canFallAtLeast} subtracts on the 26.x line. */
    private static final double EPSILON = 1.0E-7;

    /** Distance from the feet to the floor of a one-block drop. */
    private static final double ONE_BLOCK = 1.0;

    /**
     * {@code canFallAtLeast} clears a box reaching {@code depth + epsilon} below
     * the feet and answers "yes, you could fall" only when that box is empty. A
     * probe that reaches the floor of the drop therefore answers "no".
     */
    private static boolean probeSaysPlayerCouldFall(double depth, double dropHeight) {
        return depth + EPSILON < dropHeight;
    }

    // -- Half one: why the bug exists, and why the cheap fixes do not work --

    @Test
    void aStepHeightThatClearsABlockCannotSeeAOneBlockDrop() {
        assertFalse(probeSaysPlayerCouldFall(WITH_STEP_ASSIST, ONE_BLOCK),
            "1.1 probes past the floor one block down, so vanilla concludes there is "
                + "no ledge and never backs the player off — this is issue #134");
        assertTrue(probeSaysPlayerCouldFall(VANILLA, ONE_BLOCK),
            "0.6 stops short of that floor, so the drop reads as a drop and the "
                + "sneak stop engages");
    }

    /**
     * Ruling out "just lower the step height instead". Any value that clears a
     * full block necessarily hides a one-block drop, and the epsilon means even an
     * exact 1.0 is over the line.
     */
    @Test
    void loweringTheStepHeightToOneStillHidesTheDrop() {
        assertFalse(probeSaysPlayerCouldFall(1.0, ONE_BLOCK),
            "1.0 + epsilon already reaches the floor, so a 1.0 step height does not "
                + "fix this — the amount is not the lever");
    }

    /** The two-block case always worked; this is the regression guard for it. */
    @Test
    void aTwoBlockDropWasNeverHiddenAtEitherDepth() {
        assertTrue(probeSaysPlayerCouldFall(WITH_STEP_ASSIST, 2.0),
            "1.1 cannot reach a floor two blocks down, which is why deep drops "
                + "always stopped the player");
        assertTrue(probeSaysPlayerCouldFall(VANILLA, 2.0),
            "and the guard must not break the case that already worked");
    }

    // -- Half two: the guard's four cases --

    @Test
    void sneakingWithStepAssistProbesAtVanillaDepth() {
        float depth = SneakLedgeGuard.edgeProbeDepth(WITH_STEP_ASSIST, true, VANILLA, true);

        assertEquals(VANILLA_F, depth, 0.0F,
            "a sneaking player with our step height must probe at the unmodified base");
        assertTrue(probeSaysPlayerCouldFall(depth, ONE_BLOCK),
            "which is what makes the one-block sneak stop engage again");
        assertTrue(probeSaysPlayerCouldFall(depth, 2.0),
            "without breaking the two-block stop");
    }

    @Test
    void notSneakingIsLeftExactlyAlone() {
        assertEquals(WITH_STEP_ASSIST,
            SneakLedgeGuard.edgeProbeDepth(WITH_STEP_ASSIST, false, VANILLA, true), 0.0,
            "walking off a ledge upright is vanilla behaviour and must not change");
        assertFalse(probeSaysPlayerCouldFall(WITH_STEP_ASSIST, ONE_BLOCK),
            "so an upright player still steps down a one-block drop as before");
    }

    /**
     * Step Assist toggled off removes the attribute modifier entirely, so this is
     * also the "player with no step-height power" case: nothing of ours is
     * applied, and the guard must be a no-op even while sneaking.
     */
    @Test
    void aPlayerWithNoneOfOurStepHeightIsLeftExactlyAlone() {
        assertEquals(0.6F, SneakLedgeGuard.edgeProbeDepth(0.6F, true, VANILLA, false), 0.0,
            "vanilla sneak must stay bit-for-bit vanilla");
        assertEquals(1.1F, SneakLedgeGuard.edgeProbeDepth(1.1F, true, VANILLA, false), 0.0,
            "another mod's step height is not ours to clamp");
    }

    /**
     * A power that <em>lowers</em> step height below the base must keep its lower
     * value: clamping up would let a sneaking player probe deeper than they can
     * actually step.
     */
    @Test
    void aStepHeightBelowTheBaseIsNotRaisedToIt() {
        assertEquals(0.2F, SneakLedgeGuard.edgeProbeDepth(0.2F, true, VANILLA, true), 0.0,
            "the guard clamps down to the base, it does not clamp up to it");
    }

    /** The one property that makes the guard safe in cases nobody enumerated. */
    @Test
    void theGuardNeverProbesDeeperThanVanillaWould() {
        for (float original : new float[] {0.0F, 0.2F, 0.6F, 1.0F, 1.1F, 2.5F, 10.0F}) {
            for (boolean sneaking : new boolean[] {true, false}) {
                for (boolean ours : new boolean[] {true, false}) {
                    float depth = SneakLedgeGuard.edgeProbeDepth(original, sneaking, VANILLA, ours);
                    assertTrue(depth <= original,
                        "deepening the probe could only hide more drops: original=" + original
                            + " sneaking=" + sneaking + " ours=" + ours + " -> " + depth);
                }
            }
        }
    }
}
