package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.api.power.PowerHolder;
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

/** power_active honours a power type's own {@code condition}, as Apoli's isActive does. */
class PowerActiveTypeConditionTest {
    private static final ResourceLocation GATED = ResourceLocation.parse("typecond:gated");
    private static final ResourceLocation BARE = ResourceLocation.parse("typecond:bare");
    private static final ResourceLocation FIXED = ResourceLocation.parse("typecond:fixed");
    private static final ResourceLocation PASSIVE = ResourceLocation.parse("typecond:passive");
    private static final ResourceLocation BRANCH = ResourceLocation.parse("typecond:branch");
    private static final ResourceLocation AURA = ResourceLocation.parse("typecond:aura");
    @TempDir Path pack;
    private MockedStatic<ActiveOriginService> service;
    private final List<PowerHolder<?>> granted = new ArrayList<>();

    @BeforeEach
    void setup() throws Exception {
        Path powers = Files.createDirectories(pack.resolve("data/typecond/origins/powers"));
        String sneak = "\"condition\":{\"type\":\"neoorigins:sneaking\"}";
        Files.writeString(powers.resolve("gated.json"), """
            {"type":"neoorigins:persistent_effect","toggleable":true,%s,
             "effects":[{"effect":"minecraft:slowness","amplifier":0}]}
            """.formatted(sneak));
        Files.writeString(powers.resolve("bare.json"), """
            {"type":"neoorigins:persistent_effect","toggleable":true,
             "effects":[{"effect":"minecraft:speed","amplifier":0}]}
            """);
        Files.writeString(powers.resolve("fixed.json"), """
            {"type":"neoorigins:persistent_effect","toggleable":false,%s,
             "effects":[{"effect":"minecraft:haste","amplifier":0}]}
            """.formatted(sneak));
        Files.writeString(powers.resolve("passive.json"), """
            {"type":"neoorigins:condition_passive","toggleable":true,%s,
             "entity_action":{"type":"neoorigins:extinguish"}}
            """.formatted(sneak));
        Files.writeString(powers.resolve("branch.json"), """
            {"type":"neoorigins:condition_passive","toggleable":true,%s,
             "entity_action":{"type":"neoorigins:extinguish"},
             "else_action":{"type":"neoorigins:extinguish"}}
            """.formatted(sneak));
        Files.writeString(powers.resolve("aura.json"), """
            {"type":"neoorigins:effect_over_time","toggleable":true,%s,
             "entity_action":{"type":"neoorigins:extinguish"}}
            """.formatted(sneak));
        RealDatapack.loadWith(pack);
        service = Mockito.mockStatic(ActiveOriginService.class, Answers.CALLS_REAL_METHODS);
        service.when(() -> ActiveOriginService.allPowers(Mockito.any())).thenAnswer(i -> List.copyOf(granted));
    }

    @AfterEach
    void cleanup() {
        service.close();
        RealDatapack.restoreShipped();
    }

    private static boolean active(ServerPlayer player, ResourceLocation id) {
        return ConditionParser.parse(JsonParser.parseString("""
            {"type":"neoorigins:power_active","power":"%s"}
            """.formatted(id)).getAsJsonObject(), "type-condition-test").test(player);
    }

    private ServerPlayer grantedPlayer(ResourceLocation... ids) {
        var player = PlayerLifecycle.realPlayer();
        player.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        for (var id : ids) {
            var holder = PowerDataManager.INSTANCE.getPower(id);
            granted.add(holder);
            PlayerLifecycle.grant(holder, player);
        }
        return player;
    }

    @Test
    void toggledOnFollowsTheTypeCondition() {
        var player = grantedPlayer(GATED);
        player.setShiftKeyDown(false);
        assertFalse(active(player, GATED), "on, condition false");
        player.setShiftKeyDown(true);
        assertTrue(active(player, GATED), "on, condition true");
    }

    @Test
    void toggledOffReadsOffEvenWhenTheConditionHolds() {
        var player = grantedPlayer(GATED);
        player.setShiftKeyDown(true);
        PowerDataManager.INSTANCE.getPower(GATED).onActivated(player);
        assertFalse(active(player, GATED));
    }

    @Test
    void noTypeConditionIsUnchanged() {
        var player = grantedPlayer(BARE);
        player.setShiftKeyDown(false);
        assertTrue(active(player, BARE));
    }

    @Test
    void untoggleablePowerAlsoNeedsItsCondition() {
        var player = grantedPlayer(FIXED);
        player.setShiftKeyDown(false);
        assertFalse(active(player, FIXED));
        player.setShiftKeyDown(true);
        assertTrue(active(player, FIXED));
    }

    @Test
    void conditionPassiveAndAuraGateOnABareCondition() {
        var player = grantedPlayer(PASSIVE, AURA);
        player.setShiftKeyDown(false);
        assertFalse(active(player, PASSIVE));
        assertFalse(active(player, AURA));
        player.setShiftKeyDown(true);
        assertTrue(active(player, PASSIVE));
        assertTrue(active(player, AURA));
    }

    @Test
    void elseActionKeepsTheConditionABranchNotAGate() {
        var player = grantedPlayer(BRANCH);
        player.setShiftKeyDown(false);
        assertTrue(active(player, BRANCH));
    }
}
