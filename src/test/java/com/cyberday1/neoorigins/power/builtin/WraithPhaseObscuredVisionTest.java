package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** wraith_phase {@code obscured_vision} reaches the client as a capability tag. */
class WraithPhaseObscuredVisionTest {

    private static WraithPhasePower.Config config(String body) {
        return new WraithPhasePower().codec()
            .parse(JsonOps.INSTANCE, JsonParser.parseString(body)).getOrThrow();
    }

    private static boolean hasVisionCap(WraithPhasePower.Config c) {
        return new WraithPhasePower().capabilities(c).stream()
            .anyMatch(s -> s.startsWith(WraithPhasePower.OBSCURED_VISION_PREFIX));
    }

    @Test
    void offByDefault() {
        var c = config("{}");
        assertFalse(c.obscuredVision());
        assertEquals(1.5f, c.phaseViewDistance());
        assertFalse(hasVisionCap(c), "clear sight publishes nothing new");
        assertTrue(new WraithPhasePower().capabilities(c).contains("wall_phase"));
    }

    @Test
    void obscuredVisionCarriesItsDistance() {
        var c = config("{\"obscured_vision\": true, \"phase_view_distance\": 2.5}");
        var caps = new WraithPhasePower().capabilities(c);
        assertTrue(caps.contains("wall_phase"));
        assertTrue(caps.contains(WraithPhasePower.OBSCURED_VISION_PREFIX + "2.5"));
        assertEquals(2.5f, com.cyberday1.neoorigins.client.VisualEffectsHandler.parseViewDistance("2.5"));
        assertEquals(1.5f, com.cyberday1.neoorigins.client.VisualEffectsHandler.parseViewDistance("x"),
            "a value that does not parse keeps the default");
    }
}
