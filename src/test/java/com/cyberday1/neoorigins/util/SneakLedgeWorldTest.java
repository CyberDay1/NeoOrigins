package com.cyberday1.neoorigins.util;

import com.cyberday1.neoorigins.rig.HeadlessWorld;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #134, end to end: a sneaking player with Step Assist must stop at a
 * one-block ledge instead of walking off it.
 *
 * <p>{@link SneakLedgeGuardTest} pins the guard's arithmetic and
 * {@code PlayerLedgeInjectionPointTest} pins the injection point. Neither runs the
 * game. This one does: a real {@code Player} in a real block world (see
 * {@link HeadlessWorld}) actually walks, through vanilla's own
 * {@code maybeBackOffFromEdge} with our mixin applied, and the assertion is about
 * where the player ends up.
 *
 * <p>The world is a stone platform whose lip is at {@code z = 1.0}, with a floor one
 * or two blocks lower beyond it. The player starts at {@code z = 1.0} — standing on
 * the lip — and asks to walk {@code +0.6} further out, which is more than vanilla
 * will ever grant.
 */
// hub: neoorigins/sneak-ledge-guard.md
class SneakLedgeWorldTest {

    /** Step Assist's amount: 0.6 base + 0.5 clears a full block. */
    private static final double STEP_ASSIST_BONUS = 0.5;
    private static final double WALK = 0.6;
    private static final double LIP_Z = 1.0;

    /** Platform top at y=0 (so its surface is y=1.0), edge at z=1.0. */
    private static HeadlessWorld platformWithDropOf(int blocks) {
        return new HeadlessWorld()
            .fill(-3, 0, -4, 3, 0, 0)
            .fill(-3, -blocks, 1, 3, -blocks, 5);
    }

    private static HeadlessWorld.RigPlayer playerOnLip(HeadlessWorld world) {
        return world.spawnAt(0.5, 1.0, LIP_Z);
    }

    private static void giveStepHeight(HeadlessWorld.RigPlayer player, String namespace) {
        AttributeInstance step = player.getAttribute(Attributes.STEP_HEIGHT);
        step.addPermanentModifier(new AttributeModifier(
            ResourceLocation.fromNamespaceAndPath(namespace, "rig_step_assist"),
            STEP_ASSIST_BONUS, AttributeModifier.Operation.ADD_VALUE));
    }

    @Test
    void aSneakingStepAssistedPlayerStopsAtAOneBlockLedge() {
        HeadlessWorld world = platformWithDropOf(1);
        HeadlessWorld.RigPlayer player = playerOnLip(world);
        giveStepHeight(player, "neoorigins");
        player.setShiftKeyDown(true);

        assertEquals(1.1f, player.maxUpStep(), 1.0E-5F,
            "the power must actually be raising step height, or this proves nothing");

        player.walk(0.0, WALK);

        assertTrue(player.getZ() < LIP_Z + WALK,
            "sneaking must clamp the walk, but the player moved the full distance to z="
                + player.getZ());
        assertTrue(world.standingOnSolidGround(player),
            "the player walked off the ledge — ended at z=" + player.getZ() + " with no floor");
    }

    @Test
    void aTwoBlockDropStopsThePlayerEvenWhenTheProbeIsNotNarrowed() {
        // The probe reaches 1.1 below the feet; a floor two blocks down is at 2.0,
        // so it was never in range and these drops always worked. Driven with a
        // FOREIGN step height so the guard does not apply and the depth stays 1.1.
        HeadlessWorld world = platformWithDropOf(2);
        HeadlessWorld.RigPlayer player = playerOnLip(world);
        giveStepHeight(player, "somemod");
        player.setShiftKeyDown(true);

        player.walk(0.0, WALK);

        assertTrue(world.standingOnSolidGround(player),
            "a two-block drop must stop a sneaking player regardless of step height");
    }

    @Test
    void notSneakingWalksOffTheLedgeExactlyAsBefore() {
        HeadlessWorld world = platformWithDropOf(1);
        HeadlessWorld.RigPlayer player = playerOnLip(world);
        giveStepHeight(player, "neoorigins");
        player.setShiftKeyDown(false);

        player.walk(0.0, WALK);

        assertEquals(LIP_Z + WALK, player.getZ(), 1.0E-6,
            "an upright player must not be clamped at all");
        assertFalse(world.standingOnSolidGround(player),
            "an upright player is supposed to walk off the edge");
    }

    @Test
    void aPlayerWithNoStepHeightPowerBehavesExactlyLikeVanilla() {
        HeadlessWorld world = platformWithDropOf(1);
        HeadlessWorld.RigPlayer player = playerOnLip(world);
        player.setShiftKeyDown(true);

        assertEquals(0.6f, player.maxUpStep(), 1.0E-5F, "vanilla step height");

        player.walk(0.0, WALK);

        assertTrue(player.getZ() < LIP_Z + WALK, "vanilla sneak stop must still engage");
        assertTrue(world.standingOnSolidGround(player), "vanilla sneaking must not fall");
    }

    @Test
    void aForeignModsStepHeightIsLeftAloneAndStillWalksOff() {
        // Not a wish — the guard is deliberately scoped to modifiers in our own
        // namespace, so another mod's step height keeps the behaviour it chose.
        // This is where the knife falls: if the ownership gate were dropped, this
        // player would be silently policed too.
        HeadlessWorld world = platformWithDropOf(1);
        HeadlessWorld.RigPlayer player = playerOnLip(world);
        giveStepHeight(player, "somemod");
        player.setShiftKeyDown(true);

        player.walk(0.0, WALK);

        assertEquals(LIP_Z + WALK, player.getZ(), 1.0E-6,
            "a foreign mod's step height must not be narrowed by us");
        assertFalse(world.standingOnSolidGround(player),
            "and so the player still walks off, as that mod intended");
    }
}
