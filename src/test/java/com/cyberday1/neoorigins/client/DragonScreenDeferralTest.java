package com.cyberday1.neoorigins.client;

import com.cyberday1.neoorigins.client.DragonAltarSuppressor.Action;
import com.cyberday1.neoorigins.client.NeoOriginsClientConfig.DragonScreenPolicy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dragon Survival and NeoOrigins both open a screen at join, and the loser of
 * that race used to be silently replaced — the reported "it kicks you out of the
 * selector". These pin the three-way policy and, in particular, that a species
 * screen opened deliberately (no picker on screen) is never intercepted.
 */
class DragonScreenDeferralTest {

    private static Action decide(DragonScreenPolicy policy, boolean newIsSpecies, boolean newIsPicker,
                                 boolean currentIsSpecies, boolean currentIsPicker) {
        return DragonAltarSuppressor.decide(policy, newIsSpecies, newIsPicker,
                                            currentIsSpecies, currentIsPicker);
    }

    @Test
    void deferHoldsDragonScreenThatWouldTramplePicker() {
        assertEquals(Action.CANCEL_AND_STASH,
            decide(DragonScreenPolicy.DEFER, true, false, false, true));
    }

    @Test
    void deferKeepsDragonScreenThePickerItselfReplaces() {
        assertEquals(Action.STASH_ONLY,
            decide(DragonScreenPolicy.DEFER, false, true, true, false));
    }

    @Test
    void deferLeavesDeliberatelyOpenedDragonScreenAlone() {
        // From an altar block / inventory button / command: no picker on screen.
        assertEquals(Action.NONE, decide(DragonScreenPolicy.DEFER, true, false, false, false));
    }

    @Test
    void deferIgnoresUnrelatedScreens() {
        assertEquals(Action.NONE, decide(DragonScreenPolicy.DEFER, false, false, false, false));
        assertEquals(Action.NONE, decide(DragonScreenPolicy.DEFER, false, true, false, false));
    }

    @Test
    void suppressCancelsEveryDragonSpeciesScreenAndKeepsNone() {
        assertEquals(Action.CANCEL, decide(DragonScreenPolicy.SUPPRESS, true, false, false, true));
        assertEquals(Action.CANCEL, decide(DragonScreenPolicy.SUPPRESS, true, false, false, false));
        assertEquals(Action.NONE, decide(DragonScreenPolicy.SUPPRESS, false, true, true, false));
    }

    @Test
    void allowNeverInterferes() {
        assertEquals(Action.NONE, decide(DragonScreenPolicy.ALLOW, true, false, false, true));
        assertEquals(Action.NONE, decide(DragonScreenPolicy.ALLOW, false, true, true, false));
    }
}
