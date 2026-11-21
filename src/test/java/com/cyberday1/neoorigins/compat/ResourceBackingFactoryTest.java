package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.resource.ResourceBacking;
import com.cyberday1.neoorigins.api.resource.ResourceBar;
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

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

/** Factory-registered backings: one backing per bar, built from the bar's declared shape. */
class ResourceBackingFactoryTest {
    private static final String BACKING = "factorytest:pool";
    private static final String NATIVE = "factorytest:native_bar";
    private static final String COMPAT = "factorytest:compat_bar";

    /** A pool that remembers the bar it was built for and seeds start_value on grant. */
    static final class Pool implements ResourceBacking {
        final ResourceBar bar;
        int value;
        int max;
        Pool(ResourceBar bar) { this.bar = bar; this.max = bar.max(); }
        @Override public int read(ServerPlayer player) { return value; }
        @Override public void add(ServerPlayer player, int delta) { value = Math.min(max, value + delta); }
        @Override public int max(ServerPlayer player) { return max; }
        @Override public void onGranted(ServerPlayer player) { value = bar.startValue(); }
    }

    private final List<Pool> built = new ArrayList<>();
    private MockedStatic<PacketDistributor> packets;
    private MockedStatic<ContentTogglesConfig> toggles;

    @BeforeEach
    void rig() {
        packets = mockStatic(PacketDistributor.class);
        packets.when(() -> PacketDistributor.sendToPlayer(any(ServerPlayer.class), any(CustomPacketPayload.class), any(CustomPacketPayload[].class)))
            .thenAnswer(inv -> null);
        toggles = mockStatic(ContentTogglesConfig.class);
        NeoOriginsAPI.registerResourceBacking(Identifier.parse(BACKING), bar -> {
            Pool pool = new Pool(bar);
            built.add(pool);
            return pool;
        });
    }

    @AfterEach
    void tearDown() {
        ResourceBackingRouter.unregister(BACKING);
        ResourceBackingRouter.clearBars();
        ResourceBackingRouter.clearWarnings();
        CompatAttachments.clearResourceMeta();
        toggles.close();
        packets.close();
    }

    private static JsonObject json(String s) { return JsonParser.parseString(s).getAsJsonObject(); }

    private static PowerHolder<ResourcePower.Config> nativeBar() {
        var type = new ResourcePower();
        var config = type.codec().parse(JsonOps.INSTANCE, json("""
            {"_power_id":"%s","min":5,"max":40,"start_value":25,"backing":"%s",
             "max_action":{"type":"neoorigins:set_fall_distance","fall_distance":9}}
            """.formatted(NATIVE, BACKING))).getOrThrow();
        return new PowerHolder<>(Identifier.parse(NATIVE), type, config, Component.empty(), Component.empty());
    }

    @Test
    void eachBarGetsOneBackingBuiltFromItsDeclaredShape() {
        var power = nativeBar();
        ServerPlayer a = PlayerLifecycle.realPlayer();
        ServerPlayer b = PlayerLifecycle.realPlayer();
        power.onGranted(a);
        power.onGranted(b);
        power.onLogin(a);

        assertEquals(1, built.size(), "one backing per bar, however many holders set it up");
        assertEquals(new ResourceBar(Identifier.parse(NATIVE), 5, 40, 25), built.get(0).bar);
        assertEquals(25, ResourcePower.getValue(a, NATIVE), "onGranted applied start_value");
        assertFalse(a.getData(CompatAttachments.resourceState()).has(NATIVE));
        assertNull(ResourceBackingRouter.backing(BACKING), "a factory id has no shared instance");

        var compat = OriginsCompatPowerLoader.compileForTest(Identifier.parse(COMPAT), "origins:resource",
            json("{\"type\":\"origins:resource\",\"min\":0,\"max\":12,\"start_value\":3,\"backing\":\"" + BACKING + "\"}"));
        assertNotNull(compat);
        assertEquals(2, built.size());
        assertEquals(new ResourceBar(Identifier.parse(COMPAT), 0, 12, 3), built.get(1).bar);
        assertNotSame(built.get(0), built.get(1));
    }

    @Test
    void aReloadRebuildsTheBacking() {
        var power = nativeBar();
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        power.onGranted(sp);
        ResourceBackingRouter.clearBars();
        CompatAttachments.clearResourceMeta();
        power.onLogin(sp);
        assertEquals(2, built.size());
        assertEquals(0, ResourcePower.getValue(sp, NATIVE), "a rebuilt backing is not re-granted");
    }

    @Test
    void maxActionUsesTheLiveBackingMax() {
        var power = nativeBar();
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        power.onGranted(sp);
        Pool pool = built.get(0);
        pool.max = 60;
        sp.fallDistance = 0;
        power.onTick(sp);
        pool.value = 40;
        power.onTick(sp);
        assertEquals(0, sp.fallDistance, "the declared max is not the top of a backed bar");
        pool.value = 60;
        power.onTick(sp);
        assertEquals(9, sp.fallDistance);
    }

    @Test
    void aFailingFactoryReadsEmptyAndIdsAreShared() {
        ResourceBackingRouter.unregister(BACKING);
        NeoOriginsAPI.registerResourceBacking(Identifier.parse(BACKING),
            (java.util.function.Function<ResourceBar, ResourceBacking>) bar -> { throw new IllegalStateException("nope"); });
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        nativeBar().onGranted(sp);
        assertTrue(ResourceBackingRouter.isBacked(NATIVE));
        assertEquals(7, ResourceBackingRouter.read(sp, NATIVE, 7));
        assertFalse(sp.getData(CompatAttachments.resourceState()).has(NATIVE));
        assertThrows(IllegalStateException.class, () -> NeoOriginsAPI.registerResourceBacking(
            Identifier.parse(BACKING), new ResourceBackingRegistryTest.Pool()));
        assertThrows(IllegalStateException.class, () -> NeoOriginsAPI.registerResourceBacking(
            Identifier.parse(CompatAttachments.BACKING_IRONS_MANA), bar -> new Pool(bar)));
    }
}
