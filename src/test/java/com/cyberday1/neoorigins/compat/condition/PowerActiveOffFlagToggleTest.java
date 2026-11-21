package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.Toggles;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #153: power_active reads the off-flag of toggleable persistent_effect-style powers. */
class PowerActiveOffFlagToggleTest {
    private static final ResourceLocation GUARD = ResourceLocation.parse("toggletest:guard");
    private static final ResourceLocation ALWAYS = ResourceLocation.parse("toggletest:always");
    @TempDir Path pack;
    private MockedStatic<ActiveOriginService> service;
    private final List<PowerHolder<?>> granted = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        Path powers = Files.createDirectories(pack.resolve("data/toggletest/origins/powers"));
        Files.writeString(powers.resolve("guard.json"), """
            {"type":"neoorigins:persistent_effect","toggleable":true,"default_off":true,
             "effects":[{"effect":"minecraft:slowness","amplifier":0}]}
            """);
        Files.writeString(powers.resolve("always.json"), """
            {"type":"neoorigins:persistent_effect","toggleable":false,
             "effects":[{"effect":"minecraft:speed","amplifier":0}]}
            """);
        RealDatapack.loadWith(pack);
        service = Mockito.mockStatic(ActiveOriginService.class, Answers.CALLS_REAL_METHODS);
        service.when(() -> ActiveOriginService.allPowers(Mockito.any())).thenAnswer(i -> List.copyOf(granted));
    }

    @AfterEach
    void cleanup() {
        service.close();
        RealDatapack.restoreShipped();
    }

    private static boolean active(ServerPlayer player, ResourceLocation id, boolean inverted) {
        return ConditionParser.parse(JsonParser.parseString("""
            {"type":"neoorigins:power_active","power":"%s","inverted":%s}
            """.formatted(id, inverted)).getAsJsonObject(), "power-active-test").test(player);
    }

    private static ServerPlayer player() {
        var player = PlayerLifecycle.realPlayer();
        player.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return player;
    }

    @Test
    void defaultOffToggleReadsOffUntilSwitchedOn() {
        var holder = PowerDataManager.INSTANCE.getPower(GUARD);
        var player = player();
        granted.add(holder);
        PlayerLifecycle.grant(holder, player);

        assertFalse(active(player, GUARD, false));
        assertTrue(active(player, GUARD, true));
        assertFalse(Toggles.isOn(player, GUARD.toString()));

        holder.onActivated(player);
        assertTrue(active(player, GUARD, false));
        assertFalse(active(player, GUARD, true));
        assertTrue(Toggles.isOn(player, GUARD.toString()));

        holder.onActivated(player);
        assertFalse(active(player, GUARD, false));
    }

    @Test
    void switchedOnButNotGrantedReadsOff() {
        var holder = PowerDataManager.INSTANCE.getPower(GUARD);
        var player = player();
        PlayerLifecycle.grant(holder, player);
        holder.onActivated(player);
        assertFalse(active(player, GUARD, false));
    }

    @Test
    void untoggleablePowerStillReadsGrantedAndCondition() {
        var holder = PowerDataManager.INSTANCE.getPower(ALWAYS);
        var player = player();
        assertFalse(active(player, ALWAYS, false));
        granted.add(holder);
        PlayerLifecycle.grant(holder, player);
        assertTrue(active(player, ALWAYS, false));
    }

    @Test
    void shippedIronMonkGuardStartsLowered() {
        RealDatapack.restoreShipped();
        var guard = ResourceLocation.parse("neoorigins:iron_monk_guard");
        var holder = PowerDataManager.INSTANCE.getPower(guard);
        var player = player();
        granted.add(holder);
        PlayerLifecycle.grant(holder, player);
        assertFalse(active(player, guard, false));
        holder.onActivated(player);
        assertTrue(active(player, guard, false));
    }
}
