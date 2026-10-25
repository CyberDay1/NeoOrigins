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

    /** The full pre-parse pipeline, which also canonicalizes the apoli: spelling. */
    private static JsonObject throughLoader(String id, String json) {
        var resolved = com.cyberday1.neoorigins.data.PowerDataManager.resolvePowerType(
            Identifier.parse(id), JsonParser.parseString(json).getAsJsonObject());
        assertTrue(resolved != null, id + " was dropped");
        return resolved.json();
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
     * Origins' conduit_power_on_land does not grant any effect: it lets an active
     * conduit reach the holder out of water. It used to be mapped onto a permanent
     * water breathing, night vision and Haste set, which an Origins pack never had.
     * Both the bare id and the type now land on the native type.
     */
    @Test
    void conduitPowerOnLandLiftsTheConduitWaterCheckAndGrantsNothing() {
        for (JsonObject out : new JsonObject[] {
                translate("origins:conduit_power_on_land", "{ \"type\": \"origins:simple\" }"),
                wellKnown("origins:conduit_power_on_land"),
                translate("compat:conduit", "{ \"type\": \"origins:conduit_power_on_land\" }"),
                throughLoader("compat:conduit_apoli", "{ \"type\": \"apoli:conduit_power_on_land\" }") }) {
            assertEquals("neoorigins:conduit_power_on_land", out.get("type").getAsString());
            assertTrue(!out.has("effects"), "must not grant potion effects: " + out);
        }
    }

    /**
     * Origins' entity_glow outlines matching OTHER entities for the holder. It used
     * to be a Route B handler that put Glowing on the holder, visible to everyone.
     * The native type takes Apoli's fields as they are.
     */
    @Test
    void entityGlowOutlinesOthersForTheHolder() {
        JsonObject out = throughLoader("compat:glow", """
            { "type": "apoli:entity_glow",
              "entity_condition": { "type": "apoli:in_tag", "tag": "minecraft:arthropod", "inverted": true },
              "bientity_condition": { "type": "apoli:can_see" },
              "use_teams": false, "red": 1.0, "green": 0.0, "blue": 0.0 }
            """);
        assertEquals("neoorigins:entity_glow", out.get("type").getAsString());
        var config = new com.cyberday1.neoorigins.power.builtin.EntityGlowPower().codec()
            .parse(com.mojang.serialization.JsonOps.INSTANCE, out)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
        assertTrue(config.selection().entityCondition().isPresent());
        assertTrue(config.bientityCondition().isPresent());
        assertTrue(!config.useTeams());
        assertEquals(0xFF0000, config.rgb());
        assertTrue(!OriginsCompatPowerLoader.isRouteBType("origins:entity_glow"),
            "entity_glow must not reach the Route B self-glow handler");
        assertTrue(OriginsCompatPowerLoader.isRouteBType("origins:self_glow"));
    }

    /**
     * The bare origins:master_of_webs id stood for wall_climbing, which climbs every
     * wall. Master of Webs is web movement and climbing inside webs.
     */
    @Test
    void masterOfWebsIsWebMovementNotWallClimbing() {
        JsonObject out = wellKnown("origins:master_of_webs");
        assertEquals("neoorigins:cobweb_affinity", out.get("type").getAsString());
        assertTrue(out.get("climb").getAsBoolean());
    }

    /**
     * Blazeborn's damage_from_snowballs is a modify_damage_taken add gated on a
     * snowball projectile. As modify_damage it lost the gate and could never fire
     * on a 0-damage snowball hit. All three upstream spellings, and the bare id.
     */
    @Test
    void snowballDamageIsProjectileVulnerability() {
        for (JsonObject out : new JsonObject[] {
                throughLoader("compat:snow_120", """
                    { "type": "origins:modify_damage_taken", "hidden": true,
                      "modifier": { "name": "Snowball damage taken like Blazes", "value": 3, "operation": "addition" },
                      "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball" } }
                    """),
                throughLoader("compat:snow_121", """
                    { "type": "origins:modify_damage_taken", "hidden": true,
                      "modifier": { "amount": 3, "operation": "add_base_early" },
                      "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball" } }
                    """),
                throughLoader("compat:snow_apoli", """
                    { "type": "apoli:modify_damage_taken", "hidden": true,
                      "apply_armor_condition": "apoli:allow", "attacker_action": "apoli:nothing",
                      "bientity_action": "apoli:nothing", "bientity_condition": "apoli:allow",
                      "damage_armor_condition": "apoli:allow", "self_action": "apoli:nothing",
                      "modifier": { "operation": "apoli:add_base_early", "value": 3.0 },
                      "damage_condition": { "type": "apoli:projectile", "projectile": "minecraft:snowball",
                                            "projectile_condition": "apoli:allow" } }
                    """),
                wellKnown("origins:damage_from_snowballs") }) {
            assertEquals("neoorigins:projectile_vulnerability", out.get("type").getAsString(), out.toString());
            var config = new com.cyberday1.neoorigins.power.builtin.ProjectileVulnerabilityPower().codec()
                .parse(com.mojang.serialization.JsonOps.INSTANCE, out)
                .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
            assertEquals(java.util.List.of("minecraft:snowball"), config.projectiles());
            assertEquals(3.0F, config.amount());
            assertTrue(config.matches(net.minecraft.world.entity.EntityType.SNOWBALL));
            assertTrue(!config.matches(net.minecraft.world.entity.EntityType.ARROW));
        }
    }

    /** Any other modify_damage_taken shape keeps the modify_damage translation. */
    @Test
    void otherModifyDamageTakenShapesAreUntouched() {
        for (String json : new String[] {
                // multiplicative, not a flat add
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 1.0, "operation": "multiply_total" },
                  "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball" } }
                """,
                // inverted: everything except snowballs
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 3, "operation": "addition" },
                  "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball", "inverted": true } }
                """,
                // a real projectile_condition
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 3, "operation": "addition" },
                  "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:arrow",
                                        "projectile_condition": { "type": "origins:on_fire" } } }
                """,
                // a real bientity_condition
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 3, "operation": "addition" },
                  "bientity_condition": { "type": "origins:can_see" },
                  "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball" } }
                """,
                // any projectile, no type
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 3, "operation": "addition" },
                  "damage_condition": { "type": "origins:projectile" } }
                """,
                // a different damage_condition
                """
                { "type": "origins:modify_damage_taken",
                  "modifier": { "value": 3, "operation": "addition" },
                  "damage_condition": { "type": "origins:name", "name": "inFire" } }
                """ }) {
            JsonObject out = translate("compat:other", json);
            assertEquals("neoorigins:modify_damage", out.get("type").getAsString(), json);
        }
        // a power-level condition still defers to Route B
        assertTrue(OriginsPowerTranslator.translate(Identifier.parse("compat:gated"),
            JsonParser.parseString("""
                { "type": "origins:modify_damage_taken", "condition": { "type": "origins:sneaking" },
                  "modifier": { "value": 3, "operation": "addition" },
                  "damage_condition": { "type": "origins:projectile", "projectile": "minecraft:snowball" } }
                """).getAsJsonObject()).isEmpty());
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

    /**
     * Origins' burn_in_daylight is Apoli's BURN(20, 6s) gated on being sun-exposed and
     * not invisible. The stand-in was a bare {@code condition_passive}: no condition, no
     * action, nothing happening. Reported from a pack running it with Essential.
     */
    @Test
    void burnInDaylightActuallyBurns() {
        JsonObject out = wellKnown("origins:burn_in_daylight");
        assertEquals("neoorigins:burn", out.get("type").getAsString());
        assertEquals(20, out.get("interval").getAsInt());
        assertEquals(120, out.get("burn_duration").getAsInt(), "Apoli burns for 6 seconds");
        assertTrue(out.has("condition"), "an unconditional burn would cook the holder indoors");
        String condition = out.get("condition").toString();
        assertTrue(condition.contains("exposed_to_sun"), condition);
        assertTrue(condition.contains("invisible"), "phantomized players do not burn: " + condition);
    }

    /** Upstream hotblooded is immunity to poison and hunger; a bare effect_immunity lists none. */
    @Test
    void hotbloodedNamesTheEffectsItBlocks() {
        JsonObject out = wellKnown("origins:hotblooded");
        assertEquals("neoorigins:effect_immunity", out.get("type").getAsString());
        assertTrue(out.has("effects"), "effect_immunity with no effect list blocks nothing");
        String effects = out.getAsJsonArray("effects").toString();
        assertTrue(effects.contains("minecraft:poison") && effects.contains("minecraft:hunger"), effects);
    }

    /**
     * Upstream water_vulnerability is 2 damage a second in water or rain. It has to be
     * emitted in remapped condition_passive form: `neoorigins:damage_in_water` is a
     * legacy alias, which the injector cannot resolve, so naming it dropped the
     * stand-in silently (see WellKnownTargetsResolveTest).
     */
    @Test
    void waterVulnerabilityActuallyHurts() {
        JsonObject out = wellKnown("origins:water_vulnerability");
        assertEquals("neoorigins:condition_passive", out.get("type").getAsString());
        assertEquals(20, out.get("interval").getAsInt());
        String condition = out.get("condition").toString();
        assertTrue(condition.contains("in_water") && condition.contains("in_rain"), condition);
        JsonObject action = out.getAsJsonObject("entity_action");
        assertEquals("neoorigins:damage", action.get("type").getAsString());
        assertEquals(2.0, action.get("amount").getAsDouble());
    }
}
