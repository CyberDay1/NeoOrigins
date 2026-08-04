package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code neoorigins:ignore_fluid} parse + capability-emission contract.
 *
 * <p>These tests deliberately touch <em>only</em> the codec and
 * {@code capabilities(Config)}. That path must stay registry-free — it runs
 * during datapack load and login sync, where an unknown or modded fluid id has
 * to degrade to a no-op instead of throwing — so it must be exercisable with no
 * Minecraft bootstrap at all. If a future change makes these tests need a live
 * registry, that IS the regression.
 */
class IgnoreFluidParseTest {

    private static final IgnoreFluidPower POWER = new IgnoreFluidPower();

    private static IgnoreFluidPower.Config decode(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return IgnoreFluidPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    private static Set<String> caps(String json) {
        return POWER.capabilities(decode(json));
    }

    // ── Parsing ─────────────────────────────────────────────────────────

    @Test
    void singleIdParses() {
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": "minecraft:lava" }
            """);
        assertEquals(List.of("minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void arrayUnderSingularKeyParses() {
        // Authors will try `"fluid": [...]` — the singular key must accept an array.
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": ["minecraft:water", "minecraft:lava"] }
            """);
        assertEquals(List.of("minecraft:water", "minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void arrayUnderPluralKeyParses() {
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluids": ["minecraft:water", "minecraft:lava"] }
            """);
        assertEquals(List.of("minecraft:water", "minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void singleIdUnderPluralKeyParses() {
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluids": "minecraft:lava" }
            """);
        assertEquals(List.of("minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void bothKeysMergeAndDeduplicate() {
        var cfg = decode("""
            {
              "type": "neoorigins:ignore_fluid",
              "fluid": "minecraft:lava",
              "fluids": ["minecraft:water", "minecraft:lava"]
            }
            """);
        assertEquals(List.of("minecraft:lava", "minecraft:water"), cfg.resolvedEntries());
    }

    @Test
    void bareNameGetsMinecraftNamespace() {
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": "lava" }
            """);
        assertEquals(List.of("minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void fluidTagEntryIsKept() {
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": "#c:milk" }
            """);
        assertEquals(List.of("#c:milk"), cfg.resolvedEntries());
    }

    @Test
    void noFluidGivenDefaultsToWaterAndLava() {
        // A marker-only entry that silently did nothing would be a footgun.
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid" }
            """);
        assertEquals(List.of("minecraft:water", "minecraft:lava"), cfg.resolvedEntries());
    }

    // ── Degradation: never throw on bad or unknown input ────────────────

    @Test
    void unknownModdedIdParsesAndSurvivesToACapability() {
        // The mod may simply not be installed. Parsing must not throw, and the
        // entry must still be carried — it becomes a capability that no live
        // fluid ever matches, i.e. a clean no-op.
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": "somemod:liquid_starlight" }
            """);
        assertEquals(List.of("somemod:liquid_starlight"), cfg.resolvedEntries());
        assertTrue(POWER.capabilities(cfg).contains("ignore_fluid:somemod:liquid_starlight"));
    }

    @Test
    void malformedEntriesAreDroppedNotThrown() {
        var cfg = decode("""
            {
              "type": "neoorigins:ignore_fluid",
              "fluids": ["Minecraft:Lava", "  ", "not a fluid id", ":", "#", "minecraft:"]
            }
            """);
        // Case is normalised; everything unusable is dropped rather than raising.
        assertEquals(List.of("minecraft:lava"), cfg.resolvedEntries());
    }

    @Test
    void allEntriesMalformedYieldsMarkerOnly() {
        // Degrades to "ignores nothing" — not an exception, and not the
        // water+lava default (the author DID name fluids, they were just junk).
        var cfg = decode("""
            { "type": "neoorigins:ignore_fluid", "fluid": "not a fluid id" }
            """);
        assertTrue(cfg.resolvedEntries().isEmpty());
        assertEquals(Set.of("ignore_fluid"), POWER.capabilities(cfg));
    }

    // ── Capability strings ──────────────────────────────────────────────

    @Test
    void capabilitiesAreMarkerPlusOnePerFluid() {
        assertEquals(
            Set.of("ignore_fluid", "ignore_fluid:minecraft:water", "ignore_fluid:minecraft:lava"),
            caps("""
                { "type": "neoorigins:ignore_fluid", "fluids": ["minecraft:water", "minecraft:lava"] }
                """));
    }

    @Test
    void singleFluidEmitsOnlyThatFluidsCapability() {
        Set<String> caps = caps("""
            { "type": "neoorigins:ignore_fluid", "fluid": "minecraft:lava" }
            """);
        assertEquals(Set.of("ignore_fluid", "ignore_fluid:minecraft:lava"), caps);
        assertFalse(caps.contains("ignore_fluid:minecraft:water"));
    }

    @Test
    void tagCapabilityKeepsTheHashPrefix() {
        assertTrue(caps("""
            { "type": "neoorigins:ignore_fluid", "fluid": "#c:milk" }
            """).contains("ignore_fluid:#c:milk"));
    }

    @Test
    void markerIsAlwaysPresentSoTheHotMixinGateWorks() {
        // The mixins test the bare marker first and only then resolve the fluid;
        // dropping it would make every ignore_fluid power a no-op.
        assertTrue(caps("""
            { "type": "neoorigins:ignore_fluid", "fluid": "minecraft:lava" }
            """).contains("ignore_fluid"));
    }

    @Test
    void ignoreWaterCapabilityIsNotEmitted() {
        // ignore_fluid supersedes ignore_water but must not impersonate it —
        // EntityIgnoreWaterMixin and the water-speed attribute stay that power's.
        assertFalse(caps("""
            { "type": "neoorigins:ignore_fluid", "fluid": "minecraft:water" }
            """).contains("ignore_water"));
    }
}
