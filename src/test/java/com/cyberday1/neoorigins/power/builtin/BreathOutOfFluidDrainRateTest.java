package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code neoorigins:breath_out_of_fluid} drain-field resolution contract.
 *
 * <p>Regression cover for a Discord report (RavynsEtc, 2026-08-06): every value
 * an author typed produced the same ~20s dry-out, because the handler
 * overwrote the parsed field with the global config unconditionally. The codec
 * had always parsed the field correctly, so a parse-only test would have passed
 * while the bug was live — what actually matters is that an authored value is
 * distinguishable from an omitted one, which is what {@link
 * BreathOutOfFluidPower#UNSET} exists to express.
 *
 * <p>The four built-in {@code *_dries_out} JSONs author no drain field at all
 * and must keep deferring to {@code [ocean_origins] drain_rate_ticks}. If a
 * literal default ever creeps back into the codec, {@link #omittedFieldsStayUnset}
 * fails — that is the guard against re-regressing issue #120, where the
 * built-ins would jump to 10 minutes of land time.
 */
class BreathOutOfFluidDrainRateTest {

    private static BreathOutOfFluidPower.Config decode(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return BreathOutOfFluidPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    // ── The reported bug ────────────────────────────────────────────────

    @Test
    void authoredDrainRateSurvivesParsing() {
        // The reporter's exact shape: a small drain_rate that used to be ignored.
        assertEquals(2, decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water", "drain_rate": 2 }
            """).drainIntervalTicks());
        assertEquals(20, decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water", "drain_rate": 20 }
            """).drainIntervalTicks());
    }

    @Test
    void distinctAuthoredValuesStayDistinct() {
        // The actual user-visible symptom was that different inputs collapsed
        // onto one behaviour, so assert they do not collapse.
        assertTrue(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_rate": 2 }
            """).drainIntervalTicks()
            != decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_rate": 20 }
            """).drainIntervalTicks());
    }

    // ── #120 guard ──────────────────────────────────────────────────────

    @Test
    void omittedFieldsStayUnset() {
        // Exactly how merling/siren/kraken/abyssal _dries_out.json are written.
        assertEquals(BreathOutOfFluidPower.UNSET, decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water" }
            """).drainIntervalTicks());
    }

    @Test
    void unsetIsNotAUsableInterval() {
        // The handler treats "> 0" as authored; UNSET must fall outside that.
        assertTrue(BreathOutOfFluidPower.UNSET <= 0);
    }

    // ── Priority ladder, mirroring BreathInFluidPower ────────────────────

    @Test
    void airLossPerSecondWinsOverBothAliases() {
        assertEquals(5, decode("""
            {
              "type": "neoorigins:breath_out_of_fluid",
              "air_loss_per_second": 4,
              "drain_interval_ticks": 99,
              "drain_rate": 77
            }
            """).drainIntervalTicks());
    }

    @Test
    void drainIntervalTicksWinsOverLegacyDrainRate() {
        assertEquals(99, decode("""
            {
              "type": "neoorigins:breath_out_of_fluid",
              "drain_interval_ticks": 99,
              "drain_rate": 77
            }
            """).drainIntervalTicks());
    }

    @Test
    void airLossPerSecondClampsToAtLeastOneTick() {
        // 20 / 40 would floor to 0 and make `tickCount % interval` divide by zero.
        assertEquals(1, decode("""
            { "type": "neoorigins:breath_out_of_fluid", "air_loss_per_second": 40 }
            """).drainIntervalTicks());
    }

    @Test
    void nonPositiveAuthoredValueClampsToOne() {
        assertEquals(1, decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_rate": 0 }
            """).drainIntervalTicks());
    }

    // ── Resolution: the step the original bug was missing ───────────────

    /** Stand-in for whatever {@code [ocean_origins] drain_rate_ticks} is set to. */
    private static final int CONFIG = 1;

    @Test
    void authoredValueBeatsTheConfig() {
        // THE reported bug: this used to return CONFIG no matter what.
        assertEquals(2, BreathOutOfFluidPower.resolveIntervalTicks(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_rate": 2 }
            """), CONFIG));
        assertEquals(20, BreathOutOfFluidPower.resolveIntervalTicks(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_rate": 20 }
            """), CONFIG));
    }

    @Test
    void omittedValueDefersToTheConfig() {
        // The #120 contract: built-in *_dries_out powers track the config.
        assertEquals(CONFIG, BreathOutOfFluidPower.resolveIntervalTicks(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water" }
            """), CONFIG));
        // ...and they track it wherever the admin moves it, not just at 1.
        assertEquals(37, BreathOutOfFluidPower.resolveIntervalTicks(decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "water" }
            """), 37));
    }

    @Test
    void authoredValueIsUnaffectedByConfigChanges() {
        var cfg = decode("""
            { "type": "neoorigins:breath_out_of_fluid", "drain_interval_ticks": 8 }
            """);
        assertEquals(8, BreathOutOfFluidPower.resolveIntervalTicks(cfg, 1));
        assertEquals(8, BreathOutOfFluidPower.resolveIntervalTicks(cfg, 1200));
    }

    // ── Unrelated fields keep working ───────────────────────────────────

    @Test
    void fluidDefaultsToWaterAndLavaIsAccepted() {
        assertEquals("water", decode("""
            { "type": "neoorigins:breath_out_of_fluid" }
            """).fluid());
        assertEquals("lava", decode("""
            { "type": "neoorigins:breath_out_of_fluid", "fluid": "lava" }
            """).fluid());
    }
}
