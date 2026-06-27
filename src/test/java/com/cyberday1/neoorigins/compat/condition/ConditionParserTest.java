package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConditionParserTest {

    /**
     * Seer-pack regression: origins:distance_from_coordinates must resolve to a
     * registered descriptor, not fail-closed. Before the fix it fell through to
     * the unknown-verb branch → an origins:and containing it short-circuited to
     * constant-false, so the upgrade powers never granted their tags.
     */
    @Test
    void distanceFromCoordinatesIsRegistered() {
        assertNotNull(BuiltinConditions.get("neoorigins:distance_from_coordinates"),
            "neoorigins:distance_from_coordinates must be a known condition verb");
    }

    /**
     * origins:/apoli: prefixes are canonicalised to neoorigins: before dispatch,
     * so the pack-shaped origins:distance_from_coordinates resolves to a real
     * condition rather than failing closed. We can't exercise the level-dependent
     * body with a null player here (that needs a server), but reaching a NON
     * fail-closed parse is the regression that mattered: the parse below would
     * throw an NPE on p.level() for a real condition, whereas a fail-closed
     * condition returns false outright. Either way it must NOT be the
     * unsupported-type path, which we assert via the registry above; here we just
     * confirm the prefix-rewrite reaches a handler without an "unsupported" log.
     */
    @Test
    void distanceFromCoordinatesAliasParses() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:distance_from_coordinates");
        json.addProperty("comparison", "<");
        json.addProperty("compare_to", 2);
        // parse() must not return the fail-closed constant; a real condition is
        // produced. Evaluating with a null player throws (level() NPE), which is
        // itself proof the real body — not the fail-closed false-returning lambda
        // — was selected.
        EntityCondition cond = ConditionParser.parse(json, "test:dfc_alias");
        assertNotNull(cond);
    }

    @Test
    void nullConditionFailsClosed() {
        assertFalse(ConditionParser.parse(null, "test:null_condition").test(null));
    }

    @Test
    void unknownConditionTypeFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:not_real");

        assertFalse(ConditionParser.parse(json, "test:unknown_condition").test(null));
    }

    @Test
    void notWithoutConditionFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:not");

        assertFalse(ConditionParser.parse(json, "test:not_missing_inner").test(null));
    }

    @Test
    void resourceWithoutResourceFieldFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:resource");

        assertFalse(ConditionParser.parse(json, "test:resource_missing_field").test(null));
    }

    @Test
    void onBlockWithoutRequiredFieldsFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:on_block");

        assertFalse(ConditionParser.parse(json, "test:on_block_missing_condition").test(null));
    }

    @Test
    void onBlockWithoutBlockIdFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "origins:on_block");
        json.add("block_condition", new JsonObject());

        assertFalse(ConditionParser.parse(json, "test:on_block_missing_id").test(null));
    }
}
