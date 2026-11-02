package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Addon-registered resource backings: a bar naming one reads and writes that pool. */
class ResourceBackingRegistryTest {

    /** A pool held in a field, with switches for the edge cases. */
    static final class Pool implements ResourceBacking {
        int value;
        int max;
        boolean up = true;
        boolean settable;
        boolean broken;

        @Override public boolean available() { return up; }
        @Override public int read(ServerPlayer player) {
            if (broken) throw new IllegalStateException("broken pool");
            return value;
        }
        @Override public void add(ServerPlayer player, int delta) {
            if (broken) throw new IllegalStateException("broken pool");
            value += delta;
        }
        @Override public int max(ServerPlayer player) { return max; }
        @Override public boolean set(ServerPlayer player, int v) {
            if (!settable) return false;
            value = v;
            return true;
        }
    }

    private static int n = 0;
    private final ServerPlayer sp = PlayerLifecycle.realPlayer();

    /** Registers a fresh pool under a unique id and a bar named after it that uses it. */
    private static String bar(Pool pool) {
        String id = "testmod:pool_" + (n++);
        NeoOriginsAPI.registerResourceBacking(Identifier.parse(id), pool);
        String key = id + "_bar";
        CompatAttachments.registerResourceBacking(key, id);
        CompatAttachments.registerResourceMeta(key,
            new CompatAttachments.ResourceMeta(0, 50, "Pool", 0xFFFFFFFF, false, null, 0, false));
        return key;
    }

    @AfterEach
    void reset() {
        CompatAttachments.clearResourceMeta();
        ResourceBackingRouter.clearWarnings();
    }

    @Test
    void aBarReadsAndSpendsItsRegisteredPool() {
        Pool pool = new Pool();
        pool.value = 30;
        String key = bar(pool);

        assertTrue(ResourceBackingRouter.isBacked(key));
        assertEquals(30, ResourcePower.getValue(sp, key));
        assertTrue(ResourcePower.deduct(sp, key, 12));
        assertEquals(18, pool.value);
        assertFalse(ResourcePower.deduct(sp, key, 19), "cannot spend more than the pool holds");
        assertEquals(18, pool.value);
        assertFalse(sp.getData(CompatAttachments.resourceState()).has(key), "no internal value is stored");
    }

    @Test
    void theLiveMaxFallsBackToTheDeclaredMax() {
        Pool pool = new Pool();
        String key = bar(pool);
        pool.max = 80;
        assertEquals(80, ResourceBackingRouter.maxValue(sp, key, 50));
        pool.max = 0;
        assertEquals(50, ResourceBackingRouter.maxValue(sp, key, 50));
    }

    @Test
    void anAbsoluteSetGoesThroughOnlyIfTheBackingTakesIt() {
        Pool pool = new Pool();
        pool.value = 10;
        String key = bar(pool);

        assertTrue(ResourceBackingRouter.handleAbsoluteSet(sp, key, 40), "a backed key is always handled");
        assertEquals(10, pool.value, "an additive-only pool ignores the set");
        pool.settable = true;
        ResourceBackingRouter.handleAbsoluteSet(sp, key, 40);
        assertEquals(40, pool.value);
    }

    @Test
    void anUnavailableOrBrokenBackingReadsTheFallbackAndDropsWrites() {
        Pool pool = new Pool();
        pool.value = 30;
        String key = bar(pool);

        pool.up = false;
        assertEquals(7, ResourceBackingRouter.read(sp, key, 7));
        assertTrue(ResourceBackingRouter.add(sp, key, 5), "still handled: never falls back to the internal store");
        assertEquals(30, pool.value);

        pool.up = true;
        pool.broken = true;
        assertEquals(7, ResourceBackingRouter.read(sp, key, 7), "a throwing backing must not escape");
        assertTrue(ResourceBackingRouter.add(sp, key, 5));
        assertFalse(sp.getData(CompatAttachments.resourceState()).has(key));
    }

    @Test
    void anUnregisteredBackingIdLeavesTheBarInternallyStored() {
        CompatAttachments.registerResourceBacking("testmod:orphan_bar", "nobody:registers_this");
        assertFalse(ResourceBackingRouter.isBacked("testmod:orphan_bar"));
        assertFalse(ResourceBackingRouter.add(sp, "testmod:orphan_bar", 5));
    }

    @Test
    void anIdCanOnlyBeRegisteredOnce() {
        Pool pool = new Pool();
        NeoOriginsAPI.registerResourceBacking(Identifier.parse("testmod:once"), pool);
        assertThrows(IllegalStateException.class,
            () -> NeoOriginsAPI.registerResourceBacking(Identifier.parse("testmod:once"), new Pool()));
        assertThrows(IllegalStateException.class,
            () -> NeoOriginsAPI.registerResourceBacking(Identifier.parse(CompatAttachments.BACKING_IRONS_MANA), new Pool()),
            "the built-in Iron's mana backing cannot be replaced");
        assertNotNull(ResourceBackingRouter.backing(CompatAttachments.BACKING_IRONS_MANA));
    }
}
