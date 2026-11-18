package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.power.builtin.BreathOutOfFluidPower;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The per-tick resync only fires when this signature moves, so every synced tag a
 * client predicts movement from has to move it.
 */
class PhaseGateSignatureTest {

    @Test
    void aGatedIgnoreFluidFlipResyncsWhileAnotherPowerKeepsTheMarker() {
        // Unconditioned water plus night-gated lava: the lava gate shutting leaves
        // the marker set, so only the per-fluid tag shows the change.
        long both = NeoOriginsNetwork.phaseGateSignature(Set.of(
            "ignore_fluid", "ignore_fluid:minecraft:water", "ignore_fluid:minecraft:lava"));
        long waterOnly = NeoOriginsNetwork.phaseGateSignature(Set.of(
            "ignore_fluid", "ignore_fluid:minecraft:water"));
        assertNotEquals(both, waterOnly);
    }

    @Test
    void physicsModeTagsCountToo() {
        long both = NeoOriginsNetwork.phaseGateSignature(Set.of(
            "ignore_fluid_physics", "ignore_fluid_physics:minecraft:water", "ignore_fluid_physics:#c:milk"));
        long waterOnly = NeoOriginsNetwork.phaseGateSignature(Set.of(
            "ignore_fluid_physics", "ignore_fluid_physics:minecraft:water"));
        assertNotEquals(both, waterOnly);
    }

    @Test
    void theFlagBitsAreUnchanged() {
        assertEquals(1L, NeoOriginsNetwork.phaseGateSignature(Set.of("wall_phase")));
        assertEquals(8192L, NeoOriginsNetwork.phaseGateSignature(Set.of("ignore_water")));
        assertEquals(16384L, NeoOriginsNetwork.phaseGateSignature(Set.of(BreathOutOfFluidPower.DRAIN_CAPABILITY)));
        assertEquals(0L, NeoOriginsNetwork.phaseGateSignature(Set.of("ignore_fluid:minecraft:water")),
            "per-fluid tags are only read under their marker, as the mixins do");
    }
}
