package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #110 follow-up item 1: {@code origins:conditioned_attribute}
 * with a plural {@code modifiers} field (array OR single object — Origins++ 2.4
 * uses both) used to be parsed by the singular-only path, which found no
 * top-level {@code attribute} and dropped the power ("Route B: no handler
 * produced a config" — 8 skips). {@code collectAttributeModifierSpecs} now
 * canonicalizes every real-pack shape into one spec object per modifier.
 */
class ConditionedAttributeSpecsTest {

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    /** Origins++ shadow-crawler/dark_boost shape: modifiers as an ARRAY. */
    @Test
    void pluralArrayYieldsOneSpecPerEntry() {
        JsonObject power = json("""
            {"type":"origins:conditioned_attribute",
             "modifiers":[
               {"attribute":"minecraft:generic.movement_speed","operation":"multiply_base","value":0.75},
               {"attribute":"minecraft:generic.attack_damage","operation":"multiply_base","value":0.75},
               {"attribute":"minecraft:generic.max_health","operation":"addition","value":8}],
             "update_health":true,
             "condition":{"type":"origins:brightness","comparison":"<=","compare_to":0.4}}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(3, specs.size());
        assertEquals("minecraft:generic.movement_speed", specs.get(0).get("attribute").getAsString());
        assertEquals("minecraft:generic.max_health", specs.get(2).get("attribute").getAsString());
        assertEquals("addition", specs.get(2).get("operation").getAsString());
        assertEquals(8, specs.get(2).get("value").getAsDouble());
    }

    /** Origins++ lunar-path/pluck shape: modifiers as a single OBJECT. */
    @Test
    void pluralSingleObjectYieldsOneSpec() {
        JsonObject power = json("""
            {"type":"origins:conditioned_attribute",
             "modifiers":{"attribute":"minecraft:generic.attack_damage","operation":"addition","value":2},
             "condition":{"type":"origins:equipped_item","equipment_slot":"mainhand"}}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(1, specs.size());
        assertEquals("minecraft:generic.attack_damage", specs.get(0).get("attribute").getAsString());
        assertEquals(2, specs.get(0).get("value").getAsDouble());
    }

    /** Pre-existing singular form: modifier object with the attribute inside it. */
    @Test
    void singularModifierObjectStillParses() {
        JsonObject power = json("""
            {"modifier":{"attribute":"minecraft:generic.max_health","operation":"addition","value":4}}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(1, specs.size());
        assertEquals("minecraft:generic.max_health", specs.get(0).get("attribute").getAsString());
    }

    /** Pre-existing singular form: attribute at top level, values in "modifier". */
    @Test
    void topLevelAttributeIsInheritedByModifierObject() {
        JsonObject power = json("""
            {"attribute":"minecraft:generic.armor",
             "modifier":{"operation":"addition","value":3}}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(1, specs.size());
        assertEquals("minecraft:generic.armor", specs.get(0).get("attribute").getAsString());
        assertEquals(3, specs.get(0).get("value").getAsDouble());
    }

    /** Pre-existing flat form: attribute/operation/value directly on the power. */
    @Test
    void flatTopLevelFormParses() {
        JsonObject power = json("""
            {"attribute":"minecraft:generic.luck","operation":"addition","amount":1}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(1, specs.size());
        assertEquals("minecraft:generic.luck", specs.get(0).get("attribute").getAsString());
        assertEquals(1, specs.get(0).get("amount").getAsDouble());
    }

    /** Array entries may omit "attribute" and inherit the top-level one. */
    @Test
    void arrayEntriesInheritTopLevelAttribute() {
        JsonObject power = json("""
            {"attribute":"minecraft:generic.movement_speed",
             "modifiers":[{"operation":"multiply_base","value":0.2},
                          {"attribute":"minecraft:generic.attack_damage","operation":"addition","value":1}]}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        assertEquals(2, specs.size());
        assertEquals("minecraft:generic.movement_speed", specs.get(0).get("attribute").getAsString());
        assertEquals("minecraft:generic.attack_damage", specs.get(1).get("attribute").getAsString());
    }

    /** Specs are defensive copies — mutating them must not touch the source JSON. */
    @Test
    void specsAreDeepCopies() {
        JsonObject power = json("""
            {"modifiers":[{"attribute":"minecraft:generic.armor","operation":"addition","value":1}]}""");
        List<JsonObject> specs = OriginsCompatPowerLoader.collectAttributeModifierSpecs(power);
        specs.get(0).addProperty("attribute", "mutated");
        assertEquals("minecraft:generic.armor",
            power.getAsJsonArray("modifiers").get(0).getAsJsonObject().get("attribute").getAsString());
    }

    @Test
    void missingEverythingYieldsNoSpecs() {
        assertTrue(OriginsCompatPowerLoader.collectAttributeModifierSpecs(json("{}")).isEmpty());
        assertTrue(OriginsCompatPowerLoader.collectAttributeModifierSpecs(
            json("{\"modifiers\":[]}")).isEmpty());
        assertFalse(OriginsCompatPowerLoader.collectAttributeModifierSpecs(
            json("{\"modifiers\":{\"attribute\":\"a:b\",\"value\":1}}")).isEmpty());
    }
}
