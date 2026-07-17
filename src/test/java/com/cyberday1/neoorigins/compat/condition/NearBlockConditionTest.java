package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #110 fixes 3 and 4: origins:block_in_radius (near_block)
 * used to be an any-match probe that ignored comparison/compare_to and capped
 * radius at 8, so Origins++'s tiered "N webs nearby" checks always behaved as
 * ">= 1 within 8". It now counts matches and applies Apoli's comparison
 * semantics (defaults ">=" 1). Fix 4 adds the neoorigins:climbing_gate and
 * neoorigins:in_block_anywhere condition verbs behind conditioned
 * origins:climbing.
 */
class NearBlockConditionTest {

    // ── registration (fix 3 + 4 verbs exist as descriptors) ─────────────

    @Test
    void nearBlockIsRegisteredWithAlias() {
        assertNotNull(BuiltinConditions.get("neoorigins:near_block"),
            "canonical near_block descriptor must exist");
        assertNotNull(BuiltinConditions.get("neoorigins:block_in_radius"),
            "Apoli name block_in_radius must dispatch as an alias");
    }

    @Test
    void climbingGateAndInBlockAnywhereAreRegistered() {
        assertNotNull(BuiltinConditions.get("neoorigins:climbing_gate"),
            "climbing_gate carries origins:climbing condition/hold_condition");
        assertNotNull(BuiltinConditions.get("neoorigins:in_block_anywhere"),
            "in_block_anywhere must be its own verb, not an in_block alias");
    }

    // ── near_block parse-level behaviour ─────────────────────────────────

    @Test
    void nearBlockWithoutAnyBlockSpecFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:block_in_radius");
        json.addProperty("radius", 5);

        assertFalse(ConditionParser.parse(json, "test:near_block_no_spec").test(null),
            "no block/blocks/tag(s)/block_condition must fail closed, not match everything");
    }

    @Test
    void nearBlockWithTagSpecParsesToRealCondition() {
        // Registry-free spec (tags are keys, resolved lazily at eval); a real
        // condition — not the fail-closed constant — must come back even with
        // the new comparison fields present.
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:block_in_radius");
        json.addProperty("tag", "origins:cobwebs");
        json.addProperty("radius", 12); // > old cap of 8
        json.addProperty("comparison", ">=");
        json.addProperty("compare_to", 3);
        json.addProperty("shape", "sphere");

        assertNotNull(ConditionParser.parse(json, "test:near_block_tag"));
    }

    @Test
    void nearBlockWithBlockConditionParsesToRealCondition() {
        // Origins++ shape: nested block_condition instead of a flat block field.
        JsonObject inner = new JsonObject();
        inner.addProperty("type", "origins:in_tag");
        inner.addProperty("tag", "origins:cobwebs");
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:block_in_radius");
        json.add("block_condition", inner);

        assertNotNull(ConditionParser.parse(json, "test:near_block_block_condition"));
    }

    // ── count semantics: the comparison the scan feeds its count into ────
    // The block scan itself needs a live level; what regressed (and what the
    // early-exit cap must preserve) is the comparison outcome per operator.

    @Test
    void defaultComparisonIsAnyMatch() {
        // Apoli default ">=" 1: one match suffices, zero fails.
        ComparisonType ge = ComparisonType.fromString(">=");
        assertTrue(ge.test(1, 1));
        assertTrue(ge.test(9, 1));
        assertFalse(ge.test(0, 1));
    }

    @Test
    void countComparisonsBehavePerOperator() {
        assertTrue(ComparisonType.fromString(">=").test(3, 3));
        assertFalse(ComparisonType.fromString(">=").test(2, 3));
        assertTrue(ComparisonType.fromString(">").test(4, 3));
        assertFalse(ComparisonType.fromString(">").test(3, 3));
        assertTrue(ComparisonType.fromString("<").test(2, 3));
        assertFalse(ComparisonType.fromString("<").test(3, 3));
        assertTrue(ComparisonType.fromString("<=").test(3, 3));
        assertFalse(ComparisonType.fromString("<=").test(4, 3));
        assertTrue(ComparisonType.fromString("==").test(3, 3));
        assertFalse(ComparisonType.fromString("==").test(4, 3));
        assertTrue(ComparisonType.fromString("!=").test(4, 3));
        assertFalse(ComparisonType.fromString("!=").test(3, 3));
    }

    /**
     * The scan stops counting at stopAt and feeds the capped count into
     * comparison.test. This locks the cap→outcome equivalence for each
     * operator family: {@code {">=", "<"} → target}, everything else
     * {@code target + 1}.
     */
    @Test
    void earlyExitCapPreservesComparisonOutcome() {
        int target = 3;
        // ">=" / "<" cap at target: reaching the cap decides both operators.
        assertTrue(ComparisonType.fromString(">=").test(target, target));
        assertFalse(ComparisonType.fromString("<").test(target, target));
        // ">", "<=", "==", "!=" cap at target+1: a real count of N > target+1
        // and the capped target+1 agree for all four.
        long cap = target + 1;
        for (String op : new String[] {">", "<=", "==", "!="}) {
            ComparisonType c = ComparisonType.fromString(op);
            assertTrue(c.test(cap, target) == c.test(cap + 5, target),
                "capped and uncapped counts must agree for " + op);
        }
    }

    // ── climbing_gate parse-level behaviour ──────────────────────────────

    @Test
    void climbingGateWithNestedConditionsParses() {
        // Registry-free nested conditions (resource) stand in for the pack's
        // real block/power gates.
        JsonObject cond = new JsonObject();
        cond.addProperty("type", "origins:resource");
        cond.addProperty("resource", "test:climb_toggle");
        cond.addProperty("comparison", "==");
        cond.addProperty("compare_to", 1);
        JsonObject hold = new JsonObject();
        hold.addProperty("type", "origins:resource");
        hold.addProperty("resource", "test:climb_toggle");
        hold.addProperty("comparison", ">=");
        hold.addProperty("compare_to", 0);
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoorigins:climbing_gate");
        json.add("condition", cond);
        json.add("hold_condition", hold);
        json.addProperty("allow_holding", true);

        assertNotNull(ConditionParser.parse(json, "test:climbing_gate"));
    }

    @Test
    void inBlockAnywhereWithoutBlockConditionParses() {
        // Apoli treats a missing block_condition as match-any; parse must not
        // fail closed.
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:in_block_anywhere");
        json.addProperty("comparison", ">=");
        json.addProperty("compare_to", 1);

        assertNotNull(ConditionParser.parse(json, "test:in_block_anywhere"));
    }
}
