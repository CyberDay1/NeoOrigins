package com.cyberday1.neoorigins.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** An additive layer file adds to {@code exclude_random} instead of dropping it; a non-list one is ignored. */
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

    @Test
    void aNonListExcludeRandomIsIgnoredLikeOriginsDid() {
        JsonObject legacy = json("{\"origins\":[\"somniabh:global\"],\"hidden\":true,\"exclude_random\":true}");
        LayerDataManager.normalizeLayerJson(Identifier.parse("somniabh:global"), legacy);
        assertFalse(legacy.has("exclude_random"));
    }

    @Test
    void aListExcludeRandomIsKept() {
        JsonObject modern = json("{\"origins\":[\"a:one\"],\"exclude_random\":[\"a:one\"]}");
        LayerDataManager.normalizeLayerJson(Identifier.parse("a:layer"), modern);
        assertEquals(json("{\"x\":[\"a:one\"]}").get("x"), modern.get("exclude_random"));
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }
}
