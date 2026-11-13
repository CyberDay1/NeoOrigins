package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.compat.action.ActionParser;
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

class CustomEntityActionTest {
    static final Identifier ID = Identifier.parse("actiontest:heal");
    static final Codec<Integer> CONFIG = Codec.INT.fieldOf("amount").codec();
    final AtomicInteger calls = new AtomicInteger();
    @TempDir Path pack;

    static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    @BeforeAll static void load() { RealDatapack.load(); }
    @BeforeEach void register() {
        NeoOriginsAPI.registerEntityAction(ID, CONFIG, (p, n) -> { p.fallDistance = n; calls.incrementAndGet(); });
    }
    @AfterEach void cleanup() { CompatRegistries.unregisterAddonAction(ID); RealDatapack.restoreShipped(); }

    @Test void exactNamespaceWinsAndCodecRoundTrips() {
        var source = json("{\"type\":\"actiontest:heal\",\"amount\":7}");
        var action = NeoOriginsAPI.actionCodec().parse(JsonOps.INSTANCE, source).getOrThrow();
        var player = PlayerLifecycle.realPlayer();
        action.accept(player);
        assertEquals(7, player.fallDistance);
        assertEquals(1, calls.get());
        assertEquals(source, NeoOriginsAPI.actionCodec().encodeStart(JsonOps.INSTANCE, action).getOrThrow());
        assertTrue(ActionParser.knownTypes().contains(ID.toString()));
    }

    @Test void nestedCompositionAndLegacyFallbackStillWork() {
        var player = PlayerLifecycle.realPlayer();
        NeoOriginsAPI.parseAction(json("""
            {"type":"origins:and","actions":[
              {"type":"neoorigins:if_else","condition":{"type":"neoorigins:constant","value":true},
               "if_action":{"type":"actiontest:heal","amount":8}},
              {"type":"oldaddon:set_fall_distance","fall_distance":3}]}
            """), "actiontest:nested").accept(player);
        assertEquals(1, calls.get());
        assertEquals(3, player.fallDistance);
    }

    @Test void invalidConfigDoesNotFallBackToTheBuiltin() {
        var player = PlayerLifecycle.realPlayer();
        player.setHealth(5);
        NeoOriginsAPI.parseAction(json("{\"type\":\"actiontest:heal\",\"amount\":\"bad\"}"), "bad").accept(player);
        assertEquals(5, player.getHealth());
        assertEquals(0, calls.get());
        assertDoesNotThrow(() -> NeoOriginsAPI.parseAction(json("{\"type\":{}}"), "bad").accept(player));
        assertThrows(IllegalStateException.class, () -> NeoOriginsAPI.registerEntityAction(ID, CONFIG, (p, n) -> {}));
        assertThrows(IllegalArgumentException.class, () -> NeoOriginsAPI.registerEntityAction(
            Identifier.parse("neoorigins:heal"), CONFIG, (p, n) -> {}));
    }

    @Test void delayedActionsUseTheRegisteredHandler() {
        var player = PlayerLifecycle.realPlayer();
        NeoOriginsAPI.parseAction(json("""
            {"type":"neoorigins:delay","ticks":0,
             "action":{"type":"actiontest:heal","amount":4}}
            """), "delayed").accept(player);
        assertEquals(0, calls.get());
        com.cyberday1.neoorigins.compat.CompatTickScheduler.tick(player);
        com.cyberday1.neoorigins.compat.CompatTickScheduler.tick(player);
        assertEquals(1, calls.get());
        assertEquals(4, player.fallDistance);
        com.cyberday1.neoorigins.compat.CompatTickScheduler.clearPlayer(player.getUUID());
    }

    @Test void aDatapackAbilityExecutesTheAddonActionAfterReload() throws Exception {
        Path powers = Files.createDirectories(pack.resolve("data/actiontest/origins/powers"));
        Files.writeString(powers.resolve("ability.json"), """
            {"type":"neoorigins:active_ability","cooldown_ticks":0,
             "entity_action":{"type":"actiontest:heal","amount":11}}
            """);
        RealDatapack.loadWith(pack);
        var player = PlayerLifecycle.realPlayer();
        var id = Identifier.parse("actiontest:ability");
        PowerDataManager.INSTANCE.getPower(id).onActivated(player);
        assertEquals(1, calls.get());
        assertEquals(11, player.fallDistance);
        RealDatapack.loadWith(pack);
        PowerDataManager.INSTANCE.getPower(id).onActivated(player);
        assertEquals(2, calls.get());
        assertEquals("actiontest:heal", PowerDataManager.INSTANCE.getRawPowerJson(id)
            .getAsJsonObject("entity_action").get("type").getAsString());
    }

    @Test void creatorValidationAndSerializationKeepTheCustomAction() {
        var draft = new com.cyberday1.neoorigins.screen.creator.model.OriginDraft();
        var power = new com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft(
            Identifier.parse("actiontest:ability"), "neoorigins:active_ability");
        power.rawJson = "{\"entity_action\":{\"type\":\"actiontest:heal\",\"amount\":8}}";
        draft.powers.add(power);
        assertTrue(com.cyberday1.neoorigins.service.DraftSanity.powerProblems(
            net.minecraft.core.RegistryAccess.EMPTY, draft).isEmpty());
        var saved = com.cyberday1.neoorigins.service.CustomPackSerializer.powerJson(power);
        assertEquals(json(power.rawJson).get("entity_action"), saved.get("entity_action"));
    }
}
