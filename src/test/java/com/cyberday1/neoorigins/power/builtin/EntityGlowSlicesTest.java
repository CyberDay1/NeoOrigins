package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.condition.TargetCondition;
import com.cyberday1.neoorigins.network.payload.SyncGlowingEntitiesPayload;
import com.cyberday1.neoorigins.network.payload.SyncHiddenEntitiesPayload;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.network.PacketDistributor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * entity_glow and prevent_entity_render: several powers on one player share the
 * client's one set (#144), and a power_condition that turns off clears it (#143).
 */
class EntityGlowSlicesTest {

    private static final int RED = 0xFF0000;
    private static final int GREEN = 0x00FF00;

    private final List<CustomPacketPayload> sent = new ArrayList<>();
    private MockedStatic<PacketDistributor> packets;
    private ServerPlayer player;
    private LivingEntity first;
    private LivingEntity second;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void rig() {
        packets = Mockito.mockStatic(PacketDistributor.class);
        packets.when(() -> PacketDistributor.sendToPlayer(any(ServerPlayer.class), any(CustomPacketPayload.class), any(CustomPacketPayload[].class)))
            .thenAnswer(inv -> { sent.add(inv.getArgument(1)); return null; });
        player = mock(ServerPlayer.class, Mockito.RETURNS_DEEP_STUBS);
        ServerLevel level = mock(ServerLevel.class, Mockito.RETURNS_DEEP_STUBS);
        when(player.level()).thenReturn(level);
        when(player.getUUID()).thenReturn(UUID.randomUUID());
        when(player.getBoundingBox()).thenReturn(new AABB(0, 0, 0, 1, 1, 1));
        when(level.getServer().getPlayerList().getViewDistance()).thenReturn(4);
        first = living(1);
        second = living(2);
        when(level.getEntities(any(net.minecraft.world.entity.Entity.class), any(AABB.class))).thenReturn(List.of(first, second));
        when(level.getEntity(1)).thenReturn(first);
        when(level.getEntity(2)).thenReturn(second);
    }

    @AfterEach
    void tearDown() {
        EntityGlowPower.clearPlayer(player.getUUID());
        PreventEntityRenderPower.clearPlayer(player.getUUID());
        packets.close();
    }

    private static LivingEntity living(int id) {
        LivingEntity e = mock(LivingEntity.class);
        when(e.getId()).thenReturn(id);
        return e;
    }

    private static TargetCondition only(LivingEntity target) {
        return (candidate, actor) -> candidate == target;
    }

    private static PowerHolder<?> glow(String id, LivingEntity target, int rgb, boolean[] gate) {
        var selection = new PreventEntityRenderPower.Config(Optional.of(only(target)), "neoorigins:entity_glow");
        var config = new EntityGlowPower.Config(selection, Optional.empty(), false,
            ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        return new PowerHolder<>(Identifier.parse(id), new EntityGlowPower(), config,
            Component.empty(), Component.empty(), false, gate == null ? null : p -> gate[0],
            PowerHolder.ConditionMode.ALLOW);
    }

    private static PowerHolder<?> hide(String id, LivingEntity target) {
        return new PowerHolder<>(Identifier.parse(id), new PreventEntityRenderPower(),
            new PreventEntityRenderPower.Config(Optional.of(only(target)), "neoorigins:prevent_entity_render"),
            Component.empty(), Component.empty());
    }

    private Map<Integer, Integer> lastGlow() {
        return ((SyncGlowingEntitiesPayload) sent.get(sent.size() - 1)).colorsById();
    }

    private Set<Integer> lastHidden() {
        return new HashSet<>(((SyncHiddenEntitiesPayload) sent.get(sent.size() - 1)).entityIds());
    }

    @Test
    void twoGlowPowersMergeInsteadOfReplacingEachOther() {
        var red = glow("test:red", first, RED, null);
        var green = glow("test:green", second, GREEN, null);
        red.onTick(player);
        green.onTick(player);
        assertEquals(Map.of(1, RED, 2, GREEN), lastGlow());

        int before = sent.size();
        red.onTick(player);
        green.onTick(player);
        assertEquals(before, sent.size(), "an unchanged merge sends nothing");

        red.onRevoked(player);
        assertEquals(Map.of(2, GREEN), lastGlow(), "revoking one power keeps the other's outlines");
    }

    @Test
    void aBlockedConditionClearsThatPowersOutlines() {
        boolean[] gate = {true};
        var power = glow("test:gated", first, RED, gate);
        power.onTick(player);
        assertEquals(Map.of(1, RED), lastGlow());

        gate[0] = false;
        power.onTick(player);
        assertEquals(Map.of(), lastGlow());

        int before = sent.size();
        power.onTick(player);
        assertEquals(before, sent.size(), "a power that stays blocked sends nothing more");
    }

    @Test
    void aBlockedConditionLeavesOtherGlowPowersAlone() {
        boolean[] gate = {true};
        var gated = glow("test:gated", first, RED, gate);
        var always = glow("test:always", second, GREEN, null);
        gated.onTick(player);
        always.onTick(player);
        gate[0] = false;
        gated.onTick(player);
        assertEquals(Map.of(2, GREEN), lastGlow());
    }

    @Test
    void twoHidePowersShareTheHiddenSet() {
        var a = hide("test:hide_a", first);
        var b = hide("test:hide_b", second);
        a.onTick(player);
        b.onTick(player);
        assertEquals(Set.of(1, 2), lastHidden());
        a.onRevoked(player);
        assertEquals(Set.of(2), lastHidden());
    }

    @Test
    void aReloadDropsSlicesOfPowersNoLongerHeld() {
        var red = glow("test:red", first, RED, null);
        var green = glow("test:green", second, GREEN, null);
        red.onTick(player);
        green.onTick(player);
        EntityGlowPower.retainHeld(player, id -> id.equals(Identifier.parse("test:green")));
        assertEquals(Map.of(2, GREEN), lastGlow());
    }
}
