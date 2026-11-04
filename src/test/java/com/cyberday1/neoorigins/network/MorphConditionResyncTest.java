package com.cyberday1.neoorigins.network;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.CodePowerOptions;
import com.cyberday1.neoorigins.api.power.PowerDisplay;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.OriginClaimsData;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.EntityModelPower;
import com.cyberday1.neoorigins.power.morph.ServerMorphState;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.storage.SavedDataStorage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** An entity_model whose condition flips mid-session must reach the client (issue #141). */
class MorphConditionResyncTest {

    private static final Identifier WOLF_FORM = Identifier.fromNamespaceAndPath("testmod", "wolf_form");
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("testmod", "test");
    private static final AtomicBoolean SHIFTED = new AtomicBoolean();

    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    @BeforeAll
    static void register() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
        EntityModelPower.Config wolf = new EntityModelPower.Config(Optional.empty(),
            Optional.of(Identifier.withDefaultNamespace("wolf")), Optional.empty(), Optional.empty(),
            Optional.of(false), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.empty(), Optional.empty(), Optional.empty(), "neoorigins:entity_model");
        NeoOriginsAPI.registerPower(WOLF_FORM, new EntityModelPower(), wolf,
            PowerDisplay.of(Component.literal("Wolf form"), Component.literal("test")),
            CodePowerOptions.builder().condition(p -> SHIFTED.get()).build());
    }

    @AfterAll
    static void unregister() {
        PowerDataManager.INSTANCE.unregisterCodePower(WOLF_FORM);
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @Test
    void theMorphFollowsItsConditionAtMostEveryFewTicks() {
        SHIFTED.set(false);
        ServerPlayer sp = join();
        NeoOriginsAPI.grantPower(sp, WOLF_FORM, SOURCE);
        NeoOriginsNetwork.syncActivePowersToPlayer(sp);
        assertNull(ServerMorphState.get(sp.getUUID()), "the condition is off, so no morph");

        sp.tickCount = 100;
        NeoOriginsNetwork.resyncIfPhaseGateFlipped(sp);
        assertNull(ServerMorphState.get(sp.getUUID()), "an unchanged condition must not morph");

        SHIFTED.set(true);
        sp.tickCount = 101;
        NeoOriginsNetwork.resyncIfPhaseGateFlipped(sp);
        assertNotNull(ServerMorphState.get(sp.getUUID()), "the condition turning on must morph the player");
        assertEquals(Optional.of(Identifier.withDefaultNamespace("wolf")),
            ServerMorphState.get(sp.getUUID()).entityType());

        SHIFTED.set(false);
        sp.tickCount = 102;
        NeoOriginsNetwork.resyncIfPhaseGateFlipped(sp);
        assertNotNull(ServerMorphState.get(sp.getUUID()), "a flip inside the minimum gap waits");

        sp.tickCount = 101 + NeoOriginsNetwork.MORPH_RESYNC_TICKS;
        NeoOriginsNetwork.resyncIfPhaseGateFlipped(sp);
        assertNull(ServerMorphState.get(sp.getUUID()), "the waiting change lands once the gap has passed");

        NeoOriginsNetwork.clearPhaseGate(sp.getUUID());
        ServerMorphState.remove(sp.getUUID());
    }

    private static ServerPlayer join() {
        MinecraftServer server = Mockito.mock(MinecraftServer.class,
            Mockito.withSettings().defaultAnswer(MorphConditionResyncTest::deepUnlessEntityArg));
        var playerList = Mockito.mock(net.minecraft.server.players.PlayerList.class,
            Mockito.withSettings().defaultAnswer(MorphConditionResyncTest::deepUnlessEntityArg));
        Mockito.when(server.getPlayerList()).thenReturn(playerList);
        ServerLevel overworld = Mockito.mock(ServerLevel.class);
        SavedDataStorage storage = Mockito.mock(SavedDataStorage.class);
        Mockito.when(server.overworld()).thenReturn(overworld);
        Mockito.when(overworld.getDataStorage()).thenReturn(storage);
        Mockito.doReturn(new OriginClaimsData()).when(storage).computeIfAbsent(Mockito.any());
        Mockito.when(server.getPlayerList().getPlayers()).thenReturn(List.of());
        ServerPlayer sp = PlayerLifecycle.realPlayer(server);
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        sp.setId(1);
        return sp;
    }

    /**
     * Deep stubs, except for a call that takes an entity: 26.2+ Entity.equals() throws
     * before the level has assigned an ID (same rig as PowerGrantServiceTest).
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
