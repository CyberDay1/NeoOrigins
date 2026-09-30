package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/**
 * {@code hud_render.should_render: false} keeps a bar out of the full sync on
 * every path that registers one, so it never reaches the client and cannot hold
 * a stacked slot. Absent, it defaults to true, as in Apoli.
 */
// hub: neoorigins/resource-bar-first-spend.md
class ResourceBarShouldRenderTest {

    private static final String KEY = "test:bar";
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
        CompatAttachments.clearCooldownDurations();
    }

    private static String hud(String shouldRender) {
        return shouldRender == null ? "{}" : "{\"should_render\": " + shouldRender + "}";
    }

    private static boolean syncedNative(String shouldRender) {
        JsonObject json = JsonParser.parseString(
            "{\"min\": 0, \"max\": 10, \"start_value\": 10, \"hud_render\": " + hud(shouldRender) + "}").getAsJsonObject();
        json.addProperty("_power_id", KEY);
        var power = new ResourcePower();
        var config = power.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        var holder = new PowerHolder<>(Identifier.parse(KEY), power, config, Component.empty(), Component.empty());
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        holder.onGranted(sp);
        return CompatAttachments.fullSyncEntries(sp).containsKey(KEY);
    }

    private static boolean syncedApoli(String type, String extra, String shouldRender) {
        JsonObject json = JsonParser.parseString(
            "{\"type\": \"" + type + "\"" + extra + ", \"hud_render\": " + hud(shouldRender) + "}").getAsJsonObject();
        var config = OriginsCompatPowerLoader.compileForTest(Identifier.parse(KEY), type, json);
        assertNotNull(config, type + " did not compile");
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        config.onGranted().accept(sp);
        return CompatAttachments.fullSyncEntries(sp).containsKey(KEY);
    }

    @Test
    void nativeResource() {
        assertTrue(syncedNative(null), "should_render must default to true");
        assertTrue(syncedNative("true"));
        assertFalse(syncedNative("false"), "a should_render:false native bar reached the client");
    }

    @Test
    void apoliResource() {
        String extra = ", \"min\": 0, \"max\": 10, \"start_value\": 10";
        assertTrue(syncedApoli("origins:resource", extra, null), "should_render must default to true");
        assertFalse(syncedApoli("origins:resource", extra, "false"), "a should_render:false resource bar reached the client");
    }

    @Test
    void apoliCooldown() {
        String extra = ", \"cooldown\": 40";
        assertTrue(syncedApoli("origins:cooldown", extra, null), "should_render must default to true");
        assertFalse(syncedApoli("origins:cooldown", extra, "false"), "a should_render:false cooldown bar reached the client");
    }

    @Test
    void activeCooldownBar() {
        String extra = ", \"cooldown\": 40, \"entity_action\": {\"type\": \"origins:nothing\"}";
        assertTrue(syncedApoli("origins:active_self", extra, null), "should_render must default to true");
        assertFalse(syncedApoli("origins:active_self", extra, "false"), "a should_render:false active cooldown bar reached the client");
    }
}
