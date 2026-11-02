package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.NeoOriginsAPI;
import com.cyberday1.neoorigins.api.power.GrantPowerResult;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerDisplay;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.rig.InMemoryConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.cyberday1.neoorigins.rig.RealDatapack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Powers other mods register in code: lookup, granting, duplicates, and surviving reloads. */
class CodePowerRegistryTest {

    private static final Identifier CODE_HP = Identifier.fromNamespaceAndPath("testmod", "code_hp");
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath("testmod", "source");
    /** A shipped +6 max health power, borrowed for its type and config. */
    private static final Identifier TEMPLATE = Identifier.fromNamespaceAndPath("neoorigins", "abyssal_apex_hp");

    private static boolean loadedAdmin;
    private static boolean loadedToggles;

    @BeforeAll
    static void loadWorld() {
        RealDatapack.load();
        loadedAdmin = InMemoryConfig.loadIfAbsent(AdminConfig.SPEC);
        loadedToggles = InMemoryConfig.loadIfAbsent(ContentTogglesConfig.SPEC);
    }

    @AfterAll
    static void unloadConfigs() {
        if (loadedAdmin) InMemoryConfig.unload(AdminConfig.SPEC);
        if (loadedToggles) InMemoryConfig.unload(ContentTogglesConfig.SPEC);
    }

    @AfterEach
    void unregister() {
        PowerDataManager.INSTANCE.unregisterCodePower(CODE_HP);
    }

    @SuppressWarnings("unchecked")
    private static <C extends PowerConfiguration> void registerFromTemplate(Identifier id) {
        PowerHolder<C> template = (PowerHolder<C>) PowerDataManager.INSTANCE.getPower(TEMPLATE);
        NeoOriginsAPI.registerPower(id, (PowerType<C>) template.type(), template.config(),
            PowerDisplay.of(Component.literal("Code HP"), Component.literal("Registered in code")));
    }

    @Test
    void aRegisteredPowerResolvesSyncsAndCanBeGranted() {
        registerFromTemplate(CODE_HP);
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(CODE_HP);
        assertNotNull(holder);
        assertEquals("Code HP", holder.name().getString());
        assertTrue(PowerDataManager.INSTANCE.getAllPowers().containsKey(CODE_HP), "the client registry sync reads getAllPowers");

        ServerPlayer sp = PlayerLifecycle.realPlayer();
        sp.connection = Mockito.mock(ServerGamePacketListenerImpl.class);
        assertEquals(GrantPowerResult.GRANTED, NeoOriginsAPI.grantPower(sp, CODE_HP, SOURCE));
        assertEquals(26.0, sp.getMaxHealth(), 1e-6);
    }

    @Test
    void anIdCanOnlyBeRegisteredOnce() {
        registerFromTemplate(CODE_HP);
        assertThrows(IllegalStateException.class, () -> registerFromTemplate(CODE_HP));
    }

    @Test
    void aReloadKeepsItAndADatapackPowerWithTheSameIdWins() throws Exception {
        registerFromTemplate(CODE_HP);
        Field injected = PowerDataManager.class.getDeclaredField("injectedPowers");
        injected.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Identifier, PowerHolder<?>> prior = (Map<Identifier, PowerHolder<?>>) injected.get(PowerDataManager.INSTANCE);
        try {
            // Route B re-injects wholesale on every reload.
            PowerDataManager.INSTANCE.injectExternalPowers(Map.of());
            assertNotNull(PowerDataManager.INSTANCE.getPower(CODE_HP), "a reload dropped the code-registered power");

            PowerHolder<?> datapack = PowerDataManager.INSTANCE.getPower(TEMPLATE);
            PowerDataManager.INSTANCE.injectExternalPowers(Map.of(CODE_HP, datapack));
            assertSame(datapack, PowerDataManager.INSTANCE.getPower(CODE_HP), "a datapack power must override the code one");
        } finally {
            PowerDataManager.INSTANCE.injectExternalPowers(prior);
        }
    }
}
