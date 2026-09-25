package com.cyberday1.neoorigins.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** An additive layer file adds to {@code exclude_random} instead of dropping it. */
class ExcludeRandomMergeTest {

    @Test
    void anAddonExclusionJoinsTheShippedOne() {
        JsonObject shipped = json("{\"origins\":[\"a:one\",\"a:dragon\"],\"exclude_random\":[\"a:dragon\"]}");
        JsonObject addon = json("{\"origins\":[\"b:two\"],\"exclude_random\":[\"b:two\",\"a:dragon\"]}");
        LayerDataManager.mergeOrigins(shipped, addon);
        assertEquals(json("{\"x\":[\"a:dragon\",\"b:two\"]}").get("x"), shipped.get("exclude_random"));
    }

    @Test
    void anAddonWithoutOneLeavesItAlone() {
        JsonObject shipped = json("{\"origins\":[\"a:one\"],\"exclude_random\":[\"a:dragon\"]}");
        LayerDataManager.mergeOrigins(shipped, json("{\"origins\":[\"b:two\"]}"));
        assertEquals(json("{\"x\":[\"a:dragon\"]}").get("x"), shipped.get("exclude_random"));
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
