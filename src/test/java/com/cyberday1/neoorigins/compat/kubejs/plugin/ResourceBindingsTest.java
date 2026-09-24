package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.CompatPower;
import com.cyberday1.neoorigins.compat.ResourceBackingRouter;
import com.cyberday1.neoorigins.compat.action.EntityAction;
import com.cyberday1.neoorigins.compat.condition.EntityCondition;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

/**
 * The {@code NeoOrigins.getResource} / {@code hasResource} script reads. The mana
 * cases plant a stored value the pool disagrees with, so a read that skips
 * {@code ResourcePower#getValue} for the attachment answers the wrong number.
 */
class ResourceBindingsTest {

    private static final String ENERGY = "test:energy";
    private static final String MANA = "test:mana";
    private static final NeoOriginsBindings JS = NeoOriginsBindings.INSTANCE;

    @AfterEach
    void clearRegistries() {
        CompatAttachments.clearResourceMeta();
    }

    @Test
    void storedResourceReadsItsValue() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.getData(CompatAttachments.resourceState()).set(ENERGY, 12);

        assertTrue(JS.hasResource(sp, ENERGY), "a stored resource must be present");
        assertEquals(12, JS.getResource(sp, ENERGY), "a stored resource must read its stored value");
    }

    @Test
    void missingResourceReadsNullNotZero() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();

        assertFalse(JS.hasResource(sp, ENERGY), "an ungranted resource must be absent");
        assertNull(JS.getResource(sp, ENERGY), "an ungranted resource must read null, not a value");
    }

    /** Iron's is absent here, so the real router answers the bar's min, never the stale stored 42. */
    @Test
    void manaBackedResourceIgnoresTheAttachment() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        registerManaBar(5);
        sp.getData(CompatAttachments.resourceState()).set(MANA, 42);

        assertEquals(5, JS.getResource(sp, MANA), "mana-backed read must route to the pool, not the attachment");
    }

    /** No stored entry at all: presence comes from the held power, the value from the pool. */
    @Test
    void manaBackedResourceReadsTheLivePool() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        registerManaBar(0);
        try (MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class);
             MockedStatic<ResourceBackingRouter> router = mockStatic(ResourceBackingRouter.class, CALLS_REAL_METHODS)) {
            origins.when(() -> ActiveOriginService.allPowers(sp)).thenReturn(List.of(manaHolder()));
            router.when(() -> ResourceBackingRouter.read(any(ServerPlayer.class), eq(MANA), anyInt())).thenReturn(37);

            assertTrue(JS.hasResource(sp, MANA), "a held mana-backed bar must be present");
            assertEquals(37, JS.getResource(sp, MANA), "a mana-backed bar must read the live pool");
        }
    }

    /** An Apoli {@code origins:resource} may declare a backing too, and is held as a compat power. */
    @Test
    void manaBackedCompatResourceReadsTheLivePool() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        registerManaBar(0);
        var compat = new PowerHolder<>(ResourceLocation.parse(MANA), CompatPower.INSTANCE,
            CompatPower.Config.builder().build(), Component.empty(), Component.empty());
        try (MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class);
             MockedStatic<ResourceBackingRouter> router = mockStatic(ResourceBackingRouter.class, CALLS_REAL_METHODS)) {
            origins.when(() -> ActiveOriginService.allPowers(sp)).thenReturn(List.of(compat));
            router.when(() -> ResourceBackingRouter.read(any(ServerPlayer.class), eq(MANA), anyInt())).thenReturn(37);

            assertTrue(JS.hasResource(sp, MANA), "a held mana-backed Apoli resource must be present");
            assertEquals(37, JS.getResource(sp, MANA), "a mana-backed Apoli resource must read the live pool");
        }
    }

    /** The backing registry is global, so another player's bar must not answer with this player's mana. */
    @Test
    void manaBackedResourceNotHeldReadsNull() {
        ServerPlayer sp = PlayerLifecycle.realPlayer();
        registerManaBar(0);
        try (MockedStatic<ActiveOriginService> origins = mockStatic(ActiveOriginService.class);
             MockedStatic<ResourceBackingRouter> router = mockStatic(ResourceBackingRouter.class, CALLS_REAL_METHODS)) {
            origins.when(() -> ActiveOriginService.allPowers(sp)).thenReturn(List.of());
            router.when(() -> ResourceBackingRouter.read(any(ServerPlayer.class), eq(MANA), anyInt())).thenReturn(37);

            assertFalse(JS.hasResource(sp, MANA), "an unheld mana-backed bar must be absent");
            assertNull(JS.getResource(sp, MANA), "an unheld mana-backed bar must read null");
        }
    }

    /** Rhino exposes public instance methods of the bound object; pin the shape scripts call. */
    @Test
    void readsArePublicInstanceMethods() throws NoSuchMethodException {
        for (String name : List.of("getResource", "hasResource")) {
            Method m = NeoOriginsBindings.class.getMethod(name, ServerPlayer.class, String.class);
            assertFalse(Modifier.isStatic(m.getModifiers()), name + " must be an instance method");
        }
    }

    private static void registerManaBar(int min) {
        CompatAttachments.registerResourceBacking(MANA, CompatAttachments.BACKING_IRONS_MANA);
        CompatAttachments.registerResourceMeta(MANA,
            new CompatAttachments.ResourceMeta(min, 100, "Mana", 0xFF55AAFF, false, null, 0, false));
    }

    private static PowerHolder<ResourcePower.Config> manaHolder() {
        EntityCondition always = p -> true;
        EntityAction none = p -> {};
        var config = new ResourcePower.Config(MANA, 0, 100, 0, 0, 20, always, none, none,
            "Mana", 0xFF55AAFF, false, null, 0, false, "neoorigins:resource", CompatAttachments.BACKING_IRONS_MANA);
        return new PowerHolder<>(ResourceLocation.parse(MANA), new ResourcePower(), config,
            Component.empty(), Component.empty());
    }
}
