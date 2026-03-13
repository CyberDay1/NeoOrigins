package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
