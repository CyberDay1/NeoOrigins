package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The {@code condition} gate on {@code neoorigins:creative_flight} and
 * {@code neoorigins:active_dash}.
 *
 * <p>Both types shipped without one, which made "flight only while you hold a
 * sword" literally inexpressible: the built-in Sword Immortal promised in its
 * own description that every art is bound to the blade in hand, and then flew
 * empty-handed. These tests pin the gate itself and, just as importantly, the
 * fact that it is re-tested every tick — a check made only at takeoff would let
 * a player sheathe the blade in mid-air and keep flying.
 *
 * <p>{@code neoorigins:sneaking} stands in for the real sword condition because
 * it resolves with no registry: the gate is what is under test, not the verb.
 */
class SwordArtConditionGateTest {

    private static final CreativeFlightPower FLIGHT = new CreativeFlightPower();
    private static final ActiveDashPower DASH = new ActiveDashPower();

    private static CreativeFlightPower.Config flight(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return CreativeFlightPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    private static ActiveDashPower.Config dash(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return ActiveDashPower.Config.CODEC.parse(JsonOps.INSTANCE, obj)
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    /** A player whose only interesting property is whether it is sneaking. */
    private static ServerPlayer player(Abilities abilities, boolean sneaking) {
        ServerPlayer p = mock(ServerPlayer.class);
        when(p.getAbilities()).thenReturn(abilities);
        when(p.isShiftKeyDown()).thenReturn(sneaking);
        return p;
    }

    private static final String SNEAK_GATED = """
        { "type": "neoorigins:creative_flight", "condition": { "type": "neoorigins:sneaking" } }
        """;

    // ── Parsing ─────────────────────────────────────────────────────────

    @Test
    void creativeFlightWithoutAConditionStaysUnconditional() {
        // Every existing creative_flight in the wild omits the field; it must keep
        // meaning "always fly", not "never fly".
        var cfg = flight("""
            { "type": "neoorigins:creative_flight" }
            """);
        assertTrue(cfg.condition().test(player(new Abilities(), false)));
    }

    @Test
    void creativeFlightKeepsItsOtherFieldsAcrossTheCodecRewrite() {
        // The codec went from a RecordCodecBuilder group to a hand-rolled decoder
        // to make room for `condition`; the fields it already had must survive.
        var cfg = flight("""
            {
              "type": "neoorigins:creative_flight",
              "enabled": false,
              "cooldown_icon": "minecraft:diamond_sword",
              "always_show_icon": true
            }
            """);
        assertFalse(cfg.enabled());
        assertEquals("minecraft:diamond_sword", cfg.cooldownIcon());
        assertTrue(cfg.alwaysShowIcon());
        assertEquals("neoorigins:creative_flight", cfg.type());
    }

    @Test
    void activeDashKeepsItsOtherFieldsAcrossTheCodecRewrite() {
        var cfg = dash("""
            {
              "type": "neoorigins:active_dash",
              "power": 2.5,
              "cooldown_ticks": 80,
              "allow_vertical": true,
              "set_velocity": true,
              "damage": 6.0,
              "damage_radius": 2.5,
              "weapon_damage_scale": 0.5,
              "cooldown_icon": "minecraft:diamond_sword",
              "cooldown_countdown": false,
              "always_show_icon": true
            }
            """);
        assertEquals(2.5f, cfg.power());
        assertEquals(80, cfg.cooldownTicks());
        assertTrue(cfg.allowVertical());
        assertTrue(cfg.setVelocity());
        assertEquals(6.0f, cfg.damage());
        assertEquals(2.5f, cfg.damageRadius());
        assertEquals(0.5f, cfg.weaponDamageScale());
        assertEquals("minecraft:diamond_sword", cfg.cooldownIcon());
        assertFalse(cfg.cooldownCountdown());
        assertTrue(cfg.alwaysShowIcon());
    }

    @Test
    void activeDashDefaultsAreUnchanged() {
        var cfg = dash("""
            { "type": "neoorigins:active_dash" }
            """);
        assertEquals(1.5f, cfg.power());
        assertEquals(40, cfg.cooldownTicks());
        assertFalse(cfg.allowVertical());
        assertFalse(cfg.setVelocity());
        assertEquals(0f, cfg.damage());
        assertEquals(2.0f, cfg.damageRadius());
        assertEquals(0f, cfg.weaponDamageScale());
        // cooldown_countdown is the one field that defaults to TRUE; an absent key
        // must not read as false.
        assertTrue(cfg.cooldownCountdown());
        assertTrue(cfg.condition().test(player(new Abilities(), false)));
    }

    // ── creative_flight: the gate ───────────────────────────────────────

    @Test
    void flightIsGrantedWhileTheConditionHolds() {
        Abilities abilities = new Abilities();
        ServerPlayer p = player(abilities, true);
        FLIGHT.tickEffect(p, flight(SNEAK_GATED));
        assertTrue(abilities.mayfly);
    }

    @Test
    void flightIsNeverGrantedWhileTheConditionFails() {
        Abilities abilities = new Abilities();
        ServerPlayer p = player(abilities, false);
        FLIGHT.tickEffect(p, flight(SNEAK_GATED));
        assertFalse(abilities.mayfly);
    }

    @Test
    void flightIsStrippedMidAirWhenTheConditionStopsHolding() {
        // The whole point: a condition checked only at takeoff would let the player
        // sheathe the sword in the sky and keep flying.
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        abilities.flying = true;
        ServerPlayer p = player(abilities, false);

        FLIGHT.tickEffect(p, flight(SNEAK_GATED));

        assertFalse(abilities.mayfly);
        assertFalse(abilities.flying);
    }

    @Test
    void strippedFlightIsNotReStrippedEveryTick() {
        // removeEffect zeroes fallDistance. The tick path now calls it on every
        // tick the condition is false, so without a no-op guard a grounded,
        // sword-less Sword Immortal would be permanently fall-damage-proof — the
        // exact opposite of the intended cost.
        Abilities abilities = new Abilities();
        ServerPlayer p = player(abilities, false);
        p.fallDistance = 12.0F;

        for (int tick = 0; tick < 5; tick++) {
            FLIGHT.tickEffect(p, flight(SNEAK_GATED));
        }

        assertEquals(12.0F, p.fallDistance);
        verify(p, never()).onUpdateAbilities();
    }

    @Test
    void togglingOnWhileTheConditionFailsDoesNotLift() {
        Abilities abilities = new Abilities();
        ServerPlayer p = player(abilities, false);

        FLIGHT.onToggledOn(p, flight(SNEAK_GATED));

        assertFalse(abilities.mayfly);
        assertFalse(abilities.flying);
    }

    @Test
    void disabledStillWinsOverAPassingCondition() {
        // The power_overrides kill-switch is checked first and must not be
        // reachable-around by authoring a condition that happens to pass.
        Abilities abilities = new Abilities();
        abilities.mayfly = true;
        ServerPlayer p = player(abilities, true);

        FLIGHT.tickEffect(p, flight("""
            {
              "type": "neoorigins:creative_flight",
              "enabled": false,
              "condition": { "type": "neoorigins:sneaking" }
            }
            """));

        assertFalse(abilities.mayfly);
    }

    // ── active_dash: the gate ───────────────────────────────────────────

    @Test
    void dashRefusesAndSpendsNoCooldownWhileTheConditionFails() {
        // execute() returning false is the base-class contract for "nothing
        // happened" — the cooldown stays untouched, so a blocked dash is free.
        ServerPlayer p = player(new Abilities(), false);
        assertFalse(DASH.execute(p, dash("""
            {
              "type": "neoorigins:active_dash",
              "set_velocity": true,
              "condition": { "type": "neoorigins:sneaking" }
            }
            """)));
        verify(p, never()).setDeltaMovement(org.mockito.ArgumentMatchers.any(Vec3.class));
    }

    @Test
    void dashFiresWhileTheConditionHolds() {
        ServerPlayer p = player(new Abilities(), true);
        when(p.getLookAngle()).thenReturn(new Vec3(1, 0, 0));
        assertTrue(DASH.execute(p, dash("""
            {
              "type": "neoorigins:active_dash",
              "set_velocity": true,
              "condition": { "type": "neoorigins:sneaking" }
            }
            """)));
    }

    // ── Routing and degradation ─────────────────────────────────────────

    @Test
    void bothTypesClaimConditionSoTheGenericAliasStandsDown() {
        // Load bearing, and invisible from either file on its own. PowerDataManager
        // treats a top-level `condition` on a type that does NOT list one as an
        // alias for `power_condition`, which gates the power by SKIPPING onTick.
        // Skipping the tick never calls removeEffect, so an aliased creative_flight
        // would leave mayfly granted and the player airborne — the condition would
        // read as authored and do nothing. Declaring the field here is what keeps
        // the codec, and therefore the strip, in charge.
        for (String type : List.of("creative_flight", "active_dash")) {
            var spec = BuiltinPowers.get(Identifier.fromNamespaceAndPath("neoorigins", type));
            assertTrue(spec != null && spec.fields().stream().anyMatch(f -> "condition".equals(f.name())),
                type + " must declare a 'condition' FieldSpec");
        }
    }

    @Test
    void malformedFieldsDegradeToADecodeErrorRatherThanThrowing() {
        // Both codecs were RecordCodecBuilder groups, which returned an error the
        // loader logged and skipped. A hand-rolled decoder that lets
        // NumberFormatException escape would turn one typo'd power into a failed
        // reload of every power.
        JsonObject bad = JsonParser.parseString("""
            { "type": "neoorigins:active_dash", "power": "fast" }
            """).getAsJsonObject();
        assertTrue(ActiveDashPower.Config.CODEC.parse(JsonOps.INSTANCE, bad).error().isPresent());

        assertTrue(CreativeFlightPower.Config.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString("\"nope\""))
            .error().isPresent());
    }
}
