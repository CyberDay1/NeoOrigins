package com.cyberday1.neoorigins.power.util;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.power.builtin.EffectOverTimePower;
import com.cyberday1.neoorigins.power.builtin.base.AbstractTogglePower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code toggle_messages: false} silences a toggle's on/off chat line. */
class ToggleMessagesTest {

    private static final Identifier ID = Identifier.parse("neoorigins:test_toggle_messages");

    record TestConfig() implements PowerConfiguration {}

    static final class QuietToggle extends AbstractTogglePower<TestConfig> {
        @Override public Codec<TestConfig> codec() { return com.mojang.serialization.MapCodec.unit(TestConfig::new).codec(); }
        @Override protected void tickEffect(ServerPlayer player, TestConfig config) { }
        @Override protected void removeEffect(ServerPlayer player, TestConfig config) { }
    }

    @AfterEach
    void reset() {
        PowerUpkeep.registerToggleMessages(ID, true);
    }

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return sp;
    }

    /** Packets sent to the player: a chat line is one. */
    private static long sends(ServerPlayer sp) {
        return Mockito.mockingDetails(sp.connection).getInvocations().stream()
            .filter(i -> i.getMethod().getName().equals("send")).count();
    }

    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void parsesWithATrueDefault() {
        assertTrue(PowerUpkeep.parseToggleMessages(json("{}"), "t"));
        assertFalse(PowerUpkeep.parseToggleMessages(json("{\"toggle_messages\": false}"), "t"));
        assertTrue(PowerUpkeep.parseToggleMessages(json("{\"toggle_messages\": {}}"), "t"),
            "a malformed value keeps the messages");
    }

    @Test
    void theLoaderRecordsItForEveryToggleTypeAndAReloadClearsIt() {
        Identifier aura = Identifier.parse("neoorigins:test_toggle_messages_aura");
        try {
            com.cyberday1.neoorigins.data.PowerDataManager.registerUpkeep(
                ID, new QuietToggle(), json("{\"toggle_messages\": false}"));
            com.cyberday1.neoorigins.data.PowerDataManager.registerUpkeep(
                aura, new EffectOverTimePower(), json("{\"toggle_messages\": false}"));
            assertFalse(PowerUpkeep.sendsToggleMessages(ID));
            assertFalse(PowerUpkeep.sendsToggleMessages(aura), "effect_over_time reads it too");

            PowerUpkeep.clearTable();
            assertTrue(PowerUpkeep.sendsToggleMessages(ID));
            assertTrue(PowerUpkeep.sendsToggleMessages(aura));
        } finally {
            PowerUpkeep.registerToggleMessages(aura, true);
        }
    }

    @Test
    void aKeypressOnASilentToggleSendsNoChat() {
        ServerPlayer sp = player();
        PowerHolder<TestConfig> holder = new PowerHolder<>(ID, new QuietToggle(), new TestConfig(),
            Component.empty(), Component.empty());

        holder.onActivated(sp);
        long loud = sends(sp);
        assertTrue(loud > 0, "the default toggle announces itself");

        PowerUpkeep.registerToggleMessages(ID, false);
        holder.onActivated(sp);
        holder.onActivated(sp);
        assertEquals(loud, sends(sp), "a silent toggle sends nothing either way");
    }

    @Test
    void anUpkeepShutOffOnASilentToggleSendsNoChat() {
        ServerPlayer sp = player();
        var previous = PowerUpkeep.setResyncForTest(p -> { });
        try {
            PowerUpkeep.registerToggleMessages(ID, false);
            PowerUpkeep.announceShutOff(sp, ID);
            assertEquals(0, sends(sp));
            PowerUpkeep.registerToggleMessages(ID, true);
            PowerUpkeep.announceShutOff(sp, ID);
            assertTrue(sends(sp) > 0);
        } finally {
            PowerUpkeep.setResyncForTest(previous);
        }
    }
}
