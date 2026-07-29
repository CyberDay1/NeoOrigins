package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.compat.CompatPolicy;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two entity conditions added to close the residual compat gap:
 * {@code nearby_entities} (Fairytale's wolf_detection / pack_mentality) and
 * {@code near_villager} (Fairytale's village_hero). Both were unregistered, so
 * every power that gated on them failed closed and vanished.
 *
 * <p>The scan bodies need a live level, so what is asserted here is everything
 * decided at parse time: registration, the selector shapes each verb accepts,
 * and — the part that matters for a fail-closed verb — exactly which malformed
 * inputs collapse to {@link CompatPolicy#FALSE_CONDITION} instead of quietly
 * counting the wrong entities.
 */
class NearbyEntitiesConditionTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    // ── registration ─────────────────────────────────────────────────────

    @Test
    void bothVerbsAreRegistered() {
        assertNotNull(BuiltinConditions.get("neoorigins:nearby_entities"),
            "nearby_entities must be a known condition verb");
        assertNotNull(BuiltinConditions.get("neoorigins:near_villager"),
            "near_villager must be a known condition verb");
    }

    @Test
    void bothVerbsAreInKnownTypes() {
        assertTrue(ConditionParser.KNOWN_TYPES.contains("neoorigins:nearby_entities"));
        assertTrue(ConditionParser.KNOWN_TYPES.contains("neoorigins:near_villager"));
    }

    // ── nearby_entities: accepted shapes ─────────────────────────────────

    /** An entity-type #tag needs no registry lookup, so it compiles outright. */
    @Test
    void tagSelectorCompilesToARealCondition() {
        EntityCondition cond = ConditionParser.parse(obj("""
            {
              "type": "origins:nearby_entities",
              "entity_type": "#minecraft:skeletons",
              "distance": 10,
              "comparison": ">=",
              "compare_to": 2
            }
            """), "test:skeleton_swarm");
        assertNotSame(CompatPolicy.FALSE_CONDITION, cond,
            "a #tag selector must not fail closed");
    }

    /** A list of tags through the plural field, plus the Apoli radius spelling. */
    @Test
    void pluralTagListAndRadiusSpellingCompile() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "apoli:nearby_entities",
              "entity_types": ["#minecraft:skeletons", "#minecraft:raiders"],
              "radius": 8
            }
            """), "test:undead_or_raiders"));
    }

    /** Apoli allows a bare nearby_entities: every entity in range counts. */
    @Test
    void noSelectorAtAllStillCompiles() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(
            obj("{ \"type\": \"origins:nearby_entities\", \"comparison\": \"<\", \"compare_to\": 3 }"),
            "test:lonely"),
            "a bare nearby_entities counts everything rather than failing closed");
    }

    /** A bientity_condition that IS generalizable compiles the whole verb. */
    @Test
    void compilableBientityConditionIsAccepted() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "apoli:nearby_entities",
              "bientity_condition": { "type": "apoli:constant", "value": true },
              "radius": 6
            }
            """), "test:any_entity"));
    }

    // ── nearby_entities: fail-closed paths ───────────────────────────────

    /**
     * An unresolvable entity id is a pack typo, and counting "no matches" for it
     * would silently invert a "fewer than N nearby" gate into permanently true.
     */
    @Test
    void unknownEntityTypeFailsClosed() {
        EntityCondition cond = ConditionParser.parse(obj("""
            { "type": "origins:nearby_entities", "entity_type": "minecraft:definitely_not_a_mob" }
            """), "test:typo");
        assertSame(CompatPolicy.FALSE_CONDITION, cond,
            "an unknown entity id must fail closed, not count zero");
        assertFalse(cond.test(null));
    }

    /**
     * A bientity_condition using a verb that cannot be evaluated against a
     * non-player entity would otherwise be dropped, counting entities the pack
     * meant to exclude.
     */
    @Test
    void uncompilableBientityConditionFailsClosed() {
        assertSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            {
              "type": "apoli:nearby_entities",
              "bientity_condition": { "type": "apoli:no_such_bientity_verb" },
              "radius": 6
            }
            """), "test:broken_filter"),
            "an uncompilable bientity_condition must fail the whole condition closed");
    }

    // ── near_villager ────────────────────────────────────────────────────

    @Test
    void nearVillagerCompilesWithAndWithoutFields() {
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(
            obj("{ \"type\": \"origins:near_villager\" }"), "test:default"),
            "near_villager must default to \">= 1 within 16\" rather than fail closed");
        assertNotSame(CompatPolicy.FALSE_CONDITION, ConditionParser.parse(obj("""
            { "type": "origins:near_villager", "distance": 32, "comparison": ">=", "compare_to": 3 }
            """), "test:village_hero"));
    }

    /**
     * The reporting packs write these verbs under three different namespaces;
     * all of them canonicalise to neoorigins: before dispatch, so none may reach
     * the unsupported-type path.
     */
    @Test
    void everyAuthoredNamespaceResolves() {
        for (String type : new String[] {
                "origins:near_villager", "apoli:near_villager",
                "neoorigins:near_villager", "near_villager" }) {
            assertNotSame(CompatPolicy.FALSE_CONDITION,
                ConditionParser.parse(obj("{ \"type\": \"" + type + "\" }"), "test:ns"),
                type + " must resolve to the registered verb");
        }
        for (String type : new String[] {
                "origins:nearby_entities", "apoli:nearby_entities",
                "neoorigins:nearby_entities", "nearby_entities" }) {
            assertNotSame(CompatPolicy.FALSE_CONDITION,
                ConditionParser.parse(obj("{ \"type\": \"" + type + "\" }"), "test:ns"),
                type + " must resolve to the registered verb");
        }
    }

    // ── count semantics shared by both verbs ─────────────────────────────

    /**
     * Both scans stop counting at stopAt and feed the capped count into
     * comparison.test, so the cap must not change the outcome: {@code {">=",
     * "<"}} cap at the target, the other four at target + 1.
     */
    @Test
    void earlyExitCapPreservesComparisonOutcome() {
        int target = 2;
        assertTrue(ComparisonType.fromString(">=").test(target, target));
        assertFalse(ComparisonType.fromString("<").test(target, target));
        long cap = target + 1;
        for (String op : new String[] { ">", "<=", "==", "!=" }) {
            ComparisonType c = ComparisonType.fromString(op);
            assertTrue(c.test(cap, target) == c.test(cap + 7, target),
                "capped and uncapped counts must agree for " + op);
        }
    }

    /** Apoli's default for both verbs is ">= 1" — "at least one nearby". */
    @Test
    void defaultComparisonIsAnyMatch() {
        ComparisonType ge = ComparisonType.fromString(">=");
        assertTrue(ge.test(1, 1));
        assertFalse(ge.test(0, 1));
    }
}
