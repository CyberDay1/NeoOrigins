package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Load-path coverage for the two cross-mod aliases that pointed at nothing:
 * {@code apugli:action_on_jump} and {@code apugli:action_on_target_death}.
 *
 * <p>Both were registered in {@link LegacyPowerTypeAliases} as thin translators
 * onto {@code neoorigins:action_on_event}, and both were dead on arrival. The
 * loader canonicalises {@code apugli:} to {@code origins:} in place and then runs
 * Route A, which drops a power outright when no translation case matches — and
 * both of those run BEFORE the alias pass. So by remap time the alias table was
 * being asked about {@code origins:action_on_jump}, an id it has never held, and
 * the power was already gone. Neither type has an {@code origins:} dispatch case
 * on either route, so nothing downstream picked them back up: a pack shipping one
 * lost the whole power, silently, with only a "no Route A translation" line in
 * the compat log to show for it.
 *
 * <p>Compile-green cannot see any of that, and neither can the schema: the type
 * was well-formed, the alias registration was correct, and the failure was
 * purely one of reachability. Hence a parse-level test — it asserts the power
 * survives the pipeline AND that the injected {@code event} key lands where
 * {@code ActionOnEventPower} reads it, rather than merely that nothing threw.
 */
class ApugliAliasLoadPathTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        LegacyPowerTypeAliases.bootstrap();
    }

    /** Run one power body through the real pre-parse pipeline, as the loader does. */
    private static PowerDataManager.Resolved resolve(String powerId, String body) {
        return PowerDataManager.resolvePowerType(
            Identifier.parse(powerId),
            JsonParser.parseString(body).getAsJsonObject());
    }

    /** Decode a resolved body through action_on_event's own codec. */
    private static ActionOnEventPower.Config decode(JsonObject json) {
        return ActionOnEventPower.Config.CODEC.parse(JsonOps.INSTANCE, json)
            .getOrThrow(msg -> new AssertionError("action_on_event decode failed: " + msg));
    }

    /** Effects the config's action applies when it is fired at a player. */
    private static List<MobEffectInstance> fire(ActionOnEventPower.Config config) {
        ServerPlayer player = mock(ServerPlayer.class);
        config.action().execute(player);
        ArgumentCaptor<MobEffectInstance> captor = ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(player, org.mockito.Mockito.atLeast(0)).addEffect(captor.capture());
        return captor.getAllValues();
    }

    private static String idOf(MobEffectInstance instance) {
        return BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).toString();
    }

    // ── The two casualties ──────────────────────────────────────────────────

    /**
     * The Apugli shape verbatim: a bare {@code entity_action}, no event key of
     * its own — the event is what the alias exists to supply.
     */
    @Test
    void actionOnJumpSurvivesTheLoaderAndCarriesTheJumpEvent() {
        var resolved = resolve("testpack:leaper", """
            {
              "type": "apugli:action_on_jump",
              "entity_action": {
                "type": "origins:apply_effect",
                "effect": { "effect": "minecraft:speed", "duration": 60, "amplifier": 1 }
              }
            }
            """);

        assertNotNull(resolved, "apugli:action_on_jump must not be dropped before parse");
        assertEquals(Identifier.parse("neoorigins:action_on_event"), resolved.typeId(),
            "the alias must remap the type to action_on_event");
        assertEquals("jump", resolved.json().get("event").getAsString(),
            "the alias remap must inject the event key into the body");

        // The key has to survive as far as the codec, not merely exist on the JSON.
        var config = decode(resolved.json());
        assertEquals(EventPowerIndex.Event.JUMP, config.event(),
            "the injected event must reach action_on_event's dispatch registration");
        assertNotSame(EntityAction.noop(), config.action(),
            "the pack's entity_action must survive the remap as a live action");

        List<MobEffectInstance> applied = fire(config);
        assertEquals(1, applied.size(), "firing the JUMP hook must run the pack's action");
        assertEquals("minecraft:speed", idOf(applied.getFirst()));
        assertEquals(60, applied.getFirst().getDuration());
    }

    /** Same, for the kill-side hook. */
    @Test
    void actionOnTargetDeathSurvivesTheLoaderAndCarriesTheKillEvent() {
        var resolved = resolve("testpack:soul_harvest", """
            {
              "type": "apugli:action_on_target_death",
              "entity_action": {
                "type": "origins:apply_effect",
                "effect": { "effect": "minecraft:regeneration", "duration": 100 }
              }
            }
            """);

        assertNotNull(resolved, "apugli:action_on_target_death must not be dropped before parse");
        assertEquals(Identifier.parse("neoorigins:action_on_event"), resolved.typeId());
        assertEquals("kill", resolved.json().get("event").getAsString());

        var config = decode(resolved.json());
        assertEquals(EventPowerIndex.Event.KILL, config.event(),
            "the injected event must reach action_on_event's dispatch registration");
        assertNotSame(EntityAction.noop(), config.action());

        List<MobEffectInstance> applied = fire(config);
        assertEquals(1, applied.size(), "firing the KILL hook must run the pack's action");
        assertEquals("minecraft:regeneration", idOf(applied.getFirst()));
    }

    /**
     * The fields the branch offers have to be the fields that work. `condition`
     * and `cooldown_ticks` pass through the alias untouched and are read by
     * ActionOnEventPower on every event, so an author writing them on the Apugli
     * spelling must get them.
     */
    @Test
    void passthroughFieldsSurviveTheAliasRemap() {
        var resolved = resolve("testpack:leaper_gated", """
            {
              "type": "apugli:action_on_jump",
              "cooldown_ticks": 40,
              "condition": { "type": "origins:sneaking" },
              "entity_action": { "type": "origins:apply_effect", "effect": "minecraft:speed" }
            }
            """);

        assertNotNull(resolved);
        var config = decode(resolved.json());
        assertEquals(EventPowerIndex.Event.JUMP, config.event());
        assertEquals(40, config.cooldownTicks(), "cooldown_ticks must reach the codec");
        assertNotSame(EntityAction.noop(), config.action());
    }

    // ── The fallback must stay on the drop path only ─────────────────────────

    /**
     * {@code apoli:}/{@code apugli:edible_item} are the other two cross-mod alias
     * entries, and they were never broken: {@code origins:edible_item} has a Route
     * A case, so canonicalisation lands them on a real translation. The alias
     * fallback must not intercept them — Route A still owns that routing.
     */
    @Test
    void edibleItemStillRoutesThroughRouteANotTheAlias() {
        var resolved = resolve("testpack:snack", """
            {
              "type": "apoli:edible_item",
              "item": "minecraft:rotten_flesh",
              "food_component": { "nutrition": 4, "saturation_modifier": 0.6 }
            }
            """);

        assertNotNull(resolved);
        assertEquals(Identifier.parse("neoorigins:edible_item"), resolved.typeId());
        // Route A rebuilds the body from scratch (items[], saturation); the alias
        // remapper mutates in place and would have left `food_component` behind.
        assertEquals(4, resolved.json().get("nutrition").getAsInt());
        assertEquals("minecraft:rotten_flesh",
            resolved.json().getAsJsonArray("items").get(0).getAsString());
    }

    /**
     * Route B runs after this pipeline and legitimately claims the types Route A
     * skips. A skipped type must still come back null here so Route B gets it —
     * the fallback is gated on the alias table, and nothing in it collides with a
     * Route B id, but this pins that.
     */
    @Test
    void routeBTypesAreStillLeftForRouteB() {
        assertNull(resolve("testpack:vamp_bite", """
            { "type": "apoli:action_on_hit", "entity_action": { "type": "origins:apply_effect" } }
            """), "a Route B type must be left unresolved here, not claimed by the alias table");
    }

    /** An Apoli-family id with no case on either route and no alias still drops. */
    @Test
    void unknownApoliFamilyTypeStillDrops() {
        assertNull(resolve("testpack:nonsense", """
            { "type": "apugli:no_such_power_type_exists" }
            """), "the fallback must only fire for ids the alias table actually holds");
    }
}
