package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.ActionContextHolder;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The public action surface: parseAction and actionCodec. */
class ApiActionsTest {

    private static final String FALL_5 = "{\"type\": \"neoorigins:set_fall_distance\", \"fall_distance\": 5}";
    private static final String FALL_9 = "{\"type\": \"neoorigins:set_fall_distance\", \"fall_distance\": 9}";

    @BeforeAll
    static void load() {
        RealDatapack.load();
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    record AddonConfig(Consumer<ServerPlayer> action, int amount) {
        static final Codec<AddonConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
            NeoOriginsAPI.actionCodec().optionalFieldOf("action", p -> {}).forGetter(AddonConfig::action),
            Codec.INT.fieldOf("amount").forGetter(AddonConfig::amount)
        ).apply(i, AddonConfig::new));
    }

    @Test
    void parsesAnActionAsAPlayerConsumer() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        NeoOriginsAPI.parseAction(json(FALL_5), "test").accept(sp);
        assertEquals(5f, sp.fallDistance);
    }

    @Test
    void anUnknownActionDoesNothing() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.fallDistance = 3f;
        assertDoesNotThrow(() -> NeoOriginsAPI.parseAction(json("{\"type\": \"testmod:nope\"}"), "test").accept(sp));
        assertDoesNotThrow(() -> NeoOriginsAPI.parseAction(null, "test").accept(sp));
        assertEquals(3f, sp.fallDistance);
    }

    @Test
    void anAddonCodecDecodesAnActionFieldOrAnArrayRunInOrder() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        AddonConfig absent = AddonConfig.CODEC.parse(JsonOps.INSTANCE, json("{\"amount\": 2}")).getOrThrow();
        sp.fallDistance = 3f;
        absent.action().accept(sp);
        assertEquals(3f, sp.fallDistance);

        AddonConfig single = AddonConfig.CODEC.parse(JsonOps.INSTANCE,
            json("{\"amount\": 2, \"action\": " + FALL_5 + "}")).getOrThrow();
        single.action().accept(sp);
        assertEquals(5f, sp.fallDistance);

        AddonConfig both = AddonConfig.CODEC.parse(JsonOps.INSTANCE,
            json("{\"amount\": 2, \"action\": [" + FALL_5 + ", " + FALL_9 + "]}")).getOrThrow();
        both.action().accept(sp);
        assertEquals(9f, sp.fallDistance, "array entries run in order, so the last one wins");
    }

    @Test
    void aDecodedActionEncodesBackToItsJson() {
        JsonElement source = JsonParser.parseString("[" + FALL_5 + ", " + FALL_9 + "]");
        Consumer<ServerPlayer> parsed = NeoOriginsAPI.actionCodec().parse(JsonOps.INSTANCE, source).getOrThrow();
        assertEquals(source, NeoOriginsAPI.actionCodec().encodeStart(JsonOps.INSTANCE, parsed).getOrThrow());
        assertTrue(NeoOriginsAPI.actionCodec().encodeStart(JsonOps.INSTANCE, p -> {}).isError());
    }

    @Test
    void aNonObjectIsADecodeError() {
        assertTrue(NeoOriginsAPI.actionCodec().parse(JsonOps.INSTANCE, JsonParser.parseString("\"x\"")).isError());
    }

    @Test
    void anEventActionDoesNothingOutsideAnEventAndActsInsideOne() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        ServerPlayer attacker = PlayerLifecycle.realPlayer();
        Consumer<ServerPlayer> ignite = NeoOriginsAPI.parseAction(
            json("{\"type\": \"neoorigins:ignite_attacker\", \"ticks\": 80}"), "test");

        int unlit = attacker.getRemainingFireTicks();
        assertDoesNotThrow(() -> ignite.accept(sp));
        assertEquals(unlit, attacker.getRemainingFireTicks(), "no event, so no attacker to ignite");

        DamageSource source = Mockito.mock(DamageSource.class);
        Mockito.when(source.getEntity()).thenReturn(attacker);
        Object prev = ActionContextHolder.set(new EventPowerIndex.HitTakenContext(2f, source));
        try {
            ignite.accept(sp);
        } finally {
            ActionContextHolder.restore(prev);
        }
        assertEquals(80, attacker.getRemainingFireTicks(), "inside a hit-taken event it reads the attacker");
    }
}
