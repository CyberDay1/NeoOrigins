package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * An Apoli-format resource is the player's progress: a level counter, stored energy,
 * a kill tally. Logging back in is not a fresh grant, so the stored value has to
 * survive it.
 *
 * <p>{@code PowerType.onLogin} defaults to re-running {@code onGranted}, which is
 * right for a power that just re-applies an effect. {@code origins:resource} seeds
 * {@code start_value} there, so every world join reset the bar to its floor, and any
 * power reading the resource read the floor with it. The native ResourcePower fixed
 * this for itself (GitHub #90); Route B kept the default until now.
 */
class CompatResourceRelogTest {

    private static final String KEY = "smokedemon:lich_health_resource";

    private MockedStatic<PacketDistributor> packets;
    private MockedStatic<ContentTogglesConfig> toggles;

    @BeforeEach
    void rig() {
        packets = mockStatic(PacketDistributor.class);
        packets.when(() -> PacketDistributor.sendToPlayer(any(ServerPlayer.class), any(CustomPacketPayload.class), any(CustomPacketPayload[].class)))
            .thenAnswer(inv -> null);
        toggles = mockStatic(ContentTogglesConfig.class);
    }

    @AfterEach
    void tearDown() {
        toggles.close();
        packets.close();
        CompatAttachments.clearResourceMeta();
    }

    private static PowerHolder<CompatPower.Config> holder(String extra) {
        JsonObject json = JsonParser.parseString(
            "{\"type\": \"origins:resource\", \"min\": 0, \"max\": 20" + extra + "}").getAsJsonObject();
        var config = OriginsCompatPowerLoader.compileForTest(Identifier.parse(KEY), "origins:resource", json);
        assertNotNull(config, "origins:resource did not compile");
        return new PowerHolder<>(Identifier.parse(KEY), CompatPower.INSTANCE, config,
            Component.empty(), Component.empty());
    }

    private static int stored(ServerPlayer player) {
        return player.getData(CompatAttachments.resourceState()).get(KEY, -1);
    }

    @Test
    void aLoginKeepsTheValueThePlayerEarned() {
        var power = holder(", \"start_value\": 0");
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        power.onGranted(sp);
        assertEquals(0, stored(sp), "a fresh grant seeds start_value");

        sp.getData(CompatAttachments.resourceState()).set(KEY, 7);
        power.onLogin(sp);
        assertEquals(7, stored(sp), "the stored resource was reset by logging back in");
    }

    @Test
    void aLoginStillSeedsAPlayerWhoHasNoStoredValue() {
        var power = holder(", \"start_value\": 5");
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        // No grant ran for this player: a save from before the bar existed, or a key
        // that failed to round-trip. Without a value the bar is left out of the sync
        // entirely, so login still has to seed one.
        power.onLogin(sp);
        assertEquals(5, stored(sp));
    }

    @Test
    void aFreshGrantStillResets() {
        // Re-granting IS the deliberate reset path (an origin change, an orb re-pick).
        var power = holder(", \"start_value\": 0");
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.getData(CompatAttachments.resourceState()).set(KEY, 7);
        power.onGranted(sp);
        assertEquals(0, stored(sp));
    }
}
