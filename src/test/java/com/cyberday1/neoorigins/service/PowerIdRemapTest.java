package com.cyberday1.neoorigins.service;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PowerIdRemapTest {

    @Test
    void renamesReferencesAtAnyDepthButNeverAType() {
        // neoorigins:resource is both a power type and, here, a power id
        var in = JsonParser.parseString("{\"type\":\"neoorigins:resource\",\"cost\":\"neoorigins:resource\","
            + "\"action\":{\"type\":\"neoorigins:resource\",\"list\":[\"neoorigins:resource\",\"x:y\"]}}");
        var out = PowerIdRemap.rewrite(in, Map.of("neoorigins:resource", "neoorigins_custom:resource"));
        assertEquals(JsonParser.parseString("{\"type\":\"neoorigins:resource\",\"cost\":\"neoorigins_custom:resource\","
            + "\"action\":{\"type\":\"neoorigins:resource\",\"list\":[\"neoorigins_custom:resource\",\"x:y\"]}}"), out);
    }
}
