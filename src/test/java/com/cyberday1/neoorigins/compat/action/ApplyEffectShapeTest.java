package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Parse-level coverage for the shapes real Apoli/Origins packs write into
 * {@code origins:apply_effect}. Every fixture below is copied verbatim out of the
 * pack corpus, because the two bugs this guards were invisible to schema
 * validation and to the audits: the action parsed, the power loaded, the origin
 * listed it, and nothing happened in game.
 *
 * <p>Bug one: the parser resolved the effect id only from a {@code "effect"}
 * <em>string</em>, but Apoli's documented shape is the nested object
 * {@code "effect": {"effect": "minecraft:speed", "duration": 200}}. Every nested
 * use fell through to a silent no-op.
 *
 * <p>Bug two: {@code "effects": [ … ]} read index 0 and dropped the rest, so a
 * three-effect sting applied one effect.
 */
class ApplyEffectShapeTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }

    /** Run an action against a mock player and return every effect it applied. */
    private static List<MobEffectInstance> applied(String actionJson, String contextId) {
        ServerPlayer player = mock(ServerPlayer.class);
        ActionParser.parse(json(actionJson), contextId).execute(player);
        ArgumentCaptor<MobEffectInstance> captor = ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(player, org.mockito.Mockito.atLeast(0)).addEffect(captor.capture());
        return captor.getAllValues();
    }

    private static String idOf(MobEffectInstance instance) {
        return BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value()).toString();
    }

    // ── Shape 1: nested `effect` object ──────────────────────────────────────

    /**
     * fairytale:sirens_call — the smallest nested-object use in the corpus. The
     * duration and amplifier live inside the nested object, so reading them off
     * the root would silently give the 200/0 defaults even once the id resolved.
     */
    @Test
    void nestedEffectObjectAppliesTheEffectItNames() {
        List<MobEffectInstance> effects = applied("""
            {
              "type": "origins:apply_effect",
              "effect": {
                "effect": "minecraft:slowness",
                "duration": 100,
                "amplifier": 2
              }
            }
            """, "test:nested_effect_object");

        assertEquals(1, effects.size(), "nested effect object must apply exactly one effect");
        MobEffectInstance instance = effects.getFirst();
        assertEquals("minecraft:slowness", idOf(instance));
        assertEquals(100, instance.getDuration(), "duration must come from the nested object");
        assertEquals(2, instance.getAmplifier(), "amplifier must come from the nested object");
    }

    /**
     * fairytale:amphibian_abilities — the full nested field set, including the
     * three display booleans, which also live inside the nested object.
     */
    @Test
    void nestedEffectObjectCarriesTheDisplayFlags() {
        List<MobEffectInstance> effects = applied("""
            {
              "type": "origins:apply_effect",
              "effect": {
                "effect": "minecraft:water_breathing",
                "duration": 220,
                "amplifier": 0,
                "is_ambient": true,
                "show_particles": false,
                "show_icon": true
              }
            }
            """, "test:nested_effect_flags");

        assertEquals(1, effects.size());
        MobEffectInstance instance = effects.getFirst();
        assertEquals("minecraft:water_breathing", idOf(instance));
        assertEquals(220, instance.getDuration());
        assertTrue(instance.isAmbient(), "is_ambient must be read from the nested object");
        assertFalse(instance.isVisible(), "show_particles must be read from the nested object");
        assertTrue(instance.showIcon(), "show_icon must be read from the nested object");
    }

    // ── Shape 2: effects[] ───────────────────────────────────────────────────

    /**
     * origins-plus-plus jellyfish:stinger — three effects in one action. Reading
     * only the first entry meant the sting poisoned but never weakened or slowed.
     */
    @Test
    void everyEntryInEffectsArrayIsApplied() {
        List<MobEffectInstance> effects = applied("""
            {
              "type": "origins:apply_effect",
              "effects": [
                { "effect": "minecraft:poison", "duration": 155, "amplifier": 3 },
                { "effect": "minecraft:weakness", "duration": 135, "amplifier": 9 },
                { "effect": "minecraft:slowness", "duration": 135, "amplifier": 7 }
              ]
            }
            """, "test:effects_array");

        assertEquals(3, effects.size(), "all effects[] entries must be applied, not just the first");
        assertEquals(List.of("minecraft:poison", "minecraft:weakness", "minecraft:slowness"),
            effects.stream().map(ApplyEffectShapeTest::idOf).toList());
        assertEquals(List.of(155, 135, 135), effects.stream().map(MobEffectInstance::getDuration).toList());
        assertEquals(List.of(3, 9, 7), effects.stream().map(MobEffectInstance::getAmplifier).toList());
    }

    /** flowerman:omega — four entries, all sharing the same duration. */
    @Test
    void fourEntryEffectsArrayAppliesAllFour() {
        List<MobEffectInstance> effects = applied("""
            {
              "type": "origins:apply_effect",
              "effects": [
                { "effect": "minecraft:speed", "duration": 40, "amplifier": 0, "show_particles": false, "show_icon": false },
                { "effect": "minecraft:strength", "duration": 40, "amplifier": 0, "show_particles": false, "show_icon": false },
                { "effect": "minecraft:regeneration", "duration": 40, "amplifier": 0, "show_particles": false, "show_icon": false },
                { "effect": "minecraft:resistance", "duration": 40, "amplifier": 0, "show_particles": false, "show_icon": false }
              ]
            }
            """, "test:effects_array_four");

        assertEquals(4, effects.size());
        assertEquals(List.of("minecraft:speed", "minecraft:strength",
                "minecraft:regeneration", "minecraft:resistance"),
            effects.stream().map(ApplyEffectShapeTest::idOf).toList());
        effects.forEach(e -> assertFalse(e.isVisible(), "per-entry show_particles must be honoured"));
    }

    // ── Shape 3: flat root — the shape that already worked ───────────────────

    /** The pre-existing flat shape must keep parsing exactly as it did. */
    @Test
    void flatEffectStringStillWorks() {
        List<MobEffectInstance> effects = applied("""
            {
              "type": "origins:apply_effect",
              "effect": "minecraft:speed",
              "duration": 60,
              "amplifier": 1
            }
            """, "test:flat_effect");

        assertEquals(1, effects.size());
        assertEquals("minecraft:speed", idOf(effects.getFirst()));
        assertEquals(60, effects.getFirst().getDuration());
        assertEquals(1, effects.getFirst().getAmplifier());
    }

    /** `id` remains a synonym for `effect` on the flat shape. */
    @Test
    void flatIdSynonymStillWorks() {
        List<MobEffectInstance> effects = applied("""
            { "type": "origins:apply_effect", "id": "minecraft:haste", "duration": 30 }
            """, "test:flat_id");

        assertEquals(1, effects.size());
        assertEquals("minecraft:haste", idOf(effects.getFirst()));
    }

    /** Nothing resolvable still no-ops — but must not apply anything either. */
    @Test
    void unresolvableEffectAppliesNothing() {
        assertTrue(applied("""
            { "type": "origins:apply_effect" }
            """, "test:no_effect").isEmpty());
    }

    // ── The target path mirrors the player path ─────────────────────────────

    /**
     * The same JSON reaches mobs through {@code origins:target_action} /
     * {@code origins:area_of_effect}, which parse through TargetActionParser.
     * fairytale:sirens_call does exactly that with a nested effect object, so the
     * two paths have to agree.
     */
    @Test
    void targetPathHandlesNestedObjectAndFullEffectsArray() {
        LivingEntity target = mock(LivingEntity.class);
        ServerPlayer actor = mock(ServerPlayer.class);

        TargetActionParser.parse(json("""
            {
              "type": "origins:apply_effect",
              "effect": { "effect": "minecraft:slowness", "duration": 100, "amplifier": 2 }
            }
            """), "test:target_nested").execute(target, actor);

        TargetActionParser.parse(json("""
            {
              "type": "origins:apply_effect",
              "effects": [
                { "effect": "minecraft:poison", "duration": 55 },
                { "effect": "minecraft:weakness", "duration": 35 },
                { "effect": "minecraft:slowness", "duration": 35 }
              ]
            }
            """), "test:target_array").execute(target, actor);

        ArgumentCaptor<MobEffectInstance> captor = ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(target, org.mockito.Mockito.times(4)).addEffect(captor.capture());
        List<String> ids = captor.getAllValues().stream().map(ApplyEffectShapeTest::idOf).toList();
        assertEquals(List.of("minecraft:slowness", "minecraft:poison",
            "minecraft:weakness", "minecraft:slowness"), ids);
        assertEquals(100, captor.getAllValues().getFirst().getDuration());
        assertEquals(2, captor.getAllValues().getFirst().getAmplifier());
    }

    /** Sanity: the ids the fixtures name are the effects we assert on. */
    @Test
    void fixtureEffectIdsAreRegistered() {
        assertEquals("minecraft:slowness",
            BuiltInRegistries.MOB_EFFECT.getKey(MobEffects.MOVEMENT_SLOWDOWN.value()).toString());
    }
}
