package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Load-path coverage for the two event names {@code datapacks/thfox-origin}
 * shipped that no build step could see were fictional.
 *
 * <p>An unknown {@code event} is a hard {@code DataResult.error}, so neither
 * power loaded at all — the fox origin listed a Mouth Pouch and a shield nerf
 * that could never fire. The schema could not catch it either, because the
 * schema's own option list was the thing missing them.
 *
 * <p>The two gaps were not the same kind. {@code item_use_start} was purely a
 * spelling: {@code ITEM_USE} already fires from
 * {@code LivingEntityUseItemEvent.Start} for anything with a use duration, which
 * is exactly the moment a shield goes up. {@code mod_food_nutrition} was a real
 * missing capability — nutrition was modifiable only through the legacy
 * {@code origins:modify_food} power, with no native event chaining onto it.
 * Hence one alias and one new event.
 */
class EventCompatLoadPathTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ActionOnEventPower.Config decode(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return ActionOnEventPower.Config.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(msg -> new AssertionError("action_on_event decode failed: " + msg));
    }

    private static DataResult<?> attempt(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return ActionOnEventPower.Config.CODEC.parse(JsonOps.INSTANCE, json);
    }

    /** ThFox {@code weak_shield}, verbatim. */
    @Test
    void itemUseStartResolvesToItemUse() {
        var config = decode("""
            {
              "type": "neoorigins:action_on_event",
              "event": "item_use_start",
              "condition": {
                "type": "neoorigins:and",
                "conditions": [
                  {
                    "type": "neoorigins:resource",
                    "resource": "thfox:setting_shield_restriction",
                    "comparison": ">=",
                    "compare_to": 1
                  },
                  { "type": "neoorigins:using_item" }
                ]
              },
              "entity_action": {
                "type": "neoorigins:execute_command",
                "command": "item replace entity @s weapon.offhand with minecraft:air"
              }
            }
            """);

        assertEquals(EventPowerIndex.Event.ITEM_USE, config.event(),
            "item_use_start must land on the event that actually fires at use start");
        assertNotNull(config.action(), "the entity_action must survive the alias path");
    }

    /** ThFox {@code food_disliked_penalty}, verbatim. */
    @Test
    void modFoodNutritionIsARealEvent() {
        var config = decode("""
            {
              "type": "neoorigins:action_on_event",
              "event": "mod_food_nutrition",
              "hidden": true,
              "condition": {
                "type": "neoorigins:and",
                "conditions": [
                  {
                    "type": "neoorigins:food_item_in_tag",
                    "tag": "#thfox:disliked_food"
                  },
                  {
                    "type": "neoorigins:resource",
                    "resource": "thfox:setting_food_nerfs",
                    "comparison": ">=",
                    "compare_to": 1
                  }
                ]
              },
              "modifier": { "operation": "multiply_base", "value": 0.5 }
            }
            """);

        assertEquals(EventPowerIndex.Event.MOD_FOOD_NUTRITION, config.event());
        assertNotNull(config.modifier(),
            "a mod_ event carries a modifier rather than an entity_action");
    }

    /** Case is normalised before the alias lookup, as it is for canonical names. */
    @Test
    void aliasIsCaseInsensitive() {
        var config = decode("""
            { "type": "neoorigins:action_on_event", "event": "ITEM_USE_START" }
            """);
        assertEquals(EventPowerIndex.Event.ITEM_USE, config.event());
    }

    /**
     * The alias fallback must not have softened the failure. A genuinely unknown
     * event still has to be rejected — silently accepting one would trade a loud
     * load error for a power that sits there never firing.
     */
    @Test
    void anUnknownEventIsStillRejected() {
        var result = attempt("""
            { "type": "neoorigins:action_on_event", "event": "mod_definitely_not_real" }
            """);
        assertTrue(result.isError(),
            "an unknown event must remain a hard decode error, not fall through the alias table");
        assertTrue(result.error().orElseThrow().message().contains("mod_definitely_not_real"),
            "the error should name the offending event so a pack author can find it");
    }
}
