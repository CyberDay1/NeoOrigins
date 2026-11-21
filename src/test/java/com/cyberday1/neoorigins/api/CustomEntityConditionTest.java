package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.compat.registry.CompatRegistries;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CustomEntityConditionTest {
    // The path matches the built-in `neoorigins:sneaking` so the exact-id lookup is exercised.
    static final Identifier ID = Identifier.parse("conditiontest:sneaking");
    static final Codec<Float> CONFIG = Codec.FLOAT.fieldOf("min_fall").codec();
    final AtomicInteger calls = new AtomicInteger();
    @TempDir Path pack;

    static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    @BeforeAll static void load() { RealDatapack.load(); }
    @BeforeEach void register() {
        NeoOriginsAPI.registerEntityCondition(ID, CONFIG, (p, min) -> { calls.incrementAndGet(); return p.fallDistance >= min; });
    }
    @AfterEach void cleanup() { CompatRegistries.unregisterAddonCondition(ID); RealDatapack.restoreShipped(); }

    @Test void exactNamespaceWinsAndCodecRoundTrips() {
        var source = json("{\"type\":\"conditiontest:sneaking\",\"min_fall\":2}");
        var condition = NeoOriginsAPI.conditionCodec().parse(JsonOps.INSTANCE, source).getOrThrow();
        var player = PlayerLifecycle.realPlayer();
        player.fallDistance = 3;
        assertTrue(condition.test(player));
        player.fallDistance = 1;
        assertFalse(condition.test(player));
        assertEquals(2, calls.get());
        assertEquals(source, NeoOriginsAPI.conditionCodec().encodeStart(JsonOps.INSTANCE, condition).getOrThrow());
        assertTrue(ConditionParser.knownTypes().contains(ID.toString()));
        assertTrue(ConditionParser.knownTypes().containsAll(ConditionParser.KNOWN_TYPES));
    }

    @Test void invertedAndNestedCompositionWork() {
        var player = PlayerLifecycle.realPlayer();
        player.fallDistance = 5;
        assertFalse(NeoOriginsAPI.parseCondition(json(
            "{\"type\":\"conditiontest:sneaking\",\"min_fall\":2,\"inverted\":true}"), "inv").test(player));
        assertTrue(NeoOriginsAPI.parseCondition(json("""
            {"type":"origins:and","conditions":[
              {"type":"neoorigins:constant","value":true},
              {"type":"conditiontest:sneaking","min_fall":4}]}
            """), "nested").test(player));
        // Unregistered namespaces keep the legacy rewrite to the built-in leaf.
        assertFalse(NeoOriginsAPI.parseCondition(json("{\"type\":\"oldaddon:sneaking\"}"), "legacy").test(player));
        assertEquals(2, calls.get());
    }

    @Test void invalidConfigFailsClosedWithoutFallingBackAndRegistrationIsGuarded() {
        var player = PlayerLifecycle.realPlayer();
        var bad = NeoOriginsAPI.parseCondition(json("{\"type\":\"conditiontest:sneaking\",\"min_fall\":\"x\"}"), "bad");
        assertFalse(bad.test(player));
        assertTrue(NeoOriginsAPI.parseCondition(json(
            "{\"type\":\"conditiontest:sneaking\",\"min_fall\":\"x\",\"inverted\":true}"), "bad").test(player));
        assertEquals(0, calls.get());
        assertThrows(IllegalStateException.class, () -> NeoOriginsAPI.registerEntityCondition(ID, CONFIG, (p, n) -> true));
        assertThrows(IllegalArgumentException.class, () -> NeoOriginsAPI.registerEntityCondition(
            Identifier.parse("origins:sneaking"), CONFIG, (p, n) -> true));
    }

    @Test void aDatapackPowerUsesTheAddonConditionAfterReload() throws Exception {
        Path powers = Files.createDirectories(pack.resolve("data/conditiontest/origins/powers"));
        Files.writeString(powers.resolve("gated.json"), """
            {"type":"neoorigins:active_ability","cooldown_ticks":0,
             "power_condition":{"type":"conditiontest:sneaking","min_fall":6},"power_condition_mode":"ALLOW",
             "entity_action":{"type":"neoorigins:set_fall_distance","fall_distance":0}}
            """);
        RealDatapack.loadWith(pack);
        var player = PlayerLifecycle.realPlayer();
        var id = Identifier.parse("conditiontest:gated");
        player.fallDistance = 2;
        assertFalse(PowerDataManager.INSTANCE.getPower(id).isConditionSatisfied(player));
        RealDatapack.loadWith(pack);
        player.fallDistance = 7;
        assertTrue(PowerDataManager.INSTANCE.getPower(id).isConditionSatisfied(player));
    }
}
