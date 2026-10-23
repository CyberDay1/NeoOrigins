package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.compat.condition.TargetConditionParser;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;
import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SeerOrigin's All-Seeing Eye: an imported entity_glow whose bientity_condition
 * pairs an actor {@code nbt} check with a {@code distance} check. {@code distance}
 * had no bientity form, so the whole condition failed closed and the power
 * outlined nothing.
 *
 * <p>26.x note: entity serialization goes through {@code ValueOutput}, and there is
 * no public way to seed a {@link TagValueOutput} with a prepared tag. The factory
 * is stubbed, as in {@code TargetNbtConditionTest}: each actor's
 * {@code saveWithoutId} selects the fixture tag the stubbed output then builds.
 */
class EntityGlowBiEntityTest {

    /** The glow sub-power of SeerOrigin's {@code seer:sight}, verbatim. */
    private static final String SEER_GLOW = """
        { "type": "origins:entity_glow",
          "entity_condition": { "type": "origins:and", "conditions": [ { "type": "origins:living" } ] },
          "bientity_condition": { "type": "origins:and", "conditions": [
            { "type": "origins:actor_condition",
              "condition": { "type": "origins:nbt", "nbt": "{Tags:[\\"seer_astral\\"]}" } },
            { "type": "origins:distance", "comparison": "<=", "compare_to": 25 } ] },
          "use_teams": false, "red": 0.75, "green": 0.0, "blue": 1.0 }
        """;

    private MockedStatic<TagValueOutput> tagOutputFactory;
    /** SNBT of the actor serialized last; what the stubbed output's buildResult() returns. */
    private static String serialized = "{}";

    @BeforeEach
    void stubEntitySerialization() {
        TagValueOutput output = mock(TagValueOutput.class);
        when(output.buildResult()).thenAnswer(inv -> nbt(serialized));
        tagOutputFactory = mockStatic(TagValueOutput.class);
        tagOutputFactory.when(() -> TagValueOutput.createWithContext(any(), any())).thenReturn(output);
    }

    @AfterEach
    void releaseStub() {
        tagOutputFactory.close();
    }

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static CompoundTag nbt(String snbt) {
        try {
            return TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            throw new AssertionError(snbt, e);
        }
    }

    private static final class Clock { long time; }

    private static ServerPlayer actor(String snbt, Clock clock) {
        ServerPlayer p = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class);
        when(level.getGameTime()).thenAnswer(inv -> clock.time);
        when(p.level()).thenReturn(level);
        when(p.getUUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));
        when(p.position()).thenReturn(Vec3.ZERO);
        doAnswer(inv -> { serialized = snbt; return null; }).when(p).saveWithoutId(any(ValueOutput.class));
        return p;
    }

    private static LivingEntity targetAt(double x) {
        LivingEntity t = mock(LivingEntity.class);
        when(t.position()).thenReturn(new Vec3(x, 0, 0));
        when(t.isAlive()).thenReturn(true);
        return t;
    }

    private static EntityGlowPower.Config seerConfig() {
        var resolved = com.cyberday1.neoorigins.data.PowerDataManager.resolvePowerType(
            Identifier.parse("seer:sight_glow"), obj(SEER_GLOW));
        assertNotNull(resolved, "the Seer glow was dropped");
        assertEquals("neoorigins:entity_glow", resolved.json().get("type").getAsString());
        return new EntityGlowPower().codec().parse(JsonOps.INSTANCE, resolved.json())
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    @Test
    void seerGlowOutlinesNearbyEntitiesWhileAstral() {
        var config = seerConfig();
        assertTrue(config.bientityCondition().isPresent());
        assertFalse(config.useTeams());
        assertEquals(0xBF00FF, config.rgb());
        var bi = config.bientityCondition().get();
        var astral = actor("{Tags:[\"seer_astral\",\"seer_projecting\"]}", new Clock());
        assertTrue(bi.test(astral, targetAt(10)), "astral holder, 10 blocks: glows");
        assertTrue(bi.test(astral, targetAt(25)), "the bound is inclusive");
        assertFalse(bi.test(astral, targetAt(26)), "beyond 25 blocks: no glow");
        assertTrue(config.selection().entityCondition().get().test(targetAt(10), astral));
    }

    @Test
    void seerGlowIsOffOutsideAstralForm() {
        var bi = seerConfig().bientityCondition().get();
        assertFalse(bi.test(actor("{Tags:[\"seer_body_owner\"]}", new Clock()), targetAt(5)));
        assertFalse(bi.test(actor("{}", new Clock()), targetAt(5)));
    }

    /** Apoli squares both sides; for a non-negative bound that is the plain distance. */
    @Test
    void distanceComparesFeetToFeet() {
        var actor = actor("{}", new Clock());
        BiPredicate<ServerPlayer, Entity> far = TargetConditionParser.parseBiEntity(
            obj("{ \"type\": \"apoli:distance\", \"comparison\": \">\", \"compare_to\": 8 }"), "test");
        assertNotNull(far);
        assertTrue(far.test(actor, targetAt(9)));
        assertFalse(far.test(actor, targetAt(8)));
        BiPredicate<ServerPlayer, Entity> exact = TargetConditionParser.parseBiEntity(
            obj("{ \"type\": \"distance\", \"comparison\": \"==\", \"compare_to\": 3 }"), "test");
        assertTrue(exact.test(actor, targetAt(3)));
        assertFalse(exact.test(actor, targetAt(4)));
    }

    /** entity_glow reads the holder's NBT once per tick, not once per candidate. */
    @Test
    void actorConditionRunsOncePerTickInAScan() {
        var clock = new Clock();
        var astral = actor("{Tags:[\"seer_astral\"]}", clock);
        var bi = seerConfig().bientityCondition().get();
        for (int i = 0; i < 5; i++) bi.test(astral, targetAt(i));
        verify(astral, times(1)).saveWithoutId(any(ValueOutput.class));
        clock.time++;
        bi.test(astral, targetAt(1));
        verify(astral, times(2)).saveWithoutId(any(ValueOutput.class));
    }

    /** Other bientity callers keep evaluating the actor on every test. */
    @Test
    void plainParseDoesNotCacheTheActor() {
        var astral = actor("{Tags:[\"seer_astral\"]}", new Clock());
        var bi = TargetConditionParser.parseBiEntity(obj("""
            { "type": "apoli:actor_condition",
              "condition": { "type": "apoli:nbt", "nbt": "{Tags:[\\"seer_astral\\"]}" } }
            """), "test");
        assertNotNull(bi);
        for (int i = 0; i < 3; i++) assertTrue(bi.test(astral, targetAt(i)));
        verify(astral, times(3)).saveWithoutId(any(ValueOutput.class));
    }

    /** A verb that still has no pair form keeps the power inert, not glowing at everything. */
    @Test
    void anUnsupportedPairVerbStillOutlinesNothing() {
        var config = new EntityGlowPower().codec().parse(JsonOps.INSTANCE, obj("""
            { "type": "neoorigins:entity_glow",
              "bientity_condition": { "type": "apoli:riding" } }
            """)).getOrThrow(msg -> new AssertionError(msg));
        var bi = config.bientityCondition().get();
        assertFalse(bi.test(actor("{}", new Clock()), targetAt(1)));
    }
}
