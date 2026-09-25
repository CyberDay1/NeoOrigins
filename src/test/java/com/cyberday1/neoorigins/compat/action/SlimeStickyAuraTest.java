package com.cyberday1.neoorigins.compat.action;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Slime's tier-2 Sticky Aura, run from the shipped file. It used to carry its
 * effect as loose fields on the area_of_effect, which that action does not read,
 * so the aura compiled to nothing.
 */
class SlimeStickyAuraTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject shippedAura() {
        String path = "/data/neoorigins/origins/powers/slime_ascended_sticky.json";
        try (InputStream in = SlimeStickyAuraTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "shipped power file missing from the classpath: " + path);
            JsonObject power = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                .getAsJsonObject();
            return power.getAsJsonObject("entity_action");
        } catch (java.io.IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    @Test
    void slowsWhatIsNearbyButNotTheSlime() {
        ServerPlayer neighbour = mock(ServerPlayer.class);
        when(neighbour.position()).thenReturn(new Vec3(2.0, 0.0, 0.0));

        ServerPlayer slime = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(slime.level()).thenReturn(level);
        when(slime.position()).thenReturn(Vec3.ZERO);
        when(slime.getBoundingBox()).thenReturn(new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3));
        when(slime.getUUID()).thenReturn(UUID.randomUUID());
        when(level.getEntitiesOfClass(eq(LivingEntity.class), any(AABB.class)))
            .thenReturn(List.<LivingEntity>of(slime, neighbour));

        ActionParser.parse(shippedAura(), "neoorigins:slime_ascended_sticky").execute(slime);

        ArgumentCaptor<MobEffectInstance> captor = ArgumentCaptor.forClass(MobEffectInstance.class);
        verify(neighbour).addEffect(captor.capture());
        MobEffectInstance applied = captor.getValue();
        assertEquals("minecraft:slowness",
            BuiltInRegistries.MOB_EFFECT.getKey(applied.getEffect().value()).toString());
        assertEquals(0, applied.getAmplifier());
        assertEquals(40, applied.getDuration());
        verify(slime, never()).addEffect(any(MobEffectInstance.class));
    }
}
