package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A {@code hud_render} colour that is not 6 or 8 hex digits falls back to the
 * default {@code #55AAFF} instead of being read as a near-transparent number.
 */
class ResourceHudColorTest {

    private static final int DEFAULT = 0xFF55AAFF;

    private static ResourcePower.Config decode(String hud) {
        JsonObject json = JsonParser.parseString("{\"min\": 0, \"max\": 10, \"hud_render\": " + hud + "}").getAsJsonObject();
        json.addProperty("_power_id", "test:bar");
        return new ResourcePower().codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @ParameterizedTest
    @ValueSource(strings = {"#FFF", "#FFFF", "#FFFFF", "#FFFFFFF", "#FFFFFFFFF", "#GGGGGG", "blue"})
    void invalidColourFallsBack(String bad) {
        var c = decode("{\"color\": \"" + bad + "\", \"tint\": \"" + bad + "\", \"tint_end\": \"" + bad + "\"}");
        assertEquals(DEFAULT, c.color());
        assertEquals(DEFAULT, c.tint());
        assertEquals(DEFAULT, c.tintEnd());
    }

    @Test
    void sixDigitColourIsOpaque() {
        var c = decode("{\"color\": \"#FF5500\", \"tint\": \"#00FF00\", \"tint_end\": \"#0000FF\"}");
        assertEquals(0xFFFF5500, c.color());
        assertEquals(0xFF00FF00, c.tint());
        assertEquals(0xFF0000FF, c.tintEnd());
    }

    @Test
    void eightDigitColourKeepsAlpha() {
        var c = decode("{\"color\": \"#80FF5500\", \"tint\": \"#00000000\"}");
        assertEquals(0x80FF5500, c.color());
        assertEquals(0, c.tint());
    }

    @Test
    void absentFieldsKeepTheirDefaults() {
        var c = decode("{}");
        assertEquals(DEFAULT, c.color());
        assertEquals(0, c.tint());
        assertEquals(0, c.tintEnd());
    }
}
