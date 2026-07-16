package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConditionParserTest {

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

    /**
     * The config-driven extra-fish-food condition must be a registered verb so
     * the aquatic_fish_diet any_of gate resolves instead of failing closed.
     */
    @Test
    void foodItemInConfigListIsRegistered() {
        assertNotNull(BuiltinConditions.get("neoorigins:food_item_in_config_list"),
            "neoorigins:food_item_in_config_list must be a known condition verb");
    }

    /**
     * An unknown config key fails closed (evaluates false) rather than throwing
     * or defaulting to true — a typo must not silently open the fish diet.
     */
    @Test
    void foodItemInConfigListUnknownKeyFailsClosed() {
        JsonObject json = new JsonObject();
        json.addProperty("type", "neoorigins:food_item_in_config_list");
        json.addProperty("key", "ocean_origins.not_a_real_key");

        assertFalse(ConditionParser.parse(json, "test:food_config_unknown_key").test(null));
    }
}
