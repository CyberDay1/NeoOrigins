package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Four compat mappings named the wrong target power type. {@code PowerEnumCheck}
 * now gates the three that named a type which does not exist; this pins the
 * MECHANIC, which no gate can derive — {@code creative_flight} named a type that
 * exists and was still wrong.
 *
 * <p>Both compat routes carry independent copies of the well-known id table, so
 * each case is asserted on both sides.
 */
// hub: neoorigins/compat-mapping-gaps.md
class OriginsCompatTargetMappingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject translate(String id, String json) {
        Optional<JsonObject> out = OriginsPowerTranslator.translate(
            Identifier.parse(id),
            JsonParser.parseString(json).getAsJsonObject());
        assertTrue(out.isPresent(), id + " did not translate");
        return out.orElseThrow();
    }

    private static JsonObject wellKnown(String id) {
        var supplier = OriginsCompatPowerLoader.WELL_KNOWN.get(id);
        assertTrue(supplier != null, id + " is not in WELL_KNOWN");
        return supplier.get();
    }

    /**
     * The reported bug. {@code neoorigins:flight} is elytra-style fall-flying
     * (FlightPower calls startFallFlying); Apoli's creative_flight grants
     * Abilities.mayfly, which is CreativeFlightPower.
     */
    @Test
    void creativeFlightIsHoverFlightNotElytra() {
        JsonObject out = translate("compat:cf",
            "{ \"type\": \"origins:creative_flight\" }");

        assertEquals("neoorigins:creative_flight", out.get("type").getAsString());
        assertNotEquals("neoorigins:flight", out.get("type").getAsString(),
            "neoorigins:flight is elytra fall-flying, not creative hover");
    }

    /** Same case label, and packs in the wild spell it both ways. */
    @Test
    void apaceCreativeFlightMatches() {
        JsonObject out = translate("compat:cf_apace",
            "{ \"type\": \"apace:creative_flight\" }");
        assertEquals("neoorigins:creative_flight", out.get("type").getAsString());
    }

    /**
     * {@code neoorigins:dries_out} has never been registered, so the power was
     * dropped silently. Upstream aquatic is entity-group membership; the sibling
     * {@code origins:arthropod} mapping already had the right shape.
     */
    @Test
    void aquaticIsTheWaterEntityGroup() {
        for (JsonObject out : new JsonObject[] {
                translate("origins:aquatic", "{ \"type\": \"origins:simple\" }"),
                wellKnown("origins:aquatic") }) {
            assertEquals("neoorigins:entity_group", out.get("type").getAsString());
            assertEquals("water", out.get("group").getAsString());
        }
    }

    /**
     * {@code neoorigins:conduit_power} has never been registered. The effect set
     * mirrors the repo's own merling_ascended_conduit.json.
     */
    @Test
    void conduitPowerOnLandGrantsTheConduitEffects() {
        for (JsonObject out : new JsonObject[] {
                translate("origins:conduit_power_on_land", "{ \"type\": \"origins:simple\" }"),
                wellKnown("origins:conduit_power_on_land") }) {
            assertEquals("neoorigins:persistent_effect", out.get("type").getAsString());
            var effects = out.getAsJsonArray("effects");
            assertEquals(3, effects.size());
            assertEquals("minecraft:water_breathing",
                effects.get(0).getAsJsonObject().get("effect").getAsString());
            assertEquals("minecraft:night_vision",
                effects.get(1).getAsJsonObject().get("effect").getAsString());
            assertEquals("minecraft:haste",
                effects.get(2).getAsJsonObject().get("effect").getAsString());
        }
    }

    /**
     * {@code neoorigins:spawn_location} has never been registered.
     * ModifyPlayerSpawnPower requires a nested LocationCondition, so a flat
     * {@code {"type": ...}} would fail the codec even if the type were right.
     */
    @Test
    void netherSpawnCarriesADimension() {
        JsonObject out = wellKnown("origins:nether_spawn");
        assertEquals("neoorigins:modify_player_spawn", out.get("type").getAsString());
        assertEquals("minecraft:the_nether",
            out.getAsJsonObject("location").get("dimension").getAsString());
    }
}
