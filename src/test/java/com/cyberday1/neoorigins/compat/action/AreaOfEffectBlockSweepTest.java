package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * An area_of_effect whose block_action is picked from the menu, with no radius
 * typed, ran the block action at ~17,000 positions per activation; with explode
 * that was a server-stalling lag bomb reachable by clicks alone.
 */
class AreaOfEffectBlockSweepTest {

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ServerPlayer caster(ServerLevel level) {
        ServerPlayer source = mock(ServerPlayer.class);
        when(source.level()).thenReturn(level);
        when(source.position()).thenReturn(Vec3.ZERO);
        when(source.getBoundingBox()).thenReturn(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3));
        when(source.getUUID()).thenReturn(UUID.randomUUID());
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class))).thenReturn(List.of());
        return source;
    }

    private static long calls(Object mock, String method) {
        return mockingDetails(mock).getInvocations().stream()
            .filter(i -> i.getMethod().getName().equals(method)).count();
    }

    private static EntityAction aoe(String radius, String blockAction) {
        return ActionParser.parse(JsonParser.parseString("{\"type\":\"neoorigins:area_of_effect\","
            + radius + "\"block_action\":" + blockAction + "}").getAsJsonObject(), "test");
    }

    /** Integer points within {@code r} of the centre: what a sphere sweep of radius r visits. */
    private static int sphere(int r) {
        int n = 0;
        for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
            if (x * x + y * y + z * z <= r * r) n++;
        }
        return n;
    }

    private static final String PARTICLES = "{\"type\":\"neoorigins:spawn_particles\",\"particle\":\"minecraft:flame\"}";

    @Test
    void anUntypedRadiusSweepsAFewBlocksNotSeventeenThousand() {
        ServerLevel level = mock(ServerLevel.class);
        aoe("", PARTICLES).execute(caster(level));
        assertEquals(sphere(2), calls(level, "sendParticles"));
    }

    @Test
    void aTypedRadiusIsStillHonouredUpToTheCap() {
        ServerLevel level = mock(ServerLevel.class);
        aoe("\"radius\":3,", PARTICLES).execute(caster(level));
        assertEquals(sphere(3), calls(level, "sendParticles"));
    }

    @Test
    void explodeGoesOffAFewTimesPerTickHoweverManyBlocksAsk() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer source = caster(level);
        EntityAction boom = aoe("\"radius\":16,", "{\"type\":\"neoorigins:explode\"}");
        when(level.getGameTime()).thenReturn(100L);
        boom.execute(source);
        assertEquals(BuiltinActions.EXPLOSIONS_PER_TICK, calls(level, "explode"));
        when(level.getGameTime()).thenReturn(101L);
        boom.execute(source);
        assertEquals(2L * BuiltinActions.EXPLOSIONS_PER_TICK, calls(level, "explode"));
    }

    @Test
    void aSingleExplodePerTickIsUntouched() {
        ServerLevel level = mock(ServerLevel.class);
        ServerPlayer source = caster(level);
        EntityAction boom = ActionParser.parse(JsonParser.parseString(
            "{\"type\":\"neoorigins:explode\"}").getAsJsonObject(), "test");
        for (long t = 0; t < 10; t++) {
            when(level.getGameTime()).thenReturn(t);
            boom.execute(source);
        }
        assertEquals(10, calls(level, "explode"));
    }
}
