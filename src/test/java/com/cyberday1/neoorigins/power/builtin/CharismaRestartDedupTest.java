package com.cyberday1.neoorigins.power.builtin;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Charisma adds its trades once per player per trader. The record of who was
 * served used to live in memory, keyed on the entity id, so a restart forgot it
 * and the same trader (whose offers are saved) got another set on the next click.
 */
class CharismaRestartDedupTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void secondInteractionIsRefused() {
        WanderingTrader trader = traderWith(new CompoundTag());
        UUID player = UUID.randomUUID();
        assertTrue(RareWanderingLootPower.claimInjection(trader, player));
        assertFalse(RareWanderingLootPower.claimInjection(trader, player));
    }

    @Test
    void theRecordSurvivesASaveAndReload() throws IOException {
        CompoundTag data = new CompoundTag();
        UUID player = UUID.randomUUID();
        assertTrue(RareWanderingLootPower.claimInjection(traderWith(data), player));

        WanderingTrader reloaded = traderWith(roundTrip(data));
        assertFalse(RareWanderingLootPower.claimInjection(reloaded, player),
            "a reloaded trader injected Charisma trades again for the same player");
        assertTrue(RareWanderingLootPower.claimInjection(reloaded, UUID.randomUUID()),
            "another player should still get their own set");
    }

    private static WanderingTrader traderWith(CompoundTag persistentData) {
        WanderingTrader trader = Mockito.mock(WanderingTrader.class);
        Mockito.when(trader.getPersistentData()).thenReturn(persistentData);
        return trader;
    }

    private static CompoundTag roundTrip(CompoundTag tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        return NbtIo.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),
            NbtAccounter.unlimitedHeap());
    }
}
