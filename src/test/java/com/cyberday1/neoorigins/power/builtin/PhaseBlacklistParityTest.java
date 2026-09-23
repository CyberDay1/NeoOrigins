package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #109: the wraith phased through obsidian, spasmed and sank. All three
 * are one client/server disagreement, and the blacklist was the place the two
 * ends could disagree — the server parsed {@code blocked_blocks} while the
 * client re-parsed {@code phase_blocked:} capability tags, in separate code.
 *
 * <p>This walks the real wire: {@code Config} → {@code capabilities()} → the
 * capability strings that actually reach the client → the client's parse, and
 * asserts the result matches the server's own parse of the same config.
 */
// hub: neoorigins/phase-blacklist-parity.md
class PhaseBlacklistParityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static WraithPhasePower.Config config(List<String> blocked) {
        return new WraithPhasePower.Config(blocked, 0.15F, false, "neoorigins:wraith_phase", "", false);
    }

    /** The blacklist the client ends up with, via the capability tags it is sent. */
    private static PhaseBlacklist clientSide(WraithPhasePower.Config config) {
        Set<String> wire = new WraithPhasePower().capabilities(config);
        return PhaseBlacklist.fromCapabilities(wire);
    }

    /** The blacklist the server uses. */
    private static PhaseBlacklist serverSide(WraithPhasePower.Config config) {
        return WraithPhasePower.blacklist(config);
    }

    private static void assertEndsAgree(List<String> blocked) {
        WraithPhasePower.Config config = config(blocked);
        PhaseBlacklist server = serverSide(config);
        PhaseBlacklist client = clientSide(config);

        assertEquals(server.ids(), client.ids(),
            () -> "blocked ids disagree across the wire for " + blocked);
        assertEquals(server.tags(), client.tags(),
            () -> "blocked tags disagree across the wire for " + blocked);
        assertEquals(server.isEmpty(), client.isEmpty(),
            () -> "emptiness disagrees for " + blocked);
    }

    // ---- the shipped default, which is what #109 was reported against ----

    @Test
    void shippedDefaultAgreesOnBothEnds() {
        List<String> shipped =
            List.of("minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:bedrock");
        assertEndsAgree(shipped);

        PhaseBlacklist blacklist = serverSide(config(shipped));
        assertTrue(blacklist.ids().contains(ResourceLocation.parse("minecraft:obsidian")),
            "obsidian is listed — the reporter's block (#109)");
        assertEquals(3, blacklist.ids().size());
        assertTrue(blacklist.tags().isEmpty());
    }

    @Test
    void tagEntriesSurviveTheWire() {
        assertEndsAgree(List.of("#seer:anchor_protected"));

        PhaseBlacklist blacklist = serverSide(config(List.of("#seer:anchor_protected")));
        assertTrue(blacklist.ids().isEmpty(), "a #tag entry is not a literal id");
        assertEquals(
            List.of(TagKey.create(Registries.BLOCK, ResourceLocation.parse("seer:anchor_protected"))),
            blacklist.tags());
    }

    @Test
    void mixedIdsAndTagsAgree() {
        assertEndsAgree(List.of("minecraft:obsidian", "#seer:anchor_protected", "minecraft:bedrock"));
    }

    /**
     * The divergence that used to be possible: the server dropped blank entries
     * up front, the client handed {@code ""} to {@code tryParse}, which accepts
     * an empty path. One end carried a junk id the other did not.
     */
    @Test
    void blankAndMalformedEntriesAreDroppedOnBothEnds() {
        assertEndsAgree(List.of("", "   ", "NOT A BLOCK ID", "#", "minecraft:obsidian"));

        PhaseBlacklist blacklist = serverSide(
            config(List.of("", "   ", "NOT A BLOCK ID", "#", "minecraft:obsidian")));
        assertEquals(Set.of(ResourceLocation.parse("minecraft:obsidian")), blacklist.ids(),
            "only the one well-formed entry survives");
        assertTrue(blacklist.tags().isEmpty(), "'#' alone is not a tag");
    }

    @Test
    void anEmptyBlacklistIsEmptyOnBothEnds() {
        assertEndsAgree(List.of());
        assertTrue(serverSide(config(List.of())).isEmpty());
        assertFalse(serverSide(config(List.of("minecraft:obsidian"))).isEmpty());
    }

    @Test
    void duplicateEntriesCollapseIdentically() {
        assertEndsAgree(List.of("minecraft:obsidian", "minecraft:obsidian",
            "#seer:anchor_protected", "#seer:anchor_protected"));

        PhaseBlacklist blacklist = serverSide(config(
            List.of("minecraft:obsidian", "minecraft:obsidian",
                "#seer:anchor_protected", "#seer:anchor_protected")));
        assertEquals(1, blacklist.ids().size());
        assertEquals(1, blacklist.tags().size(), "a repeated tag must not be matched twice per block");
    }

    /**
     * A bare path gets the default namespace on both ends. Worth pinning because
     * the two parsers previously used different entry points
     * ({@code parse} vs {@code tryParse}) into that defaulting.
     */
    @Test
    void bareNamespaceDefaultsIdenticallyOnBothEnds() {
        assertEndsAgree(List.of("obsidian"));
        assertEquals(Set.of(ResourceLocation.parse("minecraft:obsidian")),
            serverSide(config(List.of("obsidian"))).ids());
    }

    // ---- the wire itself ----

    @Test
    void capabilitiesCarryWallPhasePlusOneEntryEach() {
        Set<String> caps = new WraithPhasePower().capabilities(
            config(List.of("minecraft:obsidian", "#seer:anchor_protected")));

        assertTrue(caps.contains("wall_phase"), "the phase capability itself must be present");
        assertTrue(caps.contains(PhaseBlacklist.CAPABILITY_PREFIX + "minecraft:obsidian"));
        assertTrue(caps.contains(PhaseBlacklist.CAPABILITY_PREFIX + "#seer:anchor_protected"));
        assertEquals(3, caps.size(), "wall_phase plus one tag per blacklist entry, nothing else");
    }

    /** Capability tags unrelated to the blacklist must not leak into it. */
    @Test
    void unrelatedCapabilitiesAreIgnored() {
        PhaseBlacklist blacklist = PhaseBlacklist.fromCapabilities(
            Set.of("wall_phase", "no_physics", "wall_climb",
                PhaseBlacklist.CAPABILITY_PREFIX + "minecraft:obsidian"));

        assertEquals(Set.of(ResourceLocation.parse("minecraft:obsidian")), blacklist.ids());
        assertTrue(blacklist.tags().isEmpty());
    }

    // ---- the noPhysics restore rule, which the two ends implemented differently ----

    /**
     * The asymmetry #109 turned on: the server exempted a player standing in a
     * blacklisted block, the client did not. Both branches of
     * {@code PlayerPhaseOverrideMixin} now call this one rule, so the exemption
     * cannot go missing from one of them again.
     */
    @Test
    void wallPhaseDoesNotRestoreNoPhysicsInsideABlockedBlock() {
        assertTrue(PhaseBlacklist.restoresNoPhysics(false, true, false),
            "ordinary phasing restores noPhysics");
        assertFalse(PhaseBlacklist.restoresNoPhysics(false, true, true),
            "inside obsidian it must stay off so collision pushes the player out (#109)");
    }

    /** {@code no_physics} (PhantomForm) ignores the blacklist by design, on both ends. */
    @Test
    void fullNoclipIgnoresTheBlacklist() {
        assertTrue(PhaseBlacklist.restoresNoPhysics(true, false, true));
        assertTrue(PhaseBlacklist.restoresNoPhysics(true, true, true));
    }

    @Test
    void withNoPhasePowerNothingIsRestored() {
        assertFalse(PhaseBlacklist.restoresNoPhysics(false, false, false));
        assertFalse(PhaseBlacklist.restoresNoPhysics(false, false, true));
    }

    /**
     * Pins the rule against the shape the client branch used to have. A test that
     * agreed with "fullNoclip || wallPhase" everywhere would not have noticed the
     * missing exemption.
     */
    @Test
    void theRuleDisagreesWithTheOldUnconditionalClientForm() {
        int disagreements = 0;
        for (boolean fullNoclip : new boolean[] {false, true}) {
            for (boolean wallPhase : new boolean[] {false, true}) {
                for (boolean inBlocked : new boolean[] {false, true}) {
                    boolean oldClientForm = fullNoclip || wallPhase;
                    boolean now = PhaseBlacklist.restoresNoPhysics(fullNoclip, wallPhase, inBlocked);
                    if (oldClientForm != now) {
                        disagreements++;
                        assertTrue(wallPhase && inBlocked && !fullNoclip,
                            "the only input the rules may differ on is wall_phase inside a blocked block");
                    }
                }
            }
        }
        assertEquals(1, disagreements,
            "exactly one input changed meaning: wall_phase, inside a blocked block, no full noclip");
    }
}
