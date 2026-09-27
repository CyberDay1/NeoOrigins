package com.cyberday1.neoorigins.screen.creator.widget;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** What a form edit keeps and what it overwrites, one case per rule. */
class FormMergeTest {

    private static JsonElement j(String s) { return s == null ? null : JsonParser.parseString(s); }

    private static void expect(String want, String orig, String before, String after) {
        assertEquals(j(want), FormMerge.merge(j(orig), j(before), j(after)));
    }

    @Test
    void anUntouchedRowKeepsTheStoredValueExactly() {
        expect("{\"type\":\"neoorigins:on_fire\",\"inverted\":true}",
            "{\"type\":\"neoorigins:on_fire\",\"inverted\":true}",
            "{\"type\":\"neoorigins:on_fire\"}", "{\"type\":\"neoorigins:on_fire\"}");
        // an action written as a list reads as unset and stays a list
        expect("[{\"type\":\"neoorigins:heal\"}]", "[{\"type\":\"neoorigins:heal\"}]", null, null);
        // a typeless item_condition the sub-form could not read
        expect("{\"tag\":\"minecraft:swords\"}", "{\"tag\":\"minecraft:swords\"}", null, null);
    }

    @Test
    void editingANestedFieldKeepsItsUnreadSiblings() {
        expect("{\"type\":\"neoorigins:damage\",\"amount\":6,\"weapon_damage_scale\":0.5}",
            "{\"type\":\"neoorigins:damage\",\"amount\":4,\"weapon_damage_scale\":0.5}",
            "{\"type\":\"neoorigins:damage\",\"amount\":4}",
            "{\"type\":\"neoorigins:damage\",\"amount\":6}");
    }

    @Test
    void clearingAFieldTheFormShowedRemovesIt() {
        expect("{\"type\":\"a:b\",\"inverted\":true}",
            "{\"type\":\"a:b\",\"radius\":3,\"inverted\":true}",
            "{\"type\":\"a:b\",\"radius\":3}", "{\"type\":\"a:b\"}");
        assertNull(FormMerge.merge(j("{\"type\":\"a:b\",\"inverted\":true}"), j("{\"type\":\"a:b\"}"), null));
    }

    @Test
    void changingTheTypeKeepsWhatTheFormNeverShowed() {
        expect("{\"type\":\"a:new\",\"x\":1,\"inverted\":true}",
            "{\"type\":\"a:old\",\"y\":2,\"inverted\":true}",
            "{\"type\":\"a:old\",\"y\":2}", "{\"type\":\"a:new\",\"x\":1}");
    }

    @Test
    void settingASlotThatHeldSomethingUnreadableReplacesIt() {
        expect("{\"type\":\"a:new\"}", "[{\"type\":\"a:x\"}]", null, "{\"type\":\"a:new\"}");
    }

    @Test
    void listEntriesKeepTheirOwnUnreadKeys() {
        String orig = "[{\"type\":\"a:x\",\"inverted\":true},{\"type\":\"a:y\",\"n\":1}]";
        String before = "[{\"type\":\"a:x\"},{\"type\":\"a:y\",\"n\":1}]";
        // edit the second entry
        expect("[{\"type\":\"a:x\",\"inverted\":true},{\"type\":\"a:y\",\"n\":2}]",
            orig, before, "[{\"type\":\"a:x\"},{\"type\":\"a:y\",\"n\":2}]");
        // remove the second entry
        expect("[{\"type\":\"a:x\",\"inverted\":true}]", orig, before, "[{\"type\":\"a:x\"}]");
        // remove the first entry
        expect("[{\"type\":\"a:y\",\"n\":1}]", orig, before, "[{\"type\":\"a:y\",\"n\":1}]");
        // append one
        expect("[{\"type\":\"a:x\",\"inverted\":true},{\"type\":\"a:y\",\"n\":1},{\"type\":\"a:z\"}]",
            orig, before, "[{\"type\":\"a:x\"},{\"type\":\"a:y\",\"n\":1},{\"type\":\"a:z\"}]");
    }
}
