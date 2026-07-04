package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.action.BiEntityAction;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: {@code neoorigins:action_on_hit} must honour the documented
 * {@code entity_action} / {@code bientity_action} field (ACTIONS.md), not just
 * the flat {@code action} schema. A pack author copied the poison-on-hit example
 * straight from the docs and it did nothing because the native codec dropped the
 * nested action and fell through to the {@code restore_health} default.
 */
class ActionOnHitBiEntityParseTest {

    private static ActionOnHitPower.Config decode(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return ActionOnHitPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    @Test
    void docExamplePoisonParsesToNonNoopAction() {
        // The exact shape from docs/ACTIONS.md + POWER_TYPES.md (Venom Strike).
        var cfg = decode("""
            {
              "type": "neoorigins:action_on_hit",
              "hidden": true,
              "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 }
            }
            """);
        assertNotSame(BiEntityAction.noop(), cfg.onHitAction(),
            "entity_action apply_effect must parse to a live bientity action, not noop");
        // With a bientity action supplied and no explicit `action`, the flat
        // schema defaults to "none" so it doesn't also silently self-heal.
        assertEquals("none", cfg.action());
    }

    @Test
    void bientityActionAliasAlsoParses() {
        var cfg = decode("""
            {
              "type": "neoorigins:action_on_hit",
              "bientity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison", "duration": 60 }
            }
            """);
        assertNotSame(BiEntityAction.noop(), cfg.onHitAction());
    }

    @Test
    void flatSchemaStillDefaultsAndWorks() {
        // No bientity field at all → 100% backward-compatible flat behaviour:
        // action defaults to restore_health, onHitAction is noop.
        var cfg = decode("""
            { "type": "neoorigins:action_on_hit", "target_group": "undead", "amount": 1.0 }
            """);
        assertEquals("restore_health", cfg.action());
        assertEquals(1.0f, cfg.amount());
        assertTrue(cfg.targetGroup().isPresent());
        assertEquals("undead", cfg.targetGroup().get());
        assertSame(BiEntityAction.noop(), cfg.onHitAction());
    }

    @Test
    void explicitFlatActionCoexistsWithBientity() {
        // Author explicitly wants both: keep the flat action AND run the bientity.
        var cfg = decode("""
            {
              "type": "neoorigins:action_on_hit",
              "action": "restore_health",
              "amount": 2.0,
              "entity_action": { "type": "neoorigins:apply_effect", "effect": "minecraft:poison" }
            }
            """);
        assertEquals("restore_health", cfg.action());
        assertNotSame(BiEntityAction.noop(), cfg.onHitAction());
    }
}
