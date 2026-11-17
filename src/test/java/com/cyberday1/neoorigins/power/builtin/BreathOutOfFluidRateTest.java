package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The breath_out_of_fluid half of issue #150: {@code air_loss_per_second} is a
 * rate in air points out of 300 and drains at exactly that rate. It used to
 * become {@code 20 / value} whole ticks between drains, so 3 drained 3.3 a
 * second, 7 drained 10, and every value from 11 to 20 drained 20.
 */
class BreathOutOfFluidRateTest {

    /** Stand-in for {@code [ocean_origins] drain_rate_ticks}. */
    private static final int CONFIG = 10;

    private static BreathOutOfFluidPower.Config decode(String json) {
        return BreathOutOfFluidPower.Config.CODEC.parse(JsonOps.INSTANCE,
            JsonParser.parseString(json).getAsJsonObject()).getOrThrow();
    }

    private static double perSecond(String json) {
        return decode(json).airPerTick(CONFIG) * 20.0;
    }

    @Test
    void airLossPerSecondIsExact() {
        assertEquals(3.0, perSecond("{\"air_loss_per_second\": 3}"), 1e-9);
        assertEquals(7.0, perSecond("{\"air_loss_per_second\": 7}"), 1e-9);
        assertEquals(15.0, perSecond("{\"air_loss_per_second\": 15}"), 1e-9);
    }

    @Test
    void decimalsAndRatesAboveVanillaWork() {
        assertEquals(2.5, perSecond("{\"air_loss_per_second\": 2.5}"), 1e-9);
        assertEquals(60.0, perSecond("{\"air_loss_per_second\": 60}"), 1e-9);
    }

    @Test
    void theIntervalKeysAndTheConfigFallbackAreUnchanged() {
        // The built-in *_dries_out powers set none of the keys: one point per
        // config interval, as before.
        assertEquals(20.0 / CONFIG, perSecond("{\"fluid\": \"water\"}"), 1e-9);
        assertEquals(2.0, perSecond("{\"drain_interval_ticks\": 10}"), 1e-9);
        assertEquals(4.0, perSecond("{\"drain_rate\": 5}"), 1e-9);
    }

    @Test
    void zeroOrLessFallsBackToTheIntervalKeys() {
        assertEquals(2.0, perSecond("{\"air_loss_per_second\": 0, \"drain_interval_ticks\": 10}"), 1e-9);
        assertEquals(20.0 / CONFIG, perSecond("{\"air_loss_per_second\": -3}"), 1e-9);
    }
}
