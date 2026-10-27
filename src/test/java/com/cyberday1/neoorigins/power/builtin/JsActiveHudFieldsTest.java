package com.cyberday1.neoorigins.power.builtin;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code js_active} reads the three cooldown HUD fields every other active power
 * takes. They used to be dropped by its codec, so a script-driven power never
 * showed a cooldown icon.
 */
class JsActiveHudFieldsTest {

    private static JsCustomActivePower.Config decode(String json) {
        return JsCustomActivePower.Config.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
            .getOrThrow();
    }

    @Test
    void theHudFieldsAreRead() {
        var cfg = decode("""
            { "js_id": "dash", "cooldown_icon": "minecraft:ender_pearl",
              "cooldown_countdown": false, "always_show_icon": true }
            """);
        assertEquals("minecraft:ender_pearl", cfg.cooldownIcon());
        assertFalse(cfg.cooldownCountdown());
        assertTrue(cfg.alwaysShowIcon());
    }

    @Test
    void theDefaultsMatchTheOtherActives() {
        var cfg = decode("{ \"js_id\": \"dash\" }");
        assertEquals("", cfg.cooldownIcon());
        assertTrue(cfg.cooldownCountdown());
        assertFalse(cfg.alwaysShowIcon());
        assertEquals(20, cfg.cooldownTicks());
    }
}
