package com.cyberday1.neoorigins.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards when the bone meal handler takes the interaction over, and how many
 * applications it owes once it does.
 *
 * <p>Regression cover for a Discord report: a Herbalist bone mealing a grass
 * block saw one plant appear and the rest only after a rejoin. The handler used
 * to grow the block from inside {@code BonemealEvent} and then let vanilla
 * carry on. For grass that is self-defeating: {@code GrassBlock}'s
 * {@code isValidBonemealTarget} asks whether {@code pos.above()} is still air,
 * the first extra application had just filled it, so vanilla returned PASS,
 * {@code CommonHooks} threw away every captured block snapshot unnotified, and
 * the same PASS also skipped vanilla's {@code stack.shrink(1)}. Crops and
 * saplings hid the bug because their target check reads the stale captured
 * state instead of the live world.
 *
 * <p>The two halves of the contract both matter. Standing aside has to stay the
 * default, because taking over a plain vanilla bone meal would put this mod in
 * the path of every player who has no such power. And the count has to include
 * the vanilla application that cancelling the event suppresses, or the buff
 * silently loses one growth per use.
 *
 * <p>A third half arrived with the fix rather than before it: taking the
 * interaction over made the handler capable of overriding a {@code cancel_event}
 * action, which is the one thing a pack can say that must always win.
 */
class BonemealExtraApplicationsTest {

    // A player with no bonemeal-extra power: vanilla must be left alone.

    @Test
    void noExtrasMeansNoTakeover() {
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(0, true, false),
            "with no extras granted there is nothing to add, so vanilla must handle the block");
    }

    @Test
    void negativeExtrasMeanNoTakeover() {
        // Round(chained) is clamped at the call site, but a modifier chain that
        // subtracts must never flip the handler into owning the interaction.
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(-3, true, false),
            "a negative extra count must not be read as a reason to take over");
    }

    // An invalid target: vanilla would have refused, so we must refuse too.

    @Test
    void extrasOnAnInvalidTargetMeanNoTakeover() {
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(1, false, false),
            "vanilla would have returned false here, so claiming success would eat the bone meal for nothing");
    }

    @Test
    void noExtrasOnAnInvalidTargetMeanNoTakeover() {
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(0, false, false),
            "neither condition holds, so the event must be left untouched");
    }

    // The Herbalist case: take over, and count the suppressed vanilla growth.

    @Test
    void oneExtraOnAValidTargetGrowsTwice() {
        // class_herbalist_green_thumb.json is add_base 1. Cancelling the event
        // suppresses vanilla's own application, so the handler owes 1 + 1.
        assertEquals(2, CraftingPowerEvents.resolveBonemealApplications(1, true, false),
            "the Herbalist's single extra plus the vanilla application it replaces is two growths");
    }

    @Test
    void largerExtraCountsKeepTheOffByOne() {
        assertEquals(4, CraftingPowerEvents.resolveBonemealApplications(3, true, false));
        assertEquals(11, CraftingPowerEvents.resolveBonemealApplications(10, true, false));
    }

    // A power that cancelled the event: refusing the interaction outranks
    // everything else, including a granted extra on a perfectly valid target.

    @Test
    void aCancelledEventMeansNoTakeoverEvenWithExtrasOnAValidTarget() {
        // The takeover went in without this check, so a cancel_event action on
        // the bonemeal trigger was overridden by any bonemeal-extra power: the
        // block grew, the bone meal was spent and the event was reported
        // successful, which is precisely what the pack asked not to happen.
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(1, true, true),
            "a power cancelled the interaction, so the handler must not grow, spend or claim success");
    }

    @Test
    void cancellationOutranksEveryExtraCount() {
        for (int extras = 1; extras <= 64; extras++) {
            assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(extras, true, true),
                "extras=" + extras + " must not defeat a cancelled event");
        }
    }

    @Test
    void cancellationHoldsOnAnInvalidTargetAndWithNoExtras() {
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(1, false, true));
        assertEquals(0, CraftingPowerEvents.resolveBonemealApplications(0, true, true));
    }

    @Test
    void everyValidExtraCountIsExactlyOneMoreThanGranted() {
        // Stated as the invariant rather than a table: the stand-in application
        // is the whole reason this method exists, and dropping it would be a
        // silent nerf that no parse-only test could see.
        for (int extras = 1; extras <= 64; extras++) {
            assertEquals(extras + 1, CraftingPowerEvents.resolveBonemealApplications(extras, true, false),
                "extras=" + extras + " must add the suppressed vanilla application");
        }
    }
}
