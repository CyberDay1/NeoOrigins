package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.config.GameplayConfig.UpgradeAnnounceMode;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** An origin upgrade's announcement fills %s with the player's name and reaches the configured audience. */
class UpgradeAnnouncementTest {

    private static final String TEXT = "%s has ascended to Archmage!";

    private final MinecraftServer server = Mockito.mock(MinecraftServer.class, Mockito.RETURNS_DEEP_STUBS);

    private ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer(server);
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return sp;
    }

    private static long sends(ServerPlayer sp) {
        return Mockito.mockingDetails(sp.connection).getInvocations().stream()
            .filter(i -> i.getMethod().getName().equals("send")).count();
    }

    private void announce(ServerPlayer sp, String text, UpgradeAnnounceMode mode) {
        try (MockedStatic<GameplayConfig> config = mockStatic(GameplayConfig.class)) {
            config.when(GameplayConfig::upgradeAnnounceMode).thenReturn(mode);
            PlayerLifecycleEvents.announceUpgrade(sp, text);
        }
    }

    @Test
    void serverModeBroadcastsWithThePlayerNameAsTheArgument() {
        ServerPlayer sp = player();
        announce(sp, TEXT, UpgradeAnnounceMode.SERVER);

        ArgumentCaptor<Component> msg = ArgumentCaptor.forClass(Component.class);
        verify(server.getPlayerList()).broadcastSystemMessage(msg.capture(), Mockito.eq(false));
        TranslatableContents contents = (TranslatableContents) msg.getValue().getContents();
        assertEquals(TEXT, contents.getKey());
        assertArrayEquals(new Object[] { sp.getDisplayName() }, contents.getArgs());
        assertEquals(0, sends(sp), "the broadcast is the only copy");
    }

    @Test
    void playerModeSendsOnlyToTheUpgradedPlayer() {
        ServerPlayer sp = player();
        announce(sp, TEXT, UpgradeAnnounceMode.PLAYER);

        assertEquals(1, sends(sp));
        verify(server.getPlayerList(), never()).broadcastSystemMessage(any(Component.class), anyBoolean());
    }

    @Test
    void offModeAndAnEmptyAnnouncementStaySilent() {
        ServerPlayer sp = player();
        announce(sp, TEXT, UpgradeAnnounceMode.OFF);
        announce(sp, "", UpgradeAnnounceMode.SERVER);
        announce(sp, null, UpgradeAnnounceMode.PLAYER);

        assertEquals(0, sends(sp));
        verify(server.getPlayerList(), never()).broadcastSystemMessage(any(Component.class), anyBoolean());
    }
}
