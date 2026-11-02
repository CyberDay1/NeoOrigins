package com.cyberday1.neoorigins.event;

import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An origin upgrade fires on the grant that completes its advancement, and only on that one. */
class UpgradeTriggerTest {

    private static AdvancementProgress progress(AdvancementRequirements req) {
        AdvancementProgress p = new AdvancementProgress();
        p.update(req);
        return p;
    }

    @Test
    void aSingleCriterionCompletesOnItsGrant() {
        AdvancementRequirements req = AdvancementRequirements.allOf(List.of("go"));
        AdvancementProgress p = progress(req);
        p.grantProgress("go");
        assertTrue(PlayerLifecycleEvents.justCompleted(req, p, "go"));
    }

    @Test
    void allOfCompletesOnlyOnTheLastGrant() {
        AdvancementRequirements req = AdvancementRequirements.allOf(List.of("a", "b"));
        AdvancementProgress p = progress(req);
        p.grantProgress("a");
        assertFalse(PlayerLifecycleEvents.justCompleted(req, p, "a"), "half done");
        p.grantProgress("b");
        assertTrue(PlayerLifecycleEvents.justCompleted(req, p, "b"));
    }

    @Test
    void aSpareGrantOnAFinishedAnyOfDoesNotFireAgain() {
        AdvancementRequirements req = AdvancementRequirements.anyOf(List.of("a", "b"));
        AdvancementProgress p = progress(req);
        p.grantProgress("a");
        assertTrue(PlayerLifecycleEvents.justCompleted(req, p, "a"));
        p.grantProgress("b");
        assertFalse(PlayerLifecycleEvents.justCompleted(req, p, "b"), "already done before b");
    }
}
