package com.cyberday1.neoorigins.evolution;

import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.cyberday1.neoorigins.evolution.EssenceEvolutionManager.shouldOfferEvolution;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reported bug: "when you decline an evolve, after you kill something you
 * get spammed by the message again".
 *
 * <p>The cause is that the prompt fires off a threshold rather than an edge.
 * {@code kills >= required} goes true on the kill that crosses the tier
 * threshold and then stays true for every kill after it, and {@code required}
 * only moves when the player ACCEPTS. Declining recorded nothing at all, so
 * there was never anything for the next kill to consult.
 *
 * <p>The declined tier is now persisted, which is what these tests cover: the
 * decision itself, and the save round-trip that has to carry it across a relog.
 */
class EvolutionDeclineSuppressionTest {

    /** Stands in for the configured tier-1 threshold. */
    private static final int REQUIRED = 50;

    private static final int NO_DECLINE = 0;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ── The bug as reported ─────────────────────────────────────────────

    /**
     * The spam itself, pinned as the diagnosis. Every one of these kills is past
     * the threshold, and with nothing recorded every one of them re-offers. If
     * this ever stops holding, the explanation for the report is wrong.
     */
    @Test
    void withoutADeclineEveryKillPastTheThresholdRePrompts() {
        for (int kills : new int[] {REQUIRED, REQUIRED + 1, REQUIRED + 2, REQUIRED + 500}) {
            assertTrue(shouldOfferEvolution(1, kills, REQUIRED, NO_DECLINE),
                "the threshold stays satisfied at " + kills + " kills, which is the spam the player saw");
        }
    }

    /** And the fix: the same kills, after declining tier 1. */
    @Test
    void decliningSuppressesEveryLaterKill() {
        for (int kills : new int[] {REQUIRED, REQUIRED + 1, REQUIRED + 2, REQUIRED + 500}) {
            assertFalse(shouldOfferEvolution(1, kills, REQUIRED, 1),
                "a declined tier must not re-offer at " + kills + " kills");
        }
    }

    // ── What the suppression must NOT swallow ───────────────────────────

    /**
     * The flag is per-tier, not a global mute. Declining tier 1 says nothing
     * about tier 2, so once the player has accepted tier 1 and climbed to the
     * next threshold the prompt has to arrive.
     */
    @Test
    void aDeclineOnlyCoversTheTierItWasGivenFor() {
        assertTrue(shouldOfferEvolution(2, REQUIRED, REQUIRED, 1),
            "declining Evolved must not mute Ascended");
        assertTrue(shouldOfferEvolution(3, REQUIRED, REQUIRED, 2),
            "declining Ascended must not mute Apex");
    }

    /** Below the threshold nothing is offered, declined or not. */
    @Test
    void thereIsNoPromptBeforeTheThreshold() {
        assertFalse(shouldOfferEvolution(1, REQUIRED - 1, REQUIRED, NO_DECLINE));
        assertFalse(shouldOfferEvolution(1, 0, REQUIRED, NO_DECLINE));
    }

    /** Apex is the top: there is no tier 4 to offer. */
    @Test
    void apexIsNeverOfferedAnythingFurther() {
        assertFalse(shouldOfferEvolution(4, Integer.MAX_VALUE, Integer.MAX_VALUE, NO_DECLINE),
            "a player at Apex has nothing left to evolve into");
    }

    // ── Surviving a relog ───────────────────────────────────────────────

    /**
     * The whole reason the flag is persisted rather than held in a session map:
     * a player who declines, logs out and logs back in must not be greeted by
     * the prompt again on their next kill.
     */
    @Test
    void theDeclinedTierSurvivesASaveLoadCycle() {
        PlayerOriginData data = new PlayerOriginData();
        data.setEssenceKills(REQUIRED + 10);
        data.setDeclinedEvolutionTier(1);

        PlayerOriginData loaded = roundTrip(data);

        assertEquals(1, loaded.getDeclinedEvolutionTier(),
            "the decline must survive a relog, or the spam comes straight back");
        assertFalse(shouldOfferEvolution(1, loaded.getEssenceKills(), REQUIRED,
                loaded.getDeclinedEvolutionTier()),
            "and still suppress the prompt after loading");
    }

    /**
     * Every save written before this fix lacks the key entirely. Those players
     * must load as "hasn't declined anything" and keep being offered their
     * evolution, rather than failing to decode or defaulting to a mute.
     */
    @Test
    void aSaveWrittenBeforeTheFieldExistedStillLoads() {
        PlayerOriginData data = new PlayerOriginData();
        data.setEssenceKills(REQUIRED + 10);
        data.setEvolutionTier(0);

        CompoundTag tag = (CompoundTag) PlayerOriginData.CODEC
            .encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        tag.remove("declined_evolution_tier");   // as written by <= 2.2.25

        PlayerOriginData loaded = PlayerOriginData.CODEC
            .parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertEquals(0, loaded.getDeclinedEvolutionTier(),
            "an absent key means the player has declined nothing");
        assertTrue(shouldOfferEvolution(1, loaded.getEssenceKills(), REQUIRED,
                loaded.getDeclinedEvolutionTier()),
            "so an existing save must still be offered its evolution");
    }

    /** The rest of the evolution state has to round-trip alongside it. */
    @Test
    void killsAndTierRoundTripUnchanged() {
        PlayerOriginData data = new PlayerOriginData();
        data.setEssenceKills(123);
        data.setEvolutionTier(2);
        data.setDeclinedEvolutionTier(3);

        PlayerOriginData loaded = roundTrip(data);

        assertEquals(123, loaded.getEssenceKills());
        assertEquals(2, loaded.getEvolutionTier());
        assertEquals(3, loaded.getDeclinedEvolutionTier());
    }

    // ── Clearing the flag ───────────────────────────────────────────────

    /**
     * A stale flag is the mirror-image bug: it would mute a prompt the player
     * never turned down. Wiping the evolution (the Orb of Origin path) has to
     * take the decline with it.
     */
    @Test
    void resettingEvolutionClearsTheDecline() {
        PlayerOriginData data = new PlayerOriginData();
        data.setEssenceKills(REQUIRED + 10);
        data.setEvolutionTier(1);
        data.setDeclinedEvolutionTier(2);

        data.resetEvolution();

        assertEquals(0, data.getDeclinedEvolutionTier(),
            "a reset player starts over, so nothing may still be muted");
        assertEquals(0, data.getEssenceKills());
        assertEquals(0, data.getEvolutionTier());
    }

    /** Clamped to the tier range, like {@code setEvolutionTier} beside it. */
    @Test
    void theDeclinedTierIsClamped() {
        PlayerOriginData data = new PlayerOriginData();
        data.setDeclinedEvolutionTier(99);
        assertEquals(3, data.getDeclinedEvolutionTier());
        data.setDeclinedEvolutionTier(-5);
        assertEquals(0, data.getDeclinedEvolutionTier());
    }

    private static PlayerOriginData roundTrip(PlayerOriginData data) {
        Tag tag = PlayerOriginData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        return PlayerOriginData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }
}
