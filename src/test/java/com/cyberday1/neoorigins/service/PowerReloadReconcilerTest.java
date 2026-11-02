package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager.Definitions;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A reload re-applies a held power only when its definition changed, through old then new. */
class PowerReloadReconcilerTest {

    private static final Identifier P = Identifier.fromNamespaceAndPath("testmod", "p");
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("testmod", "source");

    record Cfg(String tag) implements PowerConfiguration {}

    /** Logs every grant and revoke with the definition it ran on. */
    static final class Rec extends PowerType<Cfg> {
        final List<String> log;
        Rec(List<String> log) { this.log = log; }
        @Override public Codec<Cfg> codec() { return com.mojang.serialization.MapCodec.unit(() -> new Cfg("")).codec(); }
        @Override public void onGranted(ServerPlayer player, Cfg config) { log.add("grant " + config.tag()); }
        @Override public void onRevoked(ServerPlayer player, Cfg config) { log.add("revoke " + config.tag()); }
    }

    private final List<String> log = new ArrayList<>();
    private final Rec type = new Rec(log);

    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    @BeforeAll
    static void loadWorld() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
    }

    @AfterAll
    static void unloadConfigs() {
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    private PowerHolder<Cfg> holder(Identifier id, String tag) {
        return new PowerHolder<>(id, type, new Cfg(tag), Component.literal(tag), Component.empty());
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    /** A generation whose only powers are datapack powers with the given JSON bodies. */
    private static Definitions datapack(Map<Identifier, PowerHolder<?>> holders, Map<Identifier, JsonObject> raw) {
        return new Definitions(holders, raw, Map.of(), Map.of(), Map.of());
    }

    private static int nextId = 7000;

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        // 26.x: the morph sync reads the entity id, which a test player has no other way to get.
        sp.setId(++nextId);
        return sp;
    }

    @Test
    void aChangedDefinitionIsRevokedOldThenGrantedNewOnce() {
        Definitions before = datapack(Map.of(P, holder(P, "v1")), Map.of(P, json("{\"amount\": 4}")));
        Definitions after = datapack(Map.of(P, holder(P, "v2")), Map.of(P, json("{\"amount\": 8}")));
        int n = PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P));
        assertEquals(1, n);
        assertEquals(List.of("revoke v1", "grant v2"), log);
    }

    @Test
    void anUnchangedDefinitionIsLeftAloneThoughItsHolderWasRebuilt() {
        Definitions before = datapack(Map.of(P, holder(P, "v1")), Map.of(P, json("{\"amount\": 4}")));
        Definitions after = datapack(Map.of(P, holder(P, "v1-rebuilt")), Map.of(P, json("{\"amount\": 4}")));
        assertEquals(0, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P)));
        assertEquals(List.of(), log, "a plain reload must not re-grant (that would reset resource values and toggles)");
    }

    @Test
    void aRemovedPowerIsRevokedWithTheOldDefinition() {
        Definitions before = datapack(Map.of(P, holder(P, "v1")), Map.of(P, json("{}")));
        Definitions after = datapack(Map.of(), Map.of());
        assertEquals(1, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P)));
        assertEquals(List.of("revoke v1"), log);
    }

    @Test
    void aPowerThatReturnsIsGranted() {
        Definitions before = datapack(Map.of(), Map.of());
        Definitions after = datapack(Map.of(P, holder(P, "back")), Map.of(P, json("{}")));
        assertEquals(1, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P)));
        assertEquals(List.of("grant back"), log);
    }

    @Test
    void aDatapackPowerOverridingACodePowerSwapsIt() {
        Definitions before = new Definitions(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(P, holder(P, "code")));
        Definitions after = new Definitions(Map.of(P, holder(P, "datapack")), Map.of(P, json("{}")),
            Map.of(), Map.of(), Map.of(P, before.code().get(P)));
        assertEquals(1, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P)));
        assertEquals(List.of("revoke code", "grant datapack"), log);
    }

    @Test
    void aSameCodePowerAcrossReloadsIsUnchanged() {
        Map<Identifier, PowerHolder<?>> code = Map.of(P, holder(P, "code"));
        Definitions before = new Definitions(Map.of(), Map.of(), Map.of(), Map.of(), code);
        Definitions after = new Definitions(Map.of(), Map.of(), Map.of(), Map.of(), code);
        assertEquals(0, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of(P)));
        assertEquals(List.of(), log);
    }

    @Test
    void anIdThePlayerNoLongerHoldsIsNotADefinitionChange() {
        Definitions before = datapack(Map.of(P, holder(P, "v1")), Map.of(P, json("{\"a\": 1}")));
        Definitions after = datapack(Map.of(P, holder(P, "v2")), Map.of(P, json("{\"a\": 2}")));
        assertEquals(0, PowerReloadReconciler.reconcile(player(), before, after, Set.of(P), Set.of()));
        assertEquals(List.of(), log);
    }

    @Test
    void anOriginPowerAndALedgerGrantBothReconcile() {
        ServerPlayer sp = player();
        Identifier layer = Identifier.fromNamespaceAndPath("neoorigins", "origin");
        Identifier originId = Identifier.fromNamespaceAndPath("neoorigins", "abyssal");
        assertNotNull(OriginDataManager.INSTANCE.getOrigin(originId));
        Identifier fromOrigin = OriginDataManager.INSTANCE.getOrigin(originId).powers().get(0);
        Identifier fromLedger = Identifier.fromNamespaceAndPath("neoorigins", "abyssal_apex_hp");
        sp.getData(OriginAttachments.originData()).setOrigin(layer, originId);
        NeoOriginsAPI.grantPower(sp, fromLedger, SOURCE);

        Set<Identifier> held = ActiveOriginService.activePowerIds(sp);
        assertTrue(held.contains(fromOrigin) && held.contains(fromLedger));

        Map<Identifier, PowerHolder<?>> oldHolders = new HashMap<>();
        Map<Identifier, PowerHolder<?>> newHolders = new HashMap<>();
        Map<Identifier, JsonObject> oldRaw = new HashMap<>();
        Map<Identifier, JsonObject> newRaw = new HashMap<>();
        for (Identifier id : List.of(fromOrigin, fromLedger)) {
            oldHolders.put(id, holder(id, "old " + id.getPath()));
            newHolders.put(id, holder(id, "new " + id.getPath()));
            oldRaw.put(id, json("{\"v\": 1}"));
            newRaw.put(id, json("{\"v\": 2}"));
        }
        int n = PowerReloadReconciler.reconcile(sp, datapack(oldHolders, oldRaw), datapack(newHolders, newRaw), held, held);
        assertEquals(2, n);
        for (Identifier id : List.of(fromOrigin, fromLedger)) {
            assertEquals(1, log.stream().filter(("revoke old " + id.getPath())::equals).count());
            assertEquals(1, log.stream().filter(("grant new " + id.getPath())::equals).count());
            assertTrue(log.indexOf("revoke old " + id.getPath()) < log.indexOf("grant new " + id.getPath()));
        }
    }
}
