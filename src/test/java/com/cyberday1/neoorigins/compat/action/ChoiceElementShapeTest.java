package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Parse-level coverage for {@code origins:choice}. Its {@code actions} array holds
 * WRAPPER records, not actions, and Apoli keys the wrapped action {@code element}.
 * The parser read {@code action} instead, so every weighted branch resolved to a
 * no-op: the power loaded, showed up on the origin, and did nothing whichever way
 * the roll went.
 *
 * <p>The fixtures are verbatim from the corpus — origins-plus-plus flea:bloodsucking
 * and ice-king:unresolved_size — so the weights and the nested-effect shapes are
 * the ones actually shipped.
 */
class ChoiceElementShapeTest {

    /**
     * flea:bloodsucking, the inner {@code origins:choice} lifted out of its
     * {@code chance} wrapper. Weights 6/9/5/10/30/3 — cumulative bounds 0, 6, 15,
     * 20, 30, 60 — and every branch is a nested-object apply_effect.
     */
    private static final String BLOODSUCKING = """
        {
          "type": "origins:choice",
          "actions": [
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:strength", "amplifier": 5, "duration": 60, "show_particles": false, "show_icon": false } }, "weight": 6 },
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:haste", "amplifier": 5, "duration": 100, "show_particles": false, "show_icon": false } }, "weight": 9 },
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:resistance", "amplifier": 2, "duration": 400, "show_particles": false, "show_icon": false } }, "weight": 5 },
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:regeneration", "amplifier": 1, "duration": 100, "show_particles": false, "show_icon": false } }, "weight": 10 },
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:saturation", "amplifier": 1, "duration": 240, "show_particles": false, "show_icon": false } }, "weight": 30 },
            { "element": { "type": "origins:apply_effect", "effect": { "effect": "minecraft:speed", "amplifier": 5, "duration": 300, "show_particles": false, "show_icon": false } }, "weight": 3 }
          ]
        }
        """;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** A player whose weighted roll is pinned, so a chosen branch is deterministic. */
    private static ServerPlayer playerRolling(int roll) {
        RandomSource random = mock(RandomSource.class);
        when(random.nextInt(anyInt())).thenReturn(roll);
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.getRandom()).thenReturn(random);
        return player;
    }

    private static String idOf(MobEffectInstance instance) {
        return BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).toString();
    }

    /**
     * Every one of the six weighted branches must be a live action. Rolling the
     * first index of each weight band picks that band; before the fix all six rolls
     * produced no effect at all.
     */
    @Test
    void everyWeightedBranchAppliesItsOwnEffect() {
        // cumulative lower bound of each band, from the shipped weights (total 63)
        int[] rolls = { 0, 6, 15, 20, 30, 60 };
        List<String> expected = List.of("minecraft:strength", "minecraft:haste",
            "minecraft:resistance", "minecraft:regeneration", "minecraft:saturation",
            "minecraft:speed");
        int[] expectedDurations = { 60, 100, 400, 100, 240, 300 };
        int[] expectedAmplifiers = { 5, 5, 2, 1, 1, 5 };

        for (int i = 0; i < rolls.length; i++) {
            ServerPlayer player = playerRolling(rolls[i]);
            ActionParser.parse(json(BLOODSUCKING), "test:choice_bloodsucking").execute(player);

            ArgumentCaptor<MobEffectInstance> captor = ArgumentCaptor.forClass(MobEffectInstance.class);
            verify(player).addEffect(captor.capture());
            MobEffectInstance instance = captor.getValue();
            assertEquals(expected.get(i), idOf(instance), "branch " + i + " applied the wrong effect");
            assertEquals(expectedDurations[i], instance.getDuration(), "branch " + i + " duration");
            assertEquals(expectedAmplifiers[i], instance.getAmplifier(), "branch " + i + " amplifier");
        }
    }

    /**
     * ice-king:unresolved_size uses {@code element} to hold a non-effect action.
     * Trimmed to two of its sixteen equal-weight branches; the point is that the
     * chosen element is a real action rather than a no-op.
     */
    @Test
    void elementHoldingANonEffectActionAlsoRuns() {
        String actionJson = """
            {
              "type": "origins:choice",
              "actions": [
                { "weight": 10, "element": { "type": "origins:heal", "amount": 1.5 } },
                { "weight": 10, "element": { "type": "origins:heal", "amount": 2.5 } }
              ]
            }
            """;

        ServerPlayer first = playerRolling(0);
        ActionParser.parse(json(actionJson), "test:choice_first").execute(first);
        verify(first).heal(1.5F);

        ServerPlayer second = playerRolling(15);
        ActionParser.parse(json(actionJson), "test:choice_second").execute(second);
        verify(second).heal(2.5F);
    }

    /**
     * The pre-fix key stays accepted. NeoOrigins' own docs used {@code action}, so
     * anything authored against them must keep working.
     */
    @Test
    void actionKeyRemainsAcceptedAsASynonym() {
        ServerPlayer player = playerRolling(0);
        ActionParser.parse(json("""
            {
              "type": "origins:choice",
              "actions": [
                { "action": { "type": "origins:heal", "amount": 4.0 }, "weight": 1 }
              ]
            }
            """), "test:choice_action_key").execute(player);

        verify(player).heal(4.0F);
    }

    /** `element` wins when an entry carries both keys, matching Apoli. */
    @Test
    void elementWinsOverActionWhenBothArePresent() {
        ServerPlayer player = playerRolling(0);
        ActionParser.parse(json("""
            {
              "type": "origins:choice",
              "actions": [
                {
                  "element": { "type": "origins:heal", "amount": 7.0 },
                  "action": { "type": "origins:heal", "amount": 1.0 },
                  "weight": 1
                }
              ]
            }
            """), "test:choice_both_keys").execute(player);

        verify(player).heal(7.0F);
        verify(player, org.mockito.Mockito.never()).heal(1.0F);
    }

    /** A choice with no usable list still no-ops rather than throwing. */
    @Test
    void emptyChoiceIsHarmless() {
        ServerPlayer player = playerRolling(0);
        assertTrue(ActionParser.parse(json("""
            { "type": "origins:choice", "actions": [] }
            """), "test:choice_empty") != null);
        ActionParser.parse(json("""
            { "type": "origins:choice", "actions": [] }
            """), "test:choice_empty").execute(player);
    }
}
