package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #133: the slime bounce stopped a running player dead.
 *
 * <p>The launch vector was built from {@code player.getDeltaMovement()}, which a
 * remote {@code ServerPlayer} never has written by client movement — so the
 * horizontal terms were a stale zero, and the velocity packet that publishes the
 * bounce REPLACES client motion rather than adding to it.
 */
// hub: neoorigins/bounce-momentum.md
class BounceOnLandMomentumTest {

    @BeforeAll
    static void loadRealData() {
        RealDatapack.load();
    }

    /** Shipped slime_bounce.json values: restitution 0.8, min 0.63, max 1.6. */
    private static BounceOnLandPower.Config shipped() {
        return new BounceOnLandPower.Config(0.8, 0.63, 1.6, "neoorigins:bounce_on_land");
    }

    /** A sprinting step is ~0.28 blocks/tick; the sign of the axis must survive too. */
    private static final double SPRINT = 0.28;

    @Test
    void runningPlayerKeepsHorizontalSpeedThroughTheBounce() {
        // Airborne step: moving +X while falling fast. Landing tick: Y truncated
        // by ground contact, horizontal still full.
        Vec3 lastDelta = new Vec3(SPRINT, -0.75, 0.0);
        Vec3 nowDelta = new Vec3(SPRINT, -0.10, 0.0);

        Vec3 launch = BounceOnLandPower.launchVelocity(lastDelta, nowDelta, shipped());

        assertNotNull(launch, "a 0.75 b/t impact is over min_velocity 0.63");
        assertEquals(SPRINT, launch.x, 1e-9, "horizontal momentum must survive the bounce (#133)");
        assertEquals(0.0, launch.z, 1e-9);
        assertTrue(launch.y > 0, "the bounce is upward");
    }

    @Test
    void bothHorizontalAxesAndNegativeDirectionsSurvive() {
        Vec3 lastDelta = new Vec3(-0.21, -0.80, 0.13);
        Vec3 nowDelta = new Vec3(-0.21, -0.05, 0.13);

        Vec3 launch = BounceOnLandPower.launchVelocity(lastDelta, nowDelta, shipped());

        assertNotNull(launch);
        assertEquals(-0.21, launch.x, 1e-9, "negative X must not be dropped or flipped");
        assertEquals(0.13, launch.z, 1e-9, "Z must be carried independently of X");
    }

    @Test
    void horizontalComesFromTheLandingTickNotThePreviousOne() {
        // The two ticks disagree: the player was sprinting, then hit a wall (or
        // let go) on the tick they landed. The bounce must carry the speed they
        // actually have now, not the one they had a tick ago.
        Vec3 lastDelta = new Vec3(0.28, -0.90, 0.0);
        Vec3 nowDelta = new Vec3(0.05, -0.10, 0.0);

        Vec3 launch = BounceOnLandPower.launchVelocity(lastDelta, nowDelta, shipped());

        assertNotNull(launch);
        assertEquals(0.05, launch.x, 1e-9,
            "horizontal must read the landing tick (nowDelta), not the previous one");
    }

    @Test
    void verticalUsesTheUntruncatedPreviousStep() {
        // The whole point of preferring lastDelta: the landing tick's own Y step
        // is clipped at ground contact, so reading it would under-launch.
        Vec3 lastDelta = new Vec3(0.0, -1.00, 0.0);
        Vec3 nowDelta = new Vec3(0.0, -0.02, 0.0);

        Vec3 launch = BounceOnLandPower.launchVelocity(lastDelta, nowDelta, shipped());

        assertNotNull(launch);
        // impact 1.00 * restitution 0.8 = 0.80, under the 1.6 cap.
        assertEquals(0.80, launch.y, 1e-9);
    }

    @Test
    void restitutionScalesTheLaunchAndTheCapBinds() {
        // 0.8 impact * 0.8 = 0.64. (0.5 would now sit under the 0.63 floor.)
        assertEquals(0.64,
            BounceOnLandPower.launchVelocity(new Vec3(0, -0.8, 0), Vec3.ZERO, shipped()).y, 1e-9);
        // Terminal-velocity fall: 3.0 * 0.8 = 2.4, clamped to max_velocity 1.6.
        assertEquals(1.60,
            BounceOnLandPower.launchVelocity(new Vec3(0, -3.0, 0), Vec3.ZERO, shipped()).y, 1e-9);
    }

    @Test
    void impactUnderMinVelocityDoesNotBounce() {
        // A sprint-jump landing: 0.3077 on the last airborne step, under the
        // 0.63 floor on both samples — no launch at all.
        assertNull(BounceOnLandPower.launchVelocity(
            new Vec3(SPRINT, -JUMP, 0.0), new Vec3(SPRINT, -0.0767, 0.0), shipped()));
    }

    @Test
    void minVelocityIsInclusiveAtTheBoundary() {
        assertNotNull(BounceOnLandPower.launchVelocity(
            new Vec3(0, -0.63, 0), Vec3.ZERO, shipped()),
            "min_velocity is a floor the impact may sit exactly on");
    }

    // ---- the #133 threshold, measured against what launchVelocity compares ----
    //
    // impact = max(-lastDelta.y, -nowDelta.y), and both are per-tick POSITION
    // deltas. Tick order is gravity, move, then the 0.98 drag multiply, so a
    // position delta is velocity after gravity and BEFORE drag; lastDelta is the
    // last complete airborne step. Re-derive the table with
    //   v -= 0.08; posDelta = v; v *= 0.98
    // falling from rest, landing on the tick whose cumulative descent reaches the
    // height. Reading the velocity field instead is 2% low and a tick out of phase.

    /** Last airborne step of a plain jump (v0 0.42) landing back on flat ground. */
    private static final double JUMP = 0.3077;
    /** Last airborne step of a fall of N blocks from rest. */
    private static final double DROP_1 = 0.3105;
    private static final double DROP_2 = 0.4566;
    private static final double DROP_3 = 0.5969;
    private static final double DROP_4 = 0.6650;
    private static final double DROP_5 = 0.7971;

    private static boolean bounces(BounceOnLandPower.Config config, double impact) {
        return BounceOnLandPower.launchVelocity(
            new Vec3(0, -impact, 0), Vec3.ZERO, config) != null;
    }

    /**
     * The reporter's ask — jumps stop bouncing, 4-block falls keep bouncing —
     * pinned against the REAL shipped slime_bounce.json, so both a codec-default
     * edit and an overriding value added to the JSON are caught.
     */
    @Test
    void shippedThresholdBouncesAFourBlockFallAndNothingShorter() {
        BounceOnLandPower.Config real = realSlimeBounce();

        assertEquals(0.63, real.minVelocity(), 1e-9,
            "the shipped floor must stay inside the measured window (0.5969, 0.6650]");

        assertFalse(bounces(real, JUMP),   "a plain jump must not bounce");
        assertFalse(bounces(real, DROP_1), "a 1-block drop must not bounce");
        assertFalse(bounces(real, DROP_2), "a 2-block drop must not bounce");
        assertFalse(bounces(real, DROP_3), "a 3-block fall must not bounce");
        assertTrue(bounces(real, DROP_4),  "a 4-block fall MUST bounce (#133)");
        assertTrue(bounces(real, DROP_5),  "a 5-block fall must bounce");
    }

    /**
     * Guards the window from the other side: 0.70 — and anything above the
     * 4-block impact — silently turns the reporter's own example off.
     */
    @Test
    void aThresholdAboveTheFourBlockImpactRejectsTheFourBlockFall() {
        BounceOnLandPower.Config tooHigh =
            new BounceOnLandPower.Config(0.8, 0.70, 1.6, "neoorigins:bounce_on_land");

        assertFalse(bounces(tooHigh, DROP_4),
            "0.70 sits above the 4-block impact 0.6650 — it rejects the fall #133 asks to keep");
        assertTrue(bounces(tooHigh, DROP_5),
            "at 0.70 the shortest bouncing fall would be 5 blocks");
    }

    /** The shipped power, loaded through the real datapack. */
    private static BounceOnLandPower.Config realSlimeBounce() {
        var holder = PowerDataManager.INSTANCE.getPower(
            Identifier.fromNamespaceAndPath("neoorigins", "slime_bounce"));
        assertNotNull(holder, "neoorigins:slime_bounce did not load at all");
        return assertInstanceOf(BounceOnLandPower.Config.class, holder.config(),
            "slime_bounce is not a bounce_on_land power any more");
    }
}
