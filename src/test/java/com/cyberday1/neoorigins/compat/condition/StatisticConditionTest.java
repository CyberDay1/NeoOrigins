package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #118: the Fairytale pack's Sleeping Beauty origin was
 * hidden by the min-power-ratio gate because three of its action_over_time
 * powers refused to compile — their gates used {@code origins:always_active}
 * and {@code origins:statistic}, neither of which had a descriptor. Both verbs
 * are registered here; {@code statistic} accepts the Apoli-canonical nested
 * {@code stat} object and the flat legacy {@code statistic} string alike, and
 * fails closed on anything it cannot resolve.
 *
 * <p>Registry-dependent resolution (turning an id into a live {@code Stat}) is
 * deliberately deferred to evaluation time, so everything asserted here runs
 * without a Minecraft bootstrap.
 */
class StatisticConditionTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ── registration ─────────────────────────────────────────────────────

    @Test
    void bothVerbsAreRegistered() {
        assertNotNull(BuiltinConditions.get("neoorigins:always_active"),
            "always_active must be a known condition verb");
        assertNotNull(BuiltinConditions.get("neoorigins:statistic"),
            "statistic must be a known condition verb");
    }

    @Test
    void bothVerbsAreInKnownTypes() {
        assertTrue(ConditionParser.KNOWN_TYPES.contains("neoorigins:always_active"));
        assertTrue(ConditionParser.KNOWN_TYPES.contains("neoorigins:statistic"));
    }

    // ── always_active ────────────────────────────────────────────────────

    /** The exact shape from fairytale's enhanced_strength.json. */
    @Test
    void alwaysActiveIsUnconditionallyTrue() {
        assertTrue(ConditionParser.parse(
            obj("{ \"type\": \"origins:always_active\" }"), "test:enhanced_strength").test(null),
            "always_active must evaluate true with no world or player context");
        assertTrue(ConditionParser.parse(
            obj("{ \"type\": \"always_active\" }"), "test:bare_name").test(null),
            "a bare type name canonicalises to neoorigins:always_active");
    }

    /** The universal inverted flag still applies to a marker condition. */
    @Test
    void alwaysActiveHonoursInverted() {
        assertFalse(ConditionParser.parse(
            obj("{ \"type\": \"origins:always_active\", \"inverted\": true }"), "test:id").test(null));
    }

    // ── statistic: shape reading ─────────────────────────────────────────

    /** The flat legacy string form used by the reported pack. */
    @Test
    void flatStatisticStringReads() {
        ConditionParser.StatRef ref = ConditionParser.readStatRef(obj("""
            {
              "type": "origins:statistic",
              "statistic": "minecraft:time_since_rest",
              "comparison": ">=",
              "compare_to": 24000
            }
            """));
        assertNotNull(ref, "a flat statistic string must be readable");
        assertEquals("minecraft:custom", ref.typeId().toString(),
            "the flat form implies the custom stat category");
        assertEquals("minecraft:time_since_rest", ref.statId().toString());
    }

    /** Apoli's canonical nested-object form. */
    @Test
    void nestedStatObjectReads() {
        ConditionParser.StatRef ref = ConditionParser.readStatRef(obj("""
            {
              "type": "apoli:statistic",
              "stat": { "type": "minecraft:custom", "stat": "minecraft:time_since_rest" },
              "comparison": ">=",
              "compare_to": 24000
            }
            """));
        assertNotNull(ref, "the nested stat object must be readable");
        assertEquals("minecraft:custom", ref.typeId().toString());
        assertEquals("minecraft:time_since_rest", ref.statId().toString());
    }

    /** Non-custom stat categories resolve through the same path. */
    @Test
    void nonCustomStatCategoriesRead() {
        ConditionParser.StatRef killed = ConditionParser.readStatRef(
            obj("{ \"stat\": { \"type\": \"minecraft:killed\", \"stat\": \"minecraft:zombie\" } }"));
        assertNotNull(killed);
        assertEquals("minecraft:killed", killed.typeId().toString());
        assertEquals("minecraft:zombie", killed.statId().toString());

        ConditionParser.StatRef mined = ConditionParser.readStatRef(
            obj("{ \"statistic\": { \"type\": \"mined\", \"stat\": \"stone\" } }"));
        assertNotNull(mined, "unqualified category and stat ids default to minecraft:");
        assertEquals("minecraft:mined", mined.typeId().toString());
        assertEquals("minecraft:stone", mined.statId().toString());
    }

    // ── statistic: fail-closed ───────────────────────────────────────────

    @Test
    void unreadableStatRefsAreRejected() {
        assertNull(ConditionParser.readStatRef(obj("{ \"comparison\": \">=\" }")),
            "a statistic condition with no stat field is unreadable");
        assertNull(ConditionParser.readStatRef(obj("{ \"statistic\": \"Not A Valid Id!\" }")),
            "a malformed resource location is unreadable");
        assertNull(ConditionParser.readStatRef(
            obj("{ \"stat\": { \"type\": \"minecraft:no_such_category\", \"stat\": \"minecraft:zombie\" } }")),
            "an unknown stat category is unreadable");
        assertNull(ConditionParser.readStatRef(obj("{ \"stat\": { \"type\": \"minecraft:custom\" } }")),
            "a nested object with no inner stat id is unreadable");
        assertNull(ConditionParser.readStatRef(obj("{ \"statistic\": [\"minecraft:jump\"] }")),
            "an array is not a stat reference");
    }

    /** An unreadable reference compiles to a constant false, never always-true. */
    @Test
    void unresolvableStatisticFailsClosed() {
        assertFalse(ConditionParser.parse(
            obj("{ \"type\": \"origins:statistic\", \"compare_to\": 1 }"), "test:no_stat").test(null),
            "a statistic condition with no stat must be false, not an open gate");
        assertFalse(ConditionParser.parse(
            obj("{ \"type\": \"origins:statistic\", \"statistic\": \"Bad Id\" }"), "test:bad_id").test(null));
        assertFalse(ConditionParser.parse(
            obj("{ \"type\": \"origins:statistic\", \"statistic\": { \"type\": \"minecraft:nope\", \"stat\": \"a:b\" } }"),
            "test:bad_category").test(null));
    }

    // ── comparison semantics ─────────────────────────────────────────────

    /**
     * The condition reuses the shared ComparisonType helper, so the operator
     * vocabulary (and the ">=" default the parser applies) behaves exactly as it
     * does for scoreboard / xp_level. 24000 ticks is the pack's threshold.
     */
    @Test
    void comparisonOperatorsBehave() {
        assertTrue(ComparisonType.fromString(">=").test(24000, 24000));
        assertTrue(ComparisonType.fromString(">=").test(24001, 24000));
        assertFalse(ComparisonType.fromString(">=").test(23999, 24000));
        assertTrue(ComparisonType.fromString("<").test(23999, 24000));
        assertTrue(ComparisonType.fromString("==").test(24000, 24000));
        assertTrue(ComparisonType.fromString("!=").test(1, 24000));
        assertTrue(ComparisonType.fromString(">").test(24001, 24000));
        assertTrue(ComparisonType.fromString("<=").test(24000, 24000));
    }

    /** A readable reference produces a real condition, not the fail-closed one. */
    @Test
    void readableStatisticCompilesToARealCondition() {
        EntityCondition cond = ConditionParser.parse(obj("""
            {
              "type": "origins:statistic",
              "statistic": "minecraft:time_since_rest",
              "comparison": ">=",
              "compare_to": 24000
            }
            """), "test:sleep_deprivation");
        assertNotNull(cond);
        assertFalse(cond == com.cyberday1.neoorigins.compat.CompatPolicy.FALSE_CONDITION,
            "a well-formed statistic condition must not fall through to the unsupported-type path");
    }
}
