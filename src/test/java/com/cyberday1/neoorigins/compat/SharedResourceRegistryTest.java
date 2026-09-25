package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.network.payload.SyncResourceValuesPayload;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * Resource meta, backing and cooldown durations are keyed by power id and shared
 * by every player. One player's revoke must not take them from another holder,
 * and a mana-backed bar must reach only the players who hold its power. A reload
 * clears them and must hand them back to players who are online.
 */
class SharedResourceRegistryTest {

    private static final String MANA = "test:mana";
    private static final String ENERGY = "test:energy";

    private final Map<ServerPlayer, List<PowerHolder<?>>> held = new IdentityHashMap<>();
    private final Map<ServerPlayer, Integer> pools = new IdentityHashMap<>();
    private final List<Object[]> sent = new ArrayList<>();
    private MockedStatic<ActiveOriginService> origins;
    private MockedStatic<ResourceBackingRouter> router;
    private MockedStatic<PacketDistributor> packets;
    private MockedStatic<ContentTogglesConfig> toggles;

    @BeforeEach
    void rig() {
        origins = mockStatic(ActiveOriginService.class);
        origins.when(() -> ActiveOriginService.allPowers(any(ServerPlayer.class)))
            .thenAnswer(inv -> held.getOrDefault(inv.getArgument(0), List.of()));
        router = mockStatic(ResourceBackingRouter.class, CALLS_REAL_METHODS);
        router.when(() -> ResourceBackingRouter.read(any(ServerPlayer.class), eq(MANA), anyInt()))
            .thenAnswer(inv -> pools.get(inv.<ServerPlayer>getArgument(0)));
        router.when(() -> ResourceBackingRouter.maxValue(any(ServerPlayer.class), eq(MANA), anyInt())).thenReturn(100);
        packets = mockStatic(PacketDistributor.class);
        packets.when(() -> PacketDistributor.sendToPlayer(any(ServerPlayer.class), any(CustomPacketPayload.class), any(CustomPacketPayload[].class)))
            .thenAnswer(inv -> sent.add(new Object[] { inv.getArgument(0), inv.getArgument(1) }));
        toggles = mockStatic(ContentTogglesConfig.class);
    }

    @AfterEach
    void tearDown() {
        toggles.close();
        packets.close();
        router.close();
        origins.close();
        CompatAttachments.clearResourceMeta();
        CompatAttachments.clearCooldownDurations();
    }

    @Test
    void anotherHoldersRevokeLeavesMyManaBar() {
        var power = nativeManaHolder();
        ServerPlayer a = holder(power, 37);
        ServerPlayer b = holder(power, 81);
        power.onGranted(a);
        power.onGranted(b);

        held.put(b, List.of());
        power.onRevoked(b);

        assertEquals(37, ResourcePower.getValue(a, MANA), "B's revoke took A's mana-backed resource away");
        var bars = CompatAttachments.fullSyncEntries(a);
        assertTrue(bars.containsKey(MANA), "B's revoke took A's HUD bar away");
        assertEquals(37, bars.get(MANA).value(), "A's HUD bar shows the wrong pool");
        assertFalse(CompatAttachments.fullSyncEntries(b).containsKey(MANA), "B still gets a bar after its revoke");
    }

    @Test
    void anotherHoldersRevokeLeavesMyApoliResource() {
        var config = compile("origins:resource", """
            {"type": "origins:resource", "min": 0, "max": 100, "backing": "irons_spellbooks:mana"}""");
        var power = new PowerHolder<>(Identifier.parse(MANA), CompatPower.INSTANCE, config,
            Component.empty(), Component.empty());
        ServerPlayer a = holder(power, 37);
        ServerPlayer b = holder(power, 81);
        config.onGranted().accept(a);
        config.onGranted().accept(b);

        held.put(b, List.of());
        config.onRevoked().accept(b);

        assertNotNull(CompatAttachments.getResourceMeta(MANA), "B's revoke dropped the bounds A's bar uses");
        assertEquals(37, ResourcePower.getValue(a, MANA), "B's revoke took A's mana-backed Apoli resource away");
    }

    @Test
    void anotherHoldersRevokeLeavesMyCooldownDuration() {
        String key = "test:blink";
        var config = compile("origins:cooldown", """
            {"type": "origins:cooldown", "cooldown": 60}""", key);
        ServerPlayer a = PlayerLifecycle.realPlayer();
        ServerPlayer b = PlayerLifecycle.realPlayer();
        config.onGranted().accept(a);
        config.onGranted().accept(b);

        config.onRevoked().accept(b);

        assertEquals(60, CompatAttachments.cooldownDuration(key), "B's revoke dropped the cooldown A's trigger_cooldown arms");
    }

    @Test
    void manaBarReachesOnlyItsHolders() {
        var power = nativeManaHolder();
        ServerPlayer a = holder(power, 37);
        ServerPlayer c = PlayerLifecycle.realPlayer();
        pools.put(c, 55);
        power.onGranted(a);

        assertTrue(CompatAttachments.fullSyncEntries(a).containsKey(MANA), "the holder must get its bar");
        assertEquals(37, CompatAttachments.fullSyncEntries(a).get(MANA).value(), "the holder's bar must show its own pool");
        assertFalse(CompatAttachments.fullSyncEntries(c).containsKey(MANA), "a non-holder got a mana bar in the full sync");

        sent.clear();
        CompatAttachments.syncResourceValuesToClient(c);
        var values = (SyncResourceValuesPayload) sent.get(0)[1];
        assertFalse(values.values().containsKey(MANA), "a non-holder got a mana value in the value sync");
    }

    @Test
    void reloadLeavesOnlineHoldersTheirNativeBars() {
        var mana = nativeManaHolder();
        var energy = nativeEnergyHolder();
        ServerPlayer a = holder(mana, 37);
        held.put(a, List.of(mana, energy));
        mana.onGranted(a);
        energy.onGranted(a);

        reload();
        try (var global = mockStatic(com.cyberday1.neoorigins.service.GlobalPowerService.class);
             var net = mockStatic(com.cyberday1.neoorigins.network.NeoOriginsNetwork.class)) {
            var event = org.mockito.Mockito.mock(net.neoforged.neoforge.event.OnDatapackSyncEvent.class);
            org.mockito.Mockito.when(event.getRelevantPlayers()).thenAnswer(inv -> java.util.stream.Stream.of(a));
            com.cyberday1.neoorigins.event.PlayerLifecycleEvents.onDatapackSync(event);
        }

        assertTrue(CompatAttachments.isManaBacked(MANA), "a reload left the online holder's bar unbacked");
        var bars = CompatAttachments.fullSyncEntries(a);
        assertTrue(bars.containsKey(ENERGY), "a reload took the online holder's energy bar away");
        assertEquals(37, bars.get(MANA).value(), "a reload took the online holder's mana bar away");
    }

    @Test
    void reloadKeepsAnApoliResourceBacked() {
        compile("origins:resource", """
            {"type": "origins:resource", "min": 0, "max": 100, "backing": "irons_spellbooks:mana"}""");

        reload();
        compile("origins:resource", """
            {"type": "origins:resource", "min": 0, "max": 100, "backing": "irons_spellbooks:mana"}""");

        assertTrue(CompatAttachments.isManaBacked(MANA), "a reload left the Apoli resource unbacked until someone is re-granted it");
    }

    /** What the Route B loader's apply clears on every reload. */
    private static void reload() {
        CompatAttachments.clearResourceMeta();
        CompatAttachments.clearCooldownDurations();
    }

    private static PowerHolder<ResourcePower.Config> nativeEnergyHolder() {
        EntityCondition always = p -> true;
        EntityAction none = p -> {};
        var config = new ResourcePower.Config(ENERGY, 0, 80, 80, 0, 20, always, none, none,
            "Energy", 0xFFAADD33, false, null, 0, false, "neoorigins:resource", "");
        return new PowerHolder<>(Identifier.parse(ENERGY), new ResourcePower(), config,
            Component.empty(), Component.empty());
    }

    private ServerPlayer holder(PowerHolder<?> power, int mana) {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        held.put(sp, List.of(power));
        pools.put(sp, mana);
        return sp;
    }

    private static CompatPower.Config compile(String type, String body) {
        return compile(type, body, MANA);
    }

    private static CompatPower.Config compile(String type, String body, String id) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        var config = OriginsCompatPowerLoader.compileForTest(Identifier.parse(id), type, json);
        assertNotNull(config, type + " did not compile");
        return config;
    }

    private static PowerHolder<ResourcePower.Config> nativeManaHolder() {
        EntityCondition always = p -> true;
        EntityAction none = p -> {};
        var config = new ResourcePower.Config(MANA, 0, 100, 0, 0, 20, always, none, none,
            "Mana", 0xFF55AAFF, false, null, 0, false, "neoorigins:resource", CompatAttachments.BACKING_IRONS_MANA);
        return new PowerHolder<>(Identifier.parse(MANA), new ResourcePower(), config,
            Component.empty(), Component.empty());
    }
}
