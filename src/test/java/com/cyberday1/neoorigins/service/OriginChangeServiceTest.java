package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.event.OriginChangedEvent;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.origin.SetOriginResult;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The shared origin-change path behind the API, /origin set and reset, and the picker. */
class OriginChangeServiceTest {

    private static final Identifier ORIGIN = id("origin");
    private static final Identifier CLASS = id("class");
    private static final Identifier HUMAN = id("human");
    private static final Identifier MERLING = id("merling");
    private static final Identifier WARRIOR = id("class_warrior");

    private static Map<Identifier, OriginLayer> priorLayers;
    private static List<OriginLayer> priorSorted;
    private static List<? extends String> priorUnique;
    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    private MinecraftServer server;
    private OriginClaimsData claims;
    private final List<OriginChangedEvent> events = new ArrayList<>();
    private boolean cancelAll;
    private final Consumer<OriginChangedEvent> listener = e -> {
        events.add(e);
        if (cancelAll) e.setCanceled(true);
    };

    @BeforeAll
    static void loadWorld() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        priorUnique = List.copyOf(AdminConfig.UNIQUE_ORIGIN_LAYERS.get());
        priorLayers = LayerDataManager.INSTANCE.getLayers();
        priorSorted = LayerDataManager.INSTANCE.getSortedLayers();
        var shipped = RealDatapack.layers();
        LayerDataManager.INSTANCE.setClientData(shipped.getLayers(), shipped.getSortedLayers());
    }

    @AfterAll
    static void restoreWorld() {
        LayerDataManager.INSTANCE.setClientData(priorLayers, priorSorted);
        AdminConfig.UNIQUE_ORIGIN_LAYERS.set(priorUnique);
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @BeforeEach
    void freshServer() {
        server = Mockito.mock(MinecraftServer.class, Mockito.RETURNS_DEEP_STUBS);
        claims = new OriginClaimsData();
        ServerLevel overworld = Mockito.mock(ServerLevel.class);
        SavedDataStorage storage = Mockito.mock(SavedDataStorage.class);
        Mockito.when(server.overworld()).thenReturn(overworld);
        Mockito.when(overworld.getDataStorage()).thenReturn(storage);
        Mockito.doReturn(claims).when(storage).computeIfAbsent(Mockito.any());
        Mockito.when(server.getPlayerList().getPlayers()).thenReturn(List.of());
        AdminConfig.UNIQUE_ORIGIN_LAYERS.set(List.of());
        NeoForge.EVENT_BUS.addListener(OriginChangedEvent.class, listener);
    }

    @AfterEach
    void unhook() {
        NeoForge.EVENT_BUS.unregister(listener);
    }

    @Test
    void setOriginSetsTheLayerAndFiresTheEvent() {
        ServerPlayer sp = join();
        assertEquals(Optional.empty(), NeoOriginsAPI.getOrigin(sp, ORIGIN));

        assertEquals(SetOriginResult.SUCCESS, NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN));
        assertEquals(Optional.of(HUMAN), NeoOriginsAPI.getOrigin(sp, ORIGIN));
        assertEquals(1, events.size(), "setOrigin must fire OriginChangedEvent");
        assertEquals(HUMAN, events.get(0).getNewOrigin());

        assertEquals(SetOriginResult.UNCHANGED, NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN));
        assertEquals(SetOriginResult.UNKNOWN_LAYER, NeoOriginsAPI.setOrigin(sp, id("no_such_layer"), HUMAN));
        assertEquals(SetOriginResult.UNKNOWN_ORIGIN, NeoOriginsAPI.setOrigin(sp, ORIGIN, id("no_such_origin")));
        assertThrows(UnsupportedOperationException.class, () -> NeoOriginsAPI.getOrigins(sp).clear(),
            "getOrigins must not expose the live map");
    }

    @Test
    void aListenerCancelsANormalChangeButNotAnAdminOne() {
        ServerPlayer sp = join();
        cancelAll = true;

        assertEquals(SetOriginResult.CANCELLED, NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN));
        assertEquals(Optional.empty(), NeoOriginsAPI.getOrigin(sp, ORIGIN));

        assertEquals(SetOriginResult.SUCCESS, OriginChangeService.set(sp, ORIGIN, HUMAN, OriginChangeService.Mode.ADMIN));
        assertEquals(Optional.of(HUMAN), NeoOriginsAPI.getOrigin(sp, ORIGIN), "an admin set was cancelled");
        assertEquals(2, events.size(), "the admin set must still fire the event");
    }

    @Test
    void aClaimedUniqueOriginIsRefusedUnlessAdmin() {
        AdminConfig.UNIQUE_ORIGIN_LAYERS.set(List.of(ORIGIN.toString()));
        UUID rival = UUID.randomUUID();
        claims.claim(ORIGIN, MERLING, rival);
        ServerPlayer sp = join();

        assertEquals(SetOriginResult.CLAIMED, NeoOriginsAPI.setOrigin(sp, ORIGIN, MERLING));
        assertEquals(rival, claims.getOwner(ORIGIN, MERLING));

        assertEquals(SetOriginResult.SUCCESS, OriginChangeService.set(sp, ORIGIN, MERLING, OriginChangeService.Mode.ADMIN));
        assertEquals(sp.getUUID(), claims.getOwner(ORIGIN, MERLING), "an admin set takes over the claim");

        assertEquals(SetOriginResult.SUCCESS, NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN));
        assertEquals(null, claims.getOwner(ORIGIN, MERLING), "leaving a unique origin must release it");
    }

    @Test
    void clearingOneLayerKeepsTheOtherLayerAndDynamicGrants() {
        ServerPlayer sp = join();
        NeoOriginsAPI.setOrigin(sp, ORIGIN, HUMAN);
        NeoOriginsAPI.setOrigin(sp, CLASS, WARRIOR);
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        Identifier granted = id("test_dynamic_grant");
        data.addDynamicGrant(granted);

        assertTrue(NeoOriginsAPI.clearOrigin(sp, CLASS));
        assertEquals(Map.of(ORIGIN, HUMAN), NeoOriginsAPI.getOrigins(sp));
        assertTrue(data.getDynamicGrantedPowers().contains(granted), "a single-layer clear dropped a dynamic grant");
        assertFalse(NeoOriginsAPI.clearOrigin(sp, CLASS), "an empty layer reports nothing cleared");

        OriginChangeService.clearAll(sp);
        assertTrue(NeoOriginsAPI.getOrigins(sp).isEmpty());
        assertFalse(data.getDynamicGrantedPowers().contains(granted), "a full reset keeps wiping dynamic grants");
    }

    @Test
    void thePickerStillSetsTheOriginThroughTheService() throws Exception {
        ServerPlayer sp = join();
        choose(sp, ORIGIN, HUMAN);
        assertEquals(Optional.of(HUMAN), NeoOriginsAPI.getOrigin(sp, ORIGIN), "the picker no longer sets the origin");
        assertEquals(1, events.size(), "the picker must fire OriginChangedEvent exactly once");

        cancelAll = true;
        events.clear();
        ServerPlayer other = join();
        choose(other, ORIGIN, HUMAN);
        assertEquals(Optional.empty(), NeoOriginsAPI.getOrigin(other, ORIGIN), "a listener could not cancel a pick");
    }

    private ServerPlayer join() {
        ServerPlayer sp = PlayerLifecycle.realPlayer(server);
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return sp;
    }

    /** The real pick handler, run the way the network thread would run it. */
    private static void choose(ServerPlayer sp, Identifier layer, Identifier origin) throws Exception {
        IPayloadContext ctx = Mockito.mock(IPayloadContext.class);
        Mockito.when(ctx.player()).thenReturn(sp);
        Mockito.when(ctx.enqueueWork(Mockito.any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        Method handler = NeoOriginsNetwork.class.getDeclaredMethod(
            "handleChooseOrigin", ChooseOriginPayload.class, IPayloadContext.class);
        handler.setAccessible(true);
        handler.invoke(null, new ChooseOriginPayload(layer, origin), ctx);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }
}
