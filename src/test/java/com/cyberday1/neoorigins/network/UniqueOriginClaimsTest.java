package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.client.ClientOriginClaims;
import com.cyberday1.neoorigins.client.ClientOriginState;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.payload.ChooseOriginPayload;
import com.cyberday1.neoorigins.network.payload.OpenOriginScreenPayload;
import com.cyberday1.neoorigins.network.payload.SyncOriginClaimsPayload;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.screen.OriginSelectionPresenter;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unique-origin claims end to end: the real pick handler refuses a claimed origin,
 * a claim reaches every online client, and the picker presents it as claimed.
 * The origin layer is configured unique; the class layer is left unpicked so an
 * accepted pick never completes the walk (which would teleport).
 */
// hub: neoorigins/unique-origins.md
class UniqueOriginClaimsTest {

    private static final ResourceLocation ORIGIN = id("origin");
    private static final ResourceLocation HUMAN = id("human");
    private static final ResourceLocation MERLING = id("merling");
    private static final ResourceLocation CAVE_DRAGON = id("cave_dragon");
    private static final List<ResourceLocation> DRAGONS = List.of(CAVE_DRAGON, id("forest_dragon"), id("sea_dragon"));

    private static Map<ResourceLocation, OriginLayer> priorLayers;
    private static List<OriginLayer> priorSorted;
    private static Map<ResourceLocation, Origin> shippedOrigins;
    private static List<? extends String> priorUnique;
    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    private MinecraftServer server;
    private OriginClaimsData claims;
    private final List<ServerPlayer> online = new ArrayList<>();

    @BeforeAll
    static void loadWorld() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        priorUnique = List.copyOf(AdminConfig.UNIQUE_ORIGIN_LAYERS.get());
        AdminConfig.UNIQUE_ORIGIN_LAYERS.set(List.of(ORIGIN.toString()));
        priorLayers = LayerDataManager.INSTANCE.getLayers();
        priorSorted = LayerDataManager.INSTANCE.getSortedLayers();
        var shipped = RealDatapack.layers();
        LayerDataManager.INSTANCE.setClientData(shipped.getLayers(), shipped.getSortedLayers());
        // Dragon Survival installed: the dragons load like any other origin.
        shippedOrigins = OriginDataManager.INSTANCE.getOrigins();
        Map<ResourceLocation, Origin> withDragons = new HashMap<>(shippedOrigins);
        Origin human = shippedOrigins.get(HUMAN);
        for (ResourceLocation d : DRAGONS) {
            withDragons.put(d, new Origin(d, List.of(), human.icon(), human.impact(), human.order(), false,
                false, human.name(), human.description(), List.of(), human.spawnLocation(), List.of(),
                human.figuraModel(), human.figuraModels()));
        }
        OriginDataManager.INSTANCE.setClientData(withDragons);
    }

    @AfterAll
    static void restoreWorld() {
        OriginDataManager.INSTANCE.setClientData(shippedOrigins);
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
        DimensionDataStorage storage = Mockito.mock(DimensionDataStorage.class);
        Mockito.when(server.overworld()).thenReturn(overworld);
        Mockito.when(overworld.getDataStorage()).thenReturn(storage);
        Mockito.doReturn(claims).when(storage).computeIfAbsent(Mockito.any(), Mockito.anyString());
        Mockito.when(server.getPlayerList().getPlayers()).thenReturn(online);
        Mockito.when(server.getPlayerList().getPlayer(Mockito.any(UUID.class))).thenAnswer(inv ->
            online.stream().filter(p -> p.getUUID().equals(inv.getArgument(0))).findFirst().orElse(null));
        ClientOriginClaims.clear();
        ClientOriginState.setOrigins(Map.of(), false);
    }

    @AfterEach
    void clearClient() {
        ClientOriginClaims.clear();
        ClientOriginState.setOrigins(Map.of(), false);
    }

    // ── Server: the authority ───────────────────────────────────────────

    @Test
    void aClaimedOriginIsRefusedServerSide() throws Exception {
        UUID rival = UUID.randomUUID();
        claims.claim(ORIGIN, MERLING, rival);
        ServerPlayer sp = join();

        choose(sp, MERLING);

        assertNull(origin(sp), "the server gave a player an origin another player has claimed");
        assertEquals(rival, claims.getOwner(ORIGIN, MERLING), "a refused pick moved the claim");
        assertTrue(sent(sp).stream().anyMatch(p -> p instanceof OpenOriginScreenPayload),
            "a refused player was left without a picker to choose again");
    }

    @Test
    void aFreeOriginIsTakenAndClaimed() throws Exception {
        claims.claim(ORIGIN, MERLING, UUID.randomUUID());
        ServerPlayer sp = join();

        choose(sp, HUMAN);

        assertEquals(HUMAN, origin(sp), "an unclaimed pick was refused");
        assertEquals(sp.getUUID(), claims.getOwner(ORIGIN, HUMAN), "an accepted pick in a unique layer was not claimed");
    }

    @Test
    void aClaimReachesEveryOnlineClient() throws Exception {
        ServerPlayer picker = join();
        ServerPlayer watcher = join();

        choose(picker, HUMAN);

        SyncOriginClaimsPayload toWatcher = lastClaims(watcher);
        assertEquals(picker.getGameProfile().getName(), toWatcher.claims().getOrDefault(ORIGIN, Map.of()).get(HUMAN),
            "another online player was not told the origin is now claimed");
        assertFalse(lastClaims(picker).claims().getOrDefault(ORIGIN, Map.of()).containsKey(HUMAN),
            "a player's own claim must not lock it for them");
    }

    @Test
    void aDragonCanStillBePickedDirectly() throws Exception {
        ServerPlayer sp = join();
        choose(sp, CAVE_DRAGON);
        assertEquals(CAVE_DRAGON, origin(sp), "excluding the dragons from random also stopped a direct pick");
    }

    // ── Client: the courtesy copy ───────────────────────────────────────

    @Test
    void theClientCopyReflectsAClaimAfterSync() throws Exception {
        ServerPlayer picker = join();
        ServerPlayer watcher = join();
        choose(picker, HUMAN);

        // The bytes the watcher's client would decode.
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        SyncOriginClaimsPayload.STREAM_CODEC.encode(buf, lastClaims(watcher));
        ClientOriginClaims.applySync(SyncOriginClaimsPayload.STREAM_CODEC.decode(buf).claims());

        assertTrue(ClientOriginClaims.isLocked(ORIGIN, HUMAN), "the client copy did not take the synced claim");
        assertEquals(picker.getGameProfile().getName(), ClientOriginClaims.owner(ORIGIN, HUMAN));
    }

    @Test
    void thePickerPresentsAClaimedOriginAsClaimed() {
        ClientOriginClaims.set(Map.of(ORIGIN, Map.of(MERLING, "Rival")));
        OriginSelectionPresenter picker = picker();

        assertTrue(picker.allOriginIds().contains(MERLING), "a claimed origin should still be listed, marked as claimed");
        assertEquals("Rival", picker.claimedBy(MERLING), "the picker does not know who holds the origin");
        assertNull(picker.claimedBy(HUMAN));

        picker.select(MERLING);
        assertFalse(picker.canConfirm(), "Confirm is live on an origin the server will refuse");
        try (var net = Mockito.mockStatic(PacketDistributor.class)) {
            picker.confirm();
            net.verify(() -> PacketDistributor.sendToServer(Mockito.any()), Mockito.never());
        }
        assertEquals(0, picker.currentLayerIndex(), "the picker advanced past a claimed pick");

        picker.select(HUMAN);
        assertTrue(picker.canConfirm());
        try (var net = Mockito.mockStatic(PacketDistributor.class)) {
            picker.confirm();
            net.verify(() -> PacketDistributor.sendToServer(new ChooseOriginPayload(ORIGIN, HUMAN)));
        }
    }

    @Test
    void thePickersRandomRollSkipsClaimedAndExcludedOrigins() {
        ClientOriginClaims.set(Map.of(ORIGIN, Map.of(MERLING, "Rival")));
        OriginSelectionPresenter picker = picker();
        assertTrue(picker.allOriginIds().containsAll(DRAGONS), "the dragons are not listed, so the roll never faces them");

        Set<ResourceLocation> hits = new HashSet<>();
        for (int i = 0; i < 1000; i++) hits.add(picker.randomId());

        assertFalse(hits.contains(MERLING), "the Random button rolled a claimed origin");
        for (ResourceLocation d : DRAGONS) assertFalse(hits.contains(d), "the Random button rolled " + d);
        assertEquals(picker.allOriginIds().size() - DRAGONS.size() - 1, hits.size(),
            "1000 rolls should reach every other listed origin");
    }

    // ── Rig ─────────────────────────────────────────────────────────────

    private ServerPlayer join() {
        ServerPlayer sp = PlayerLifecycle.realPlayer(server);
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        assertSame(server, sp.getServer(), "the player is not on the rig's server");
        online.add(sp);
        return sp;
    }

    private static OriginSelectionPresenter picker() {
        OriginSelectionPresenter p = new OriginSelectionPresenter();
        assertTrue(p.init(), "the picker found no layer to show");
        p.buildRows();
        assertEquals(ORIGIN, p.currentLayer().id());
        return p;
    }

    /** The real pick handler, run the way the network thread would run it. */
    private static void choose(ServerPlayer sp, ResourceLocation origin) throws Exception {
        IPayloadContext ctx = Mockito.mock(IPayloadContext.class);
        Mockito.when(ctx.player()).thenReturn(sp);
        Mockito.when(ctx.enqueueWork(Mockito.any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return CompletableFuture.completedFuture(null);
        });
        Method handler = NeoOriginsNetwork.class.getDeclaredMethod(
            "handleChooseOrigin", ChooseOriginPayload.class, IPayloadContext.class);
        handler.setAccessible(true);
        handler.invoke(null, new ChooseOriginPayload(ORIGIN, origin), ctx);
    }

    private static ResourceLocation origin(ServerPlayer sp) {
        return sp.getData(OriginAttachments.originData()).getOrigin(ORIGIN);
    }

    /** Every custom payload sent to {@code sp}, in order, whichever send overload carried it. */
    private static List<CustomPacketPayload> sent(ServerPlayer sp) {
        List<CustomPacketPayload> out = new ArrayList<>();
        for (var call : Mockito.mockingDetails(sp.connection).getInvocations()) {
            for (Object arg : call.getArguments()) {
                if (arg instanceof CustomPacketPayload p) out.add(p);
                else if (arg instanceof ClientboundCustomPayloadPacket packet) out.add(packet.payload());
            }
        }
        return out;
    }

    private static SyncOriginClaimsPayload lastClaims(ServerPlayer sp) {
        SyncOriginClaimsPayload last = null;
        for (CustomPacketPayload p : sent(sp)) if (p instanceof SyncOriginClaimsPayload c) last = c;
        if (last == null) fail(sp.getGameProfile().getName() + " was never sent the claims");
        return last;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("neoorigins", path);
    }
}
