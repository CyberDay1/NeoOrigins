package com.cyberday1.neoorigins.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #105: a resource bar that ships {@code start_value == max} was invisible
 * from spawn, because the HUD hid every full bar. 32 of the 33 shipped bars that
 * hide-when-full do exactly that.
 *
 * <p>The rule now keeps a full bar on screen until the resource has been spent
 * once this session. Because that is a one-term change to a boolean expression,
 * the arm that matters is the input where the old and new rules <b>disagree</b>.
 */
// hub: neoorigins/resource-bar-first-spend.md
class ResourceBarFirstSpendTest {

    /** The rule as it shipped before #105 — encoded so a no-op change cannot pass. */
    private static boolean drawnUnderOldRule(boolean alwaysShow, float fraction) {
        return alwaysShow || fraction < 1.0f;
    }

    private static boolean drawn(boolean alwaysShow, float fraction, boolean spent) {
        return ResourceBarVisibility.shouldDraw(alwaysShow, fraction, spent);
    }

    @BeforeEach
    void freshSession() {
        ResourceBarVisibility.reset();
    }

    // ---- the disagree-then-agree arm ----

    /**
     * The whole point of the change: a full, unspent, non-always_render bar. The
     * old rule hid it; the new rule draws it. If this ever agrees, the rule did
     * not change.
     */
    @Test
    void fullUnspentBarIsTheOneInputTheRulesDisagreeOn() {
        assertFalse(drawnUnderOldRule(false, 1.0f), "old rule hid a full bar");
        assertTrue(drawn(false, 1.0f, false), "new rule draws it until first spend");
    }

    /** Every other input must agree, or the change is wider than advertised. */
    @Test
    void theRulesAgreeOnEveryOtherInput() {
        for (boolean alwaysShow : new boolean[] {false, true}) {
            for (float fraction : new float[] {0.0f, 0.5f, 0.999f, 1.0f}) {
                for (boolean spent : new boolean[] {false, true}) {
                    boolean isTheDisagreeArm = !alwaysShow && fraction >= 1.0f && !spent;
                    if (isTheDisagreeArm) continue;
                    assertTrue(drawnUnderOldRule(alwaysShow, fraction) == drawn(alwaysShow, fraction, spent),
                        "rules must agree at alwaysShow=" + alwaysShow
                            + " fraction=" + fraction + " spent=" + spent);
                }
            }
        }
    }

    // ---- after the first spend, hide-when-full resumes ----

    @Test
    void afterFirstSpendAFullBarHidesAgain() {
        assertFalse(drawn(false, 1.0f, true), "a spent resource sitting at max hides");
    }

    @Test
    void aBarBelowFullAlwaysDraws() {
        assertTrue(drawn(false, 0.5f, false));
        assertTrue(drawn(false, 0.5f, true));
    }

    // ---- the two controls named in the issue ----

    /**
     * {@code voidwalker_energy} is the one shipped bar with
     * {@code always_render: true}. It must be drawn in every spend state.
     */
    @Test
    void voidwalkerAlwaysRenderKeepsWorking() {
        assertTrue(drawn(true, 1.0f, false), "always_render at full, unspent");
        assertTrue(drawn(true, 1.0f, true), "always_render at full, spent");
        assertTrue(drawn(true, 0.0f, true), "always_render at empty");
    }

    /**
     * {@code qi_resource} is the control: it ships 50/100, so it was already
     * visible and must behave identically — visible at 50, hidden once it fills.
     */
    @Test
    void qiResourceControlIsUnaffected() {
        float half = fraction(50, 0, 100);
        ResourceBarVisibility.observe("neoorigins:qi_resource", half);

        assertTrue(drawn(false, half, ResourceBarVisibility.hasBeenSpent("neoorigins:qi_resource")),
            "qi starts below max, so it draws immediately — as it already did");
        assertTrue(ResourceBarVisibility.hasBeenSpent("neoorigins:qi_resource"),
            "the same sync that shows it also marks it spent");

        float full = fraction(100, 0, 100);
        ResourceBarVisibility.observe("neoorigins:qi_resource", full);
        assertFalse(drawn(false, full, ResourceBarVisibility.hasBeenSpent("neoorigins:qi_resource")),
            "once qi fills it hides, exactly as before #105");
    }

    /**
     * A bar shipping {@code start_value == max} — 32 of the 33 — is visible on
     * arrival, then hides for good once spent and refilled.
     */
    @Test
    void aBarStartingAtMaxIsVisibleUntilItIsSpent() {
        String key = "neoorigins:voidwalker_energy_like";
        float full = fraction(80, 0, 80);

        ResourceBarVisibility.observe(key, full);
        assertTrue(drawn(false, full, ResourceBarVisibility.hasBeenSpent(key)),
            "never spent — the bar must be on screen (#105)");

        ResourceBarVisibility.observe(key, fraction(60, 0, 80));
        assertTrue(ResourceBarVisibility.hasBeenSpent(key), "dropping below max is the spend");

        assertFalse(drawn(false, full, ResourceBarVisibility.hasBeenSpent(key)),
            "regenerated back to max after a spend — hide-when-full resumes");
    }

    @Test
    void sessionResetReAdvertisesTheBar() {
        String key = "neoorigins:some_resource";
        ResourceBarVisibility.observe(key, 0.5f);
        assertTrue(ResourceBarVisibility.hasBeenSpent(key));

        ResourceBarVisibility.reset();

        assertFalse(ResourceBarVisibility.hasBeenSpent(key), "a new session forgets the spend");
        assertTrue(drawn(false, 1.0f, ResourceBarVisibility.hasBeenSpent(key)),
            "so a full bar is advertised once more after a relog");
    }

    @Test
    void spendMemoryIsPerResourceKey() {
        ResourceBarVisibility.observe("neoorigins:mana", 0.5f);

        assertTrue(ResourceBarVisibility.hasBeenSpent("neoorigins:mana"));
        assertFalse(ResourceBarVisibility.hasBeenSpent("neoorigins:stamina"),
            "spending one resource must not hide another origin's untouched bar");
    }

    @Test
    void observingAFullValueIsNotASpend() {
        String key = "neoorigins:rage";
        ResourceBarVisibility.observe(key, 1.0f);
        assertFalse(ResourceBarVisibility.hasBeenSpent(key),
            "a full-value sync must not count as a spend, or the bar hides immediately");
    }

    /** Mirrors {@code ClientResourceState.ResourceEntry#fraction()}. */
    private static float fraction(int value, int min, int max) {
        int range = max - min;
        if (range <= 0) return 1.0f;
        return Math.max(0, Math.min(1, (float) (value - min) / range));
    }
}
