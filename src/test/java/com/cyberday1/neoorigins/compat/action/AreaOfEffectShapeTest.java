package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * area_of_effect's Apoli-canonical shapes. Two separate faults made every
 * third-party AoE a silent no-op: {@code shape} written as an object (the cone
 * form Fairytale's huffing_puffing and bull_rush use) threw inside
 * {@code getAsString}, and the per-target action was only ever read from
 * {@code entity_action} — but all fourteen third-party usages write Apoli's
 * {@code bientity_action} instead, so they compiled to nothing with no warning.
 *
 * <p>The fan-out is exercised end to end against mocked players: a mocked
 * {@link ServerPlayer} target keeps the friendly-fire filter (config-backed,
 * mob-only) out of the picture while still proving which entities the gates let
 * through.
 */
class AreaOfEffectShapeTest {

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    /** A caster at the origin looking due +X, with the given entities in range. */
    private static ServerPlayer caster(LivingEntity... inRange) {
        ServerPlayer source = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(source.level()).thenReturn(level);
        when(source.position()).thenReturn(Vec3.ZERO);
        when(source.getBoundingBox()).thenReturn(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3));
        when(source.getLookAngle()).thenReturn(new Vec3(1.0, 0.0, 0.0));
        when(source.getUUID()).thenReturn(UUID.randomUUID());
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.of(inRange));
        return source;
    }

    /** A caster whose level reports the caster itself as the only entity in range. */
    private static ServerPlayer casterSeeingItself() {
        ServerPlayer source = caster();
        when(source.level().getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.<LivingEntity>of(source));
        return source;
    }

    private static ServerPlayer victimAt(double x, double y, double z) {
        ServerPlayer victim = mock(ServerPlayer.class);
        when(victim.position()).thenReturn(new Vec3(x, y, z));
        return victim;
    }

    /** Fairytale's breath-attack shape: a 60° cone carrying a bientity_action. */
    private static JsonObject coneAoe() {
        return obj("""
            {
              "type": "origins:area_of_effect",
              "radius": 8,
              "shape": { "type": "origins:cone", "angle": 60 },
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 3 }
              }
            }
            """);
    }

    // ── the object-shaped `shape` regression ─────────────────────────────

    @Test
    void anObjectShapedShapeDoesNotThrowAtParseTime() {
        assertDoesNotThrow(() -> ActionParser.parse(coneAoe(), "test:huffing_puffing"),
            "an object `shape` used to throw UnsupportedOperationException in getAsString");
        assertNotNull(ActionParser.parse(coneAoe(), "test:huffing_puffing"));
    }

    @Test
    void aStringShapedShapeStillParses() {
        assertNotNull(ActionParser.parse(obj("""
            { "type": "origins:area_of_effect", "radius": 4, "shape": "cube",
              "entity_action": { "type": "origins:heal", "amount": 1 } }
            """), "test:legacy_cube"));
    }

    // ── the cone gate ────────────────────────────────────────────────────

    /** Directly ahead of the caster is inside a 60° cone (dot 1.0 >= cos 30°). */
    @Test
    void coneHitsEntitiesInFrontOfTheCaster() {
        ServerPlayer victim = victimAt(5.0, 0.0, 0.0);
        ActionParser.parse(coneAoe(), "test:cone_front").execute(caster(victim));
        verify(victim).heal(3.0F);
    }

    /** Behind the caster is outside it, though well inside the radius. */
    @Test
    void coneSparesEntitiesBehindTheCaster() {
        ServerPlayer victim = victimAt(-5.0, 0.0, 0.0);
        ActionParser.parse(coneAoe(), "test:cone_behind").execute(caster(victim));
        verify(victim, never()).heal(anyFloat());
    }

    /** Just outside the half-angle: 45° off the look vector, cone half-width 30°. */
    @Test
    void coneSparesEntitiesOutsideTheHalfAngle() {
        ServerPlayer victim = victimAt(5.0, 0.0, 5.0);
        ActionParser.parse(coneAoe(), "test:cone_edge").execute(caster(victim));
        verify(victim, never()).heal(anyFloat());
    }

    /** A sphere of the same radius has no angular gate, so the same entity is hit. */
    @Test
    void sphereHitsWhatTheConeSpared() {
        ServerPlayer victim = victimAt(-5.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "origins:area_of_effect",
              "radius": 8,
              "shape": "sphere",
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 3 }
              }
            }
            """), "test:sphere").execute(caster(victim));
        verify(victim).heal(3.0F);
    }

    /** Sphere still bounds on distance, unlike cube. */
    @Test
    void sphereSparesEntitiesOutsideTheRadius() {
        ServerPlayer victim = victimAt(30.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "origins:area_of_effect", "radius": 8,
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 3 }
              }
            }
            """), "test:sphere_far").execute(caster(victim));
        verify(victim, never()).heal(anyFloat());
    }

    // ── bientity_action is read at all ───────────────────────────────────

    /**
     * The whole reason the third-party AoEs were no-ops: bientity_action was
     * never read. A bare array form is wrapped in a synthetic and, so both
     * spellings must dispatch.
     */
    @Test
    void bientityActionArrayFormDispatches() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_action": [
                { "type": "apoli:target_action", "action": { "type": "origins:heal", "amount": 1 } },
                { "type": "apoli:target_action", "action": { "type": "origins:heal", "amount": 2 } }
              ]
            }
            """), "test:bientity_array").execute(caster(victim));
        verify(victim).heal(1.0F);
        verify(victim).heal(2.0F);
    }

    // ── include-self defaults ────────────────────────────────────────────

    /**
     * The two forms default differently and both defaults are load-bearing: a
     * bientity_action AoE is a breath/dash attack that must not hit its caster,
     * while the legacy entity_action form has always included them (self-buff
     * auras rely on it).
     */
    @Test
    void bientityActionExcludesTheCasterByDefault() {
        ServerPlayer source = casterSeeingItself();
        ActionParser.parse(coneAoe(), "test:self_exclusion").execute(source);
        verify(source, never()).heal(anyFloat());
    }

    /** The legacy entity_action form keeps including the caster. */
    @Test
    void entityActionIncludesTheCasterByDefault() {
        ServerPlayer source = casterSeeingItself();
        ActionParser.parse(obj("""
            { "type": "origins:area_of_effect", "radius": 6,
              "entity_action": { "type": "origins:heal", "amount": 2 } }
            """), "test:self_buff_aura").execute(source);
        verify(source).heal(2.0F);
    }

    @Test
    void includeTargetTrueBringsTheCasterBackIn() {
        ServerPlayer source = casterSeeingItself();
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect", "radius": 6, "include_target": true,
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 4 }
              }
            }
            """), "test:include_target").execute(source);
        verify(source).heal(4.0F);
    }

    // ── bientity_condition fails closed ──────────────────────────────────

    /**
     * A filter that cannot be compiled would otherwise be dropped, turning
     * "spare players" into "hit everyone". The entity fan-out is skipped
     * instead, and the failure is recorded rather than silently swallowed.
     */
    @Test
    void anUncompilableBientityConditionSkipsTheFanOut() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_condition": { "type": "apoli:no_such_bientity_verb" },
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 5 }
              }
            }
            """), "test:broken_filter").execute(caster(victim));
        verify(victim, never()).heal(anyFloat());
    }

    /** A compilable filter that rejects the target also spares it. */
    @Test
    void aFalseBientityConditionSparesTheTarget() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_condition": { "type": "apoli:constant", "value": false },
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 5 }
              }
            }
            """), "test:false_filter").execute(caster(victim));
        verify(victim, never()).heal(anyFloat());
    }

    // ── can_see ──────────────────────────────────────────────────────────

    /**
     * Origins++ Ignisian filters both its quake and its wrath on
     * {@code origins:can_see}. It is the only bientity filter either power
     * carries, so before the verb existed the whole fan-out was skipped.
     */
    private static JsonObject canSeeAoe() {
        return obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_condition": { "type": "origins:can_see" },
              "bientity_action": {
                "type": "apoli:target_action",
                "action": { "type": "origins:heal", "amount": 5 }
              }
            }
            """);
    }

    @Test
    void canSeeLetsAVisibleTargetThrough() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ServerPlayer source = caster(victim);
        when(source.hasLineOfSight(victim)).thenReturn(true);
        ActionParser.parse(canSeeAoe(), "test:ignisian_wrath").execute(source);
        verify(victim).heal(5.0f);
    }

    @Test
    void canSeeSparesATargetBehindAWall() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ServerPlayer source = caster(victim);
        when(source.hasLineOfSight(victim)).thenReturn(false);
        ActionParser.parse(canSeeAoe(), "test:ignisian_quake").execute(source);
        verify(victim, never()).heal(anyFloat());
    }

    // ── bientity if_else ─────────────────────────────────────────────────

    /**
     * A bientity {@code if_else} branches on a bientity condition. Routed
     * through the loose entity-action fallback its {@code target_condition}
     * meant nothing, failed closed, and the else branch always won.
     */
    private static JsonObject ifElseAoe(boolean conditionValue) {
        return obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_action": {
                "type": "apoli:if_else",
                "condition": { "type": "apoli:constant", "value": %s },
                "if_action": {
                  "type": "apoli:target_action",
                  "action": { "type": "origins:heal", "amount": 5 }
                },
                "else_action": {
                  "type": "apoli:target_action",
                  "action": { "type": "origins:heal", "amount": 1 }
                }
              }
            }
            """.formatted(conditionValue));
    }

    @Test
    void bientityIfElseTakesTheIfBranch() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(ifElseAoe(true), "test:hindsight_hit").execute(caster(victim));
        verify(victim).heal(5.0f);
        verify(victim, never()).heal(1.0f);
    }

    @Test
    void bientityIfElseTakesTheElseBranch() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(ifElseAoe(false), "test:hindsight_miss").execute(caster(victim));
        verify(victim).heal(1.0f);
        verify(victim, never()).heal(5.0f);
    }

    /**
     * A {@code target_condition} leaf must reach the entity-general engine
     * rather than the player-typed parser: this is exactly the shape Chaotic
     * Chemist writes, and it has to be able to say "yes".
     */
    @Test
    void bientityIfElseEvaluatesATargetConditionLeaf() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_action": {
                "type": "apoli:if_else",
                "condition": {
                  "type": "apoli:target_condition",
                  "condition": { "type": "apoli:constant", "value": true }
                },
                "if_action": {
                  "type": "apoli:target_action",
                  "action": { "type": "origins:heal", "amount": 5 }
                }
              }
            }
            """), "test:hindsight_target").execute(caster(victim));
        verify(victim).heal(5.0f);
    }

    /** An uncompilable branch condition still runs the else side, but is recorded. */
    @Test
    void bientityIfElseWithAnUncompilableConditionTakesTheElseBranch() {
        ServerPlayer victim = victimAt(2.0, 0.0, 0.0);
        ActionParser.parse(obj("""
            {
              "type": "apoli:area_of_effect",
              "radius": 6,
              "bientity_action": {
                "type": "apoli:if_else",
                "condition": { "type": "apoli:no_such_bientity_verb" },
                "if_action": {
                  "type": "apoli:target_action",
                  "action": { "type": "origins:heal", "amount": 5 }
                },
                "else_action": {
                  "type": "apoli:target_action",
                  "action": { "type": "origins:heal", "amount": 1 }
                }
              }
            }
            """), "test:broken_branch").execute(caster(victim));
        verify(victim).heal(1.0f);
        verify(victim, never()).heal(5.0f);
    }
}
