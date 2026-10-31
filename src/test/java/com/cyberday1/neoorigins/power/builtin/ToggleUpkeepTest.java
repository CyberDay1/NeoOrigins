package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.action.ActionParser;
import com.cyberday1.neoorigins.compat.condition.ConditionParser;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.util.PowerUpkeep;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleUpkeepTest {
    private static final ResourceLocation ID = ResourceLocation.parse("neoorigins:test_boolean_upkeep");
    private static final ResourceLocation OTHER = ResourceLocation.parse("neoorigins:test_other_boolean_upkeep");
    private static final String MANA = "neoorigins:test_boolean_upkeep_mana";
    private MockedStatic<ContentTogglesConfig> contentConfig;
    private Consumer<ServerPlayer> previousResync;
    private int resyncs;

    @BeforeEach
    void setup() {
        contentConfig = Mockito.mockStatic(ContentTogglesConfig.class);
        previousResync = PowerUpkeep.setResyncForTest(player -> resyncs++);
    }

    @AfterEach
    void cleanup() {
        PowerUpkeep.setResyncForTest(previousResync);
        for (var id : new ResourceLocation[] { ID, OTHER }) {
            PowerUpkeep.register(id, null);
            PowerUpkeep.registerToggleMessages(id, true);
        }
        contentConfig.close();
    }

    private static JsonObject json(String body) {
        return JsonParser.parseString(body.replace("$mana", MANA)).getAsJsonObject();
    }

    private static PowerHolder<TogglePower.Config> holder(ResourceLocation id, String body) {
        var type = new TogglePower();
        var data = json(body);
        var config = type.codec().parse(JsonOps.INSTANCE, data).getOrThrow();
        PowerDataManager.registerUpkeep(id, type, data);
        return new PowerHolder<>(id, type, config, Component.empty(), Component.empty());
    }

    private static ServerPlayer player(int mana) {
        var player = PlayerLifecycle.realPlayer();
        player.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(0f);
        player.getData(CompatAttachments.resourceState()).set(MANA, mana);
        return player;
    }

    private static int mana(ServerPlayer player) {
        return player.getData(CompatAttachments.resourceState()).get(MANA, 0);
    }

    private static boolean on(ServerPlayer player, PowerHolder<TogglePower.Config> holder) {
        return player.getData(CompatAttachments.toggleState())
            .isActive(holder.id().toString(), holder.config().defaultValue());
    }

    private static void tick(ServerPlayer player, PowerHolder<?> holder, int from, int to) {
        for (int t = from; t <= to; t++) {
            player.tickCount = t;
            holder.onTick(player);
        }
    }

    private static void setOn(ServerPlayer player, boolean value) {
        ActionParser.parse(json("""
            {"type":"neoorigins:toggle", "power":"%s", "value":%s}
            """.formatted(ID, value)), "toggle-upkeep-test").execute(player);
    }

    @Test
    void defaultOnPaysOnlyOnTheConfiguredBeatWithoutNeedingAnotherPower() {
        var holder = holder(ID, """
            {"default":true, "resource_cost":"$mana", "resource_cost_amount":3, "upkeep_interval":10}
            """);
        var player = player(10);

        tick(player, holder, 1, 25);

        assertEquals(4, mana(player));
        assertTrue(on(player, holder));
        assertFalse(player.getData(CompatAttachments.toggleState()).getStates().containsKey(ID.toString()));
        assertEquals(0, resyncs);
        assertFalse(holder.occupiesHotkeySlot());
    }

    @Test
    void defaultOffAndExplicitOffDoNotChargeAndTheExistingActionEnablesUpkeep() {
        var holder = holder(ID, "{\"hunger_cost\":2}");
        var player = player(0);

        tick(player, holder, 1, 40);
        assertEquals(20, player.getFoodData().getFoodLevel());
        setOn(player, true);
        tick(player, holder, 41, 60);
        assertEquals(18, player.getFoodData().getFoodLevel());
        setOn(player, false);
        tick(player, holder, 61, 80);
        assertEquals(18, player.getFoodData().getFoodLevel());
        assertEquals(0, resyncs);
    }

    @Test
    void aShortCombinedChargeSwitchesOffTheReadableFlagOnceAndTakesNothing() {
        var holder = holder(ID, """
            {"default":true, "hunger_cost":2, "resource_cost":"$mana", "resource_cost_amount":3}
            """);
        var player = player(2);

        tick(player, holder, 1, 40);

        assertFalse(on(player, holder));
        assertEquals(20, player.getFoodData().getFoodLevel());
        assertEquals(2, mana(player));
        assertEquals(1, resyncs);
        var active = ConditionParser.parse(json("""
            {"type":"neoorigins:power_active", "power":"%s"}
            """.formatted(ID)), "toggle-upkeep-test");
        assertFalse(active.test(player));
        assertTrue(Mockito.mockingDetails(player.connection).getInvocations().stream()
            .anyMatch(i -> i.getMethod().getName().equals("send")
                && i.getArguments()[0] instanceof ClientboundSystemChatPacket));

        player.getData(CompatAttachments.resourceState()).set(MANA, 9);
        holder.onLogin(player);
        tick(player, holder, 41, 60);
        assertEquals(9, mana(player));
        assertFalse(active.test(player));
        setOn(player, true);
        tick(player, holder, 61, 80);
        assertEquals(6, mana(player));
        assertEquals(18, player.getFoodData().getFoodLevel());
        assertTrue(active.test(player));
    }

    @Test
    void aFalseUpkeepConditionWaivesTheChargeWithoutSwitchingOff() {
        var holder = holder(ID, """
            {"default":true, "resource_cost":"$mana", "resource_cost_amount":3,
             "upkeep_condition":{"type":"neoorigins:constant", "value":false}}
            """);
        var player = player(0);

        tick(player, holder, 1, 60);

        assertTrue(on(player, holder));
        assertEquals(0, mana(player));
        assertEquals(0, resyncs);
    }

    @Test
    void togglesKeepIndependentCostsAndStatesAndSilentShutoffStillSyncs() {
        var expensive = holder(ID, """
            {"default":true, "resource_cost":"$mana", "resource_cost_amount":5, "toggle_messages":false}
            """);
        var cheap = holder(OTHER, "{\"default\":true, \"hunger_cost\":1}");
        var player = player(2);

        tick(player, expensive, 20, 20);
        tick(player, cheap, 20, 20);

        assertFalse(on(player, expensive));
        assertTrue(on(player, cheap));
        assertEquals(2, mana(player));
        assertEquals(19, player.getFoodData().getFoodLevel());
        assertEquals(1, resyncs);
        assertFalse(Mockito.mockingDetails(player.connection).getInvocations().stream()
            .anyMatch(i -> i.getMethod().getName().equals("send")
                && i.getArguments()[0] instanceof ClientboundSystemChatPacket));
    }

    @Test
    void resourceUpkeepUsesTheSharedHungerFallbackWhenBarsAreDisabled() {
        contentConfig.when(ContentTogglesConfig::isResourceBarsDisabled).thenReturn(true);
        var holder = holder(ID, """
            {"default":true, "hunger_cost":1, "resource_cost":"$mana", "resource_cost_amount":3}
            """);
        var player = player(0);

        tick(player, holder, 1, 20);

        assertTrue(on(player, holder));
        assertEquals(16, player.getFoodData().getFoodLevel());
        assertEquals(0, mana(player));
    }

    @Test
    void freeTogglesRemainFreeAndReloadingWithoutCostsRemovesUpkeep() {
        holder(ID, "{\"default\":true, \"hunger_cost\":2, \"toggle_messages\":false}");
        var free = holder(ID, "{\"default\":true}");
        var player = player(0);
        player.getFoodData().setFoodLevel(0);

        tick(player, free, 1, 60);

        assertTrue(on(player, free));
        assertEquals(0, player.getFoodData().getFoodLevel());
        assertTrue(PowerUpkeep.forPower(ID).isFree());
        assertTrue(PowerUpkeep.sendsToggleMessages(ID));
        assertEquals(0, resyncs);
    }
}
