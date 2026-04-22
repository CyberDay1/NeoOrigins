package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.content.ModItems;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.cyberday1.neoorigins.command.OriginCommand;
import com.cyberday1.neoorigins.compat.OriginsPackFinder;
import com.cyberday1.neoorigins.compat.PackItemAutoRegistrar;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

@Mod(NeoOrigins.MOD_ID)
public class NeoOrigins {

    public static final String MOD_ID = "neoorigins";
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Canonical location for origin-packs as of NeoOrigins 2.0: {@code config/originpacks/}.
     * Legacy installs put the folder at {@code originpacks/} in the game root;
     * {@link #resolveOriginpacksDir()} falls back to the legacy path when the new one
     * is absent so existing setups keep loading without manual intervention.
     */
    public static java.nio.file.Path resolveOriginpacksDir() {
        java.nio.file.Path configDir = FMLPaths.CONFIGDIR.get().resolve("originpacks");
        if (java.nio.file.Files.exists(configDir)) return configDir;
        java.nio.file.Path legacy = FMLPaths.GAMEDIR.get().resolve("originpacks");
        if (java.nio.file.Files.exists(legacy)) {
            if (LEGACY_WARNED.compareAndSet(false, true)) {
                LOGGER.warn("[originpacks] Legacy 'originpacks/' folder found at game root. "
                    + "Please move it to 'config/originpacks/' — the game-root location is deprecated "
                    + "and will be removed in a future release.");
            }
            return legacy;
        }
        return configDir;
    }

    private static final java.util.concurrent.atomic.AtomicBoolean LEGACY_WARNED =
        new java.util.concurrent.atomic.AtomicBoolean(false);

    public NeoOrigins(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("NeoOrigins initializing...");

        // Register TOML config (config/neoorigins-common.toml)
        modContainer.registerConfig(ModConfig.Type.COMMON, NeoOriginsConfig.SPEC);

        // Create config/originpacks/ folder on first launch. If a legacy
        // originpacks/ folder exists at the game root it will still be picked
        // up by resolveOriginpacksDir() for back-compat.
        try {
            java.nio.file.Files.createDirectories(FMLPaths.CONFIGDIR.get().resolve("originpacks"));
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to create config/originpacks/ folder", e);
        }

        // Register custom power type registry
        PowerTypes.register(modEventBus);

        // 2.0 — bootstrap legacy power-type aliases so old JSON still loads.
        com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases.bootstrap();

        // Register mod items (Orb of Origin, etc.)
        ModItems.register(modEventBus);

        // Register attachment types (origin data + Route B compat state)
        OriginAttachments.register(modEventBus);
        CompatAttachments.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(NeoOriginsNetwork::register);

        // Register client-only keybindings
        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsKeybindings::onRegisterKeyMappings);
        }

        // Auto-register items from originpacks/ before the registry freezes
        modEventBus.addListener(PackItemAutoRegistrar::onRegisterItems);

        // Register the originpacks/ folder as a datapack source (mod event bus)
        modEventBus.addListener(NeoOrigins::onAddPackFinders);

        // Register data reload listeners (on NeoForge event bus)
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onServerStarting);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var folder = resolveOriginpacksDir();
        if (event.getPackType() == PackType.SERVER_DATA || event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new OriginsPackFinder(folder));
            LOGGER.info("Registered originpacks/ for {} at {}", event.getPackType(), folder);
        }
    }

    private static void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        // Load order matters:
        //   1. power_data         — native Route A powers + compat translation
        //   2. origins_compat_b   — Route B powers injected into PowerDataManager
        //   3. origin_data        — reads MULTIPLE_EXPANSION_MAP (now includes Route B IDs); closes log
        //   4. layer_data
        event.addListener(PowerDataManager.INSTANCE);
        event.addListener(OriginsCompatPowerLoader.INSTANCE);
        event.addListener(OriginDataManager.INSTANCE);
        event.addListener(LayerDataManager.INSTANCE);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        OriginCommand.register(event.getDispatcher());
    }

    private static void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("NeoOrigins server starting — origins: {}, layers: {}, powers: {}",
            OriginDataManager.INSTANCE.getOrigins().size(),
            LayerDataManager.INSTANCE.getLayers().size(),
            PowerDataManager.INSTANCE.getPowers().size());
    }
}
