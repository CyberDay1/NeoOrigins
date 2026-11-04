package com.cyberday1.neoorigins.api;

import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The resource reads and writes on {@link NeoOriginsAPI}. */
class ResourceApiTest {

    private static final Identifier ENERGY = Identifier.fromNamespaceAndPath("test", "energy");
    private static final Identifier MANA = Identifier.fromNamespaceAndPath("test", "mana");

    @AfterEach
    void clearRegistries() {
        CompatAttachments.clearResourceMeta();
    }

    @Test
    void aMissingResourceReadsEmptyNotZero() {
        ServerPlayer sp = player();
        assertFalse(NeoOriginsAPI.hasResource(sp, ENERGY));
        assertEquals(OptionalInt.empty(), NeoOriginsAPI.getResource(sp, ENERGY));
    }

    @Test
    void aStoredResourceReadsItsValue() {
        ServerPlayer sp = player();
        sp.getData(CompatAttachments.resourceState()).set(ENERGY.toString(), 12);
        assertTrue(NeoOriginsAPI.hasResource(sp, ENERGY));
        assertEquals(OptionalInt.of(12), NeoOriginsAPI.getResource(sp, ENERGY));
    }

    @Test
    void changeStaysWithinTheBarAndSetDoesNot() {
        ServerPlayer sp = player();
        registerBar(ENERGY, 0, 20);
        NeoOriginsAPI.setResource(sp, ENERGY, 15);

        NeoOriginsAPI.changeResource(sp, ENERGY, 10);
        assertEquals(OptionalInt.of(20), NeoOriginsAPI.getResource(sp, ENERGY), "change clamps to the max");
        NeoOriginsAPI.changeResource(sp, ENERGY, -50);
        assertEquals(OptionalInt.of(0), NeoOriginsAPI.getResource(sp, ENERGY), "change clamps to the min");

        NeoOriginsAPI.setResource(sp, ENERGY, 50);
        assertEquals(OptionalInt.of(50), NeoOriginsAPI.getResource(sp, ENERGY), "set does not clamp, as set_resource");
    }

    @Test
    void spendTakesNothingWhenThereIsNotEnough() {
        ServerPlayer sp = player();
        registerBar(ENERGY, 0, 20);
        NeoOriginsAPI.setResource(sp, ENERGY, 10);

        assertTrue(NeoOriginsAPI.spendResource(sp, ENERGY, 4));
        assertEquals(OptionalInt.of(6), NeoOriginsAPI.getResource(sp, ENERGY));
        assertFalse(NeoOriginsAPI.spendResource(sp, ENERGY, 7));
        assertEquals(OptionalInt.of(6), NeoOriginsAPI.getResource(sp, ENERGY), "a failed spend takes nothing");
    }

    /** Iron's is absent here, so the mana pool is unusable: the write must still skip the store. */
    @Test
    void aBackedResourceIsNotWrittenToTheStore() {
        ServerPlayer sp = player();
        registerBar(MANA, 0, 100);
        CompatAttachments.registerResourceBacking(MANA.toString(), CompatAttachments.BACKING_IRONS_MANA);
        sp.getData(CompatAttachments.resourceState()).set(MANA.toString(), 42);

        NeoOriginsAPI.changeResource(sp, MANA, 10);
        NeoOriginsAPI.setResource(sp, MANA, 3);
        assertEquals(42, sp.getData(CompatAttachments.resourceState()).get(MANA.toString(), 0),
            "a backed resource's writes go to its pool, never the stored value");
    }

    private static ServerPlayer player() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        return sp;
    }

    private static void registerBar(Identifier id, int min, int max) {
        CompatAttachments.registerResourceMeta(id.toString(),
            new CompatAttachments.ResourceMeta(min, max, "Bar", 0xFF55AAFF, false, null, 0, false));
    }
}
