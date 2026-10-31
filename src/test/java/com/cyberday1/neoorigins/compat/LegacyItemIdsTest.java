package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LegacyItemIdsTest {

    private static JsonElement json(String s) {
        return JsonParser.parseString(s);
    }

    @Test
    void renamedIconItemIsRemapped() {
        assertEquals(json("\"minecraft:turtle_scute\""), LegacyItemIds.remapIcon(json("\"minecraft:scute\"")));
        assertEquals(json("{\"item\":\"minecraft:turtle_scute\",\"tag\":\"{CustomModelData:1}\"}"),
            LegacyItemIds.remapIcon(json("{\"item\":\"minecraft:scute\",\"tag\":\"{CustomModelData:1}\"}")));
        assertEquals(json("{\"id\":\"minecraft:turtle_scute\"}"), LegacyItemIds.remapIcon(json("{\"id\":\"minecraft:scute\"}")));
    }

    @Test
    void currentIconsAreReturnedAsIs() {
        for (String icon : new String[] {"\"minecraft:turtle_scute\"", "{\"item\":\"minecraft:ink_sac\"}", "\"mymod:scute\""}) {
            JsonElement el = json(icon);
            assertSame(el, LegacyItemIds.remapIcon(el));
        }
    }
}
