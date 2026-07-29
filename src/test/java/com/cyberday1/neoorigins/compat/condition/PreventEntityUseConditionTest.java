package com.cyberday1.neoorigins.compat.condition;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for issue #118: {@code origins:prevent_entity_use} threw its
 * {@code entity_condition} / {@code bientity_condition} away, so a power
 * narrowed to (e.g.) {@code minecraft:wolf} cancelled EVERY entity interaction
 * — villager trading, saddling, leads, boats. The narrowing now compiles
 * through the entity-general engine here, and — because this is a
 * <em>prevention</em> — anything that will not compile must fail CLOSED
 * ({@code null}) so the loader refuses the power instead of blocking the world.
 */
class PreventEntityUseConditionTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** The exact narrowing shape from the reported pack. */
    @Test
    void entityTypeNarrowingCompiles() {
        assertNotNull(
            TargetConditionParser.parse(
                obj("""
                    { "type": "origins:entity_type", "entity_type": "minecraft:wolf" }
                    """), "test:prevent_entity_use"),
            "entity_condition entity_type must compile to an entity-general condition");
    }

    /** origins:/apoli:/apace: prefixes canonicalise to neoorigins: before dispatch. */
    @Test
    void apoliPrefixCanonicalises() {
        assertNotNull(TargetConditionParser.parse(
            obj("{ \"type\": \"apoli:entity_type\", \"entity_type\": \"#minecraft:skeletons\" }"), "test:id"));
        assertNotNull(TargetConditionParser.parse(
            obj("{ \"type\": \"apace:entity_type\", \"entity_type\": \"minecraft:villager\" }"), "test:id"));
        assertNotNull(TargetConditionParser.parse(
            obj("{ \"type\": \"entity_type\", \"entity_type\": \"minecraft:villager\" }"), "test:id"));
    }

    /**
     * The Origins++ shape (unholy_aura / hated / distrust no_pets): the target
     * narrowing is an entity-type tag via {@code origins:in_tag}. Without
     * entity-general in_tag support every one of those powers would fail closed
     * to inert instead of blocking only pets.
     */
    @Test
    void inTagNarrowingCompiles() {
        assertNotNull(TargetConditionParser.parseBiEntity(
            obj("""
                {
                  "type": "origins:target_condition",
                  "condition": { "type": "origins:in_tag", "tag": "origins-plus-plus:pets" }
                }
                """), "test:prevent_entity_use"),
            "in_tag over an entity-type tag must compile on the target side");
        assertNotNull(TargetConditionParser.parse(
            obj("{ \"type\": \"origins:in_tag\", \"tag\": \"#origins-plus-plus:pets\" }"), "test:id"),
            "a leading # on the tag id is tolerated");
    }

    /** bientity_condition target_condition routes its inner verb to the target. */
    @Test
    void bientityTargetConditionCompiles() {
        assertNotNull(
            TargetConditionParser.parseBiEntity(
                obj("""
                    {
                      "type": "origins:target_condition",
                      "condition": { "type": "origins:entity_type", "entity_type": "minecraft:wolf" }
                    }
                    """), "test:prevent_entity_use"),
            "bientity target_condition over a generalizable verb must compile");
    }

    /**
     * Fail CLOSED: a target_condition whose inner verb is player-only cannot be
     * evaluated against a wolf, so the whole predicate must refuse to compile.
     * Fail-OPEN here (the policy used for wrapping actions) would mean
     * "prevents everything" — the bug being fixed.
     */
    @Test
    void nonGeneralizableTargetConditionFailsClosed() {
        assertNull(
            TargetConditionParser.parseBiEntity(
                obj("""
                    {
                      "type": "origins:target_condition",
                      "condition": { "type": "origins:in_rain" }
                    }
                    """), "test:prevent_entity_use"),
            "a player-only target verb must fail closed (null), not degrade to always-true");
    }

    /** An unknown bientity verb fails closed too. */
    @Test
    void unknownBiEntityVerbFailsClosed() {
        assertNull(TargetConditionParser.parseBiEntity(
            obj("{ \"type\": \"origins:some_unmapped_bientity_verb\" }"), "test:id"));
    }

    /** One non-compiling child poisons the whole and/or tree — still fail closed. */
    @Test
    void combinatorWithNonGeneralizableChildFailsClosed() {
        assertNull(
            TargetConditionParser.parseBiEntity(
                obj("""
                    {
                      "type": "origins:and",
                      "conditions": [
                        { "type": "origins:target_condition",
                          "condition": { "type": "origins:entity_type", "entity_type": "minecraft:wolf" } },
                        { "type": "origins:target_condition",
                          "condition": { "type": "origins:in_rain" } }
                      ]
                    }
                    """), "test:prevent_entity_use"));
    }

    /** and/or/not/inverted semantics, exercised on constants (no entity needed). */
    @Test
    void combinatorAndInvertedSemantics() {
        var and = TargetConditionParser.parseBiEntity(
            obj("""
                {
                  "type": "origins:and",
                  "conditions": [
                    { "type": "origins:constant", "value": true },
                    { "type": "origins:constant", "value": false }
                  ]
                }
                """), "test:id");
        assertNotNull(and);
        assertFalse(and.test(null, null));

        var or = TargetConditionParser.parseBiEntity(
            obj("""
                {
                  "type": "origins:or",
                  "conditions": [
                    { "type": "origins:constant", "value": true },
                    { "type": "origins:constant", "value": false }
                  ]
                }
                """), "test:id");
        assertNotNull(or);
        assertTrue(or.test(null, null));

        var inverted = TargetConditionParser.parseBiEntity(
            obj("{ \"type\": \"origins:constant\", \"value\": true, \"inverted\": true }"), "test:id");
        assertNotNull(inverted);
        assertFalse(inverted.test(null, null));

        var not = TargetConditionParser.parseBiEntity(
            obj("""
                {
                  "type": "origins:not",
                  "condition": { "type": "origins:constant", "value": false }
                }
                """), "test:id");
        assertNotNull(not);
        assertTrue(not.test(null, null));
    }

    /**
     * A non-living target (boat, minecart, item frame) can't satisfy an
     * entity-general narrowing, so a narrowed power leaves it alone rather than
     * blocking it — the concrete symptom reported in #118.
     */
    @Test
    void nonLivingTargetIsNotMatchedByNarrowedCondition() {
        var tc = TargetConditionParser.parse(
            obj("{ \"type\": \"origins:entity_type\", \"entity_type\": \"minecraft:wolf\" }"), "test:id");
        assertNotNull(tc);
        assertFalse(TargetConditionParser.asTargetPredicate(tc).test(null, null),
            "a null/non-living target must not match a wolf-narrowed condition");
    }
}
