package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.power.GrantPowerResult;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runtime power grants through the API: sources, layers, resets and persistence. */
class PowerGrantServiceTest {

    private static final Identifier ORIGIN = id("origin");
    private static final Identifier CLASS = id("class");
    private static final Identifier HUMAN = id("human");
    private static final Identifier ABYSSAL = id("abyssal");
    /** abyssal's own +4 max health. */
    private static final Identifier ABYSSAL_ARMOR = id("abyssal_natural_armor");
    private static final Identifier PLUS_SIX = id("abyssal_apex_hp");
    private static final Identifier OTHER_SIX = id("air_mage_apex_hp");
    private static final Identifier MOD_A = Identifier.fromNamespaceAndPath("mod_a", "reward");
    private static final Identifier MOD_B = Identifier.fromNamespaceAndPath("mod_b", "quest");

    private static Map<Identifier, OriginLayer> priorLayers;
    private static List<OriginLayer> priorSorted;
    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    private MinecraftServer server;

    @BeforeAll
    static void loadWorld() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        priorLayers = LayerDataManager.INSTANCE.getLayers();
        priorSorted = LayerDataManager.INSTANCE.getSortedLayers();
        var shipped = RealDatapack.layers();
        LayerDataManager.INSTANCE.setClientData(shipped.getLayers(), shipped.getSortedLayers());
    }

    @AfterAll
    static void restoreWorld() {
        LayerDataManager.INSTANCE.setClientData(priorLayers, priorSorted);
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @BeforeEach
    void freshServer() {
        server = Mockito.mock(MinecraftServer.class, Mockito.withSettings().defaultAnswer(PowerGrantServiceTest::deepUnlessEntityArg));
        var playerList = Mockito.mock(net.minecraft.server.players.PlayerList.class,
            Mockito.withSettings().defaultAnswer(PowerGrantServiceTest::deepUnlessEntityArg));
        Mockito.when(server.getPlayerList()).thenReturn(playerList);
        ServerLevel overworld = Mockito.mock(ServerLevel.class);
        SavedDataStorage storage = Mockito.mock(SavedDataStorage.class);
        Mockito.when(server.overworld()).thenReturn(overworld);
        Mockito.when(overworld.getDataStorage()).thenReturn(storage);
        Mockito.doReturn(new OriginClaimsData()).when(storage).computeIfAbsent(Mockito.any());
        Mockito.when(server.getPlayerList().getPlayers()).thenReturn(List.of());
    }

    @Test
    void grantResultsAndUnknownPowers() {
        ServerPlayer sp = join();
        assertEquals(GrantPowerResult.UNKNOWN_POWER, NeoOriginsAPI.grantPower(sp, id("no_such_power"), MOD_A));
        assertTrue(NeoOriginsAPI.grantedPowers(sp, MOD_A).isEmpty(), "an unknown power must not be stored");

        assertEquals(GrantPowerResult.GRANTED, NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A));
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);
        assertEquals(GrantPowerResult.ALREADY_GRANTED, NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A));
        assertEquals(GrantPowerResult.UPDATED, NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, ORIGIN));
        assertEquals(26.0, sp.getMaxHealth(), 1e-6, "re-layering must not stack the modifier");
        assertTrue(NeoOriginsAPI.hasPower(sp, PLUS_SIX));
    }

    @Test
    void aPowerTwoSourcesGrantStaysUntilBothRevoke() {
        ServerPlayer sp = join();
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A);
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_B);
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);

        assertTrue(NeoOriginsAPI.revokePower(sp, PLUS_SIX, MOD_A));
        assertFalse(NeoOriginsAPI.revokePower(sp, PLUS_SIX, MOD_A), "already revoked");
        assertEquals(26.0, sp.getMaxHealth(), 1e-6, "mod_b still grants it");
        assertTrue(NeoOriginsAPI.hasPower(sp, PLUS_SIX));

        assertTrue(NeoOriginsAPI.revokePower(sp, PLUS_SIX, MOD_B));
        assertEquals(20.0, sp.getMaxHealth(), 1e-6);
        assertFalse(NeoOriginsAPI.hasPower(sp, PLUS_SIX));
    }

    @Test
    void aLayerGrantEndsWithItsLayerWhileAnUnattachedOneStays() {
        ServerPlayer sp = join();
        NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, ORIGIN);
        NeoOriginsAPI.grantPower(sp, OTHER_SIX, MOD_A);
        assertEquals(32.0, sp.getMaxHealth(), 1e-6);

        NeoOriginsAPI.setOrigin(sp, ORIGIN, ABYSSAL);
        assertEquals(Set.of(OTHER_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_A), "the layer grant must end on change");
        assertEquals(30.0, sp.getMaxHealth(), 1e-6, "20 + abyssal's 4 + the unattached 6");

        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, ORIGIN);
        assertTrue(NeoOriginsAPI.clearOrigin(sp, ORIGIN));
        assertEquals(Set.of(OTHER_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_A), "the layer grant must end on clear");
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);
    }

    @Test
    void aGrantToAnEmptyLayerEndsAtItsNextChange() {
        ServerPlayer sp = join();
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, CLASS);
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);
        NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
        assertEquals(Set.of(PLUS_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_A), "another layer must not end it");
        NeoOriginsAPI.setOrigin(sp, CLASS, id("class_warrior"));
        assertTrue(NeoOriginsAPI.grantedPowers(sp, MOD_A).isEmpty());
        assertFalse(NeoOriginsAPI.hasPower(sp, PLUS_SIX));
    }

    @Test
    void aFullResetEndsLayerGrantsAndKeepsUnattachedOnesApplied() {
        ServerPlayer sp = join();
        NeoOriginsAPI.setOrigin(sp, ORIGIN, ABYSSAL);
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, ORIGIN);
        NeoOriginsAPI.grantPower(sp, OTHER_SIX, MOD_B);
        assertEquals(36.0, sp.getMaxHealth(), 1e-6);

        OriginChangeService.clearAll(sp);
        assertTrue(NeoOriginsAPI.grantedPowers(sp, MOD_A).isEmpty());
        assertEquals(Set.of(OTHER_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_B));
        assertEquals(26.0, sp.getMaxHealth(), 1e-6, "the unattached grant must survive the reset's modifier sweep");
    }

    @Test
    void anOriginPowerTheApiAlsoGrantsSurvivesTheOriginChanging() {
        ServerPlayer sp = join();
        NeoOriginsAPI.setOrigin(sp, ORIGIN, ABYSSAL);
        NeoOriginsAPI.grantPower(sp, ABYSSAL_ARMOR, MOD_A);
        assertEquals(24.0, sp.getMaxHealth(), 1e-6, "one power, applied once");

        NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
        assertEquals(24.0, sp.getMaxHealth(), 1e-6, "the API grant still holds abyssal's armor");
        assertTrue(NeoOriginsAPI.hasPower(sp, ABYSSAL_ARMOR));

        NeoOriginsAPI.revokePower(sp, ABYSSAL_ARMOR, MOD_A);
        assertEquals(20.0, sp.getMaxHealth(), 1e-6);
    }

    @Test
    void thePredicateRevokeOnlyTouchesItsOwnSource() {
        ServerPlayer sp = join();
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A);
        NeoOriginsAPI.grantPower(sp, OTHER_SIX, MOD_A);
        NeoOriginsAPI.grantPower(sp, OTHER_SIX, MOD_B);

        assertEquals(1, NeoOriginsAPI.revokePowers(sp, MOD_A, holder -> holder.id().equals(PLUS_SIX)));
        assertEquals(Set.of(OTHER_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_A));
        assertEquals(1, NeoOriginsAPI.revokePowers(sp, MOD_A));
        assertEquals(Set.of(OTHER_SIX), NeoOriginsAPI.grantedPowers(sp, MOD_B), "mod_b's grant must be untouched");
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);
    }

    @Test
    void grantsRoundTripThroughTheSaveAndOldSavesLoadEmpty() {
        ServerPlayer sp = join();
        NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
        NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A, ORIGIN);
        NeoOriginsAPI.grantPower(sp, OTHER_SIX, MOD_B);
        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        Tag saved = PlayerOriginData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        assertTrue(((CompoundTag) saved).contains("origins"), "the saved map must stay flat");
        PlayerOriginData loaded = PlayerOriginData.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertEquals(data.getPowerGrants(), loaded.getPowerGrants());
        assertEquals(data.getOrigins(), loaded.getOrigins());

        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("power_grants");
        PlayerOriginData fromOld = PlayerOriginData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow();
        assertTrue(fromOld.getPowerGrants().isEmpty());
        assertEquals(Map.of(ORIGIN, HUMAN), fromOld.getOrigins(), "an old save must still load its origins");
    }

    @Test
    void grantAndRevokeEventsCarryThePowersTypeAndConfig() {
        List<com.cyberday1.neoorigins.api.event.PowerGrantedEvent> granted = new java.util.ArrayList<>();
        List<com.cyberday1.neoorigins.api.event.PowerRevokedEvent> revoked = new java.util.ArrayList<>();
        java.util.function.Consumer<com.cyberday1.neoorigins.api.event.PowerGrantedEvent> onGrant = granted::add;
        java.util.function.Consumer<com.cyberday1.neoorigins.api.event.PowerRevokedEvent> onRevoke = revoked::add;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.cyberday1.neoorigins.api.event.PowerGrantedEvent.class, onGrant);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(com.cyberday1.neoorigins.api.event.PowerRevokedEvent.class, onRevoke);
        try {
            ServerPlayer sp = join();
            var plusSix = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(PLUS_SIX);
            NeoOriginsAPI.grantPower(sp, PLUS_SIX, MOD_A);
            var g = granted.stream().filter(e -> e.getPowerId().equals(PLUS_SIX)).findFirst().orElseThrow();
            assertTrue(g.getPower() == plusSix);
            assertTrue(g.getPowerType() == plusSix.type());
            assertTrue(g.getConfig() == plusSix.config());

            NeoOriginsAPI.revokePower(sp, PLUS_SIX, MOD_A);
            var r = revoked.stream().filter(e -> e.getPowerId().equals(PLUS_SIX)).findFirst().orElseThrow();
            assertTrue(r.getConfig() == plusSix.config(), "a revoke must still carry the config");

            // The origin-change path: abyssal's armor is granted, then revoked on a switch to human.
            var armor = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(ABYSSAL_ARMOR);
            NeoOriginsAPI.setOrigin(sp, ORIGIN, ABYSSAL);
            assertTrue(granted.stream().anyMatch(e -> e.getPower() == armor && e.getConfig() == armor.config()));
            NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
            assertTrue(revoked.stream().anyMatch(e -> e.getPower() == armor && e.getPowerType() == armor.type()));
        } finally {
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(onGrant);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(onRevoke);
        }
    }

    private ServerPlayer join() {
        ServerPlayer sp = PlayerLifecycle.realPlayer(server);
        // 26.2+ throws on getId() before the level assigns one; the morph sync reads it.
        sp.setId(++nextId);
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return sp;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }

    private int nextId = 0;

    /**
     * Deep stubs, except for a call that takes an entity: a recorded deep stub makes Mockito
     * compare the next player against the last with equals(), which throws on 26.2+ before the
     * level has assigned an ID. Those calls get a fresh, unrecorded deep mock (as UniqueOriginClaimsTest).
     */
    private static Object deepUnlessEntityArg(org.mockito.invocation.InvocationOnMock inv) throws Throwable {
        for (Object arg : inv.getArguments()) {
            if (arg instanceof net.minecraft.world.entity.Entity) {
                Class<?> type = inv.getMethod().getReturnType();
                if (type.isPrimitive() || type == String.class) return Mockito.RETURNS_DEFAULTS.answer(inv);
                return Mockito.mock(type, Mockito.RETURNS_DEEP_STUBS);
            }
        }
        return Mockito.RETURNS_DEEP_STUBS.answer(inv);
    }
}
