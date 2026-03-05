package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.compat.CompatAttachments;
import com.cyberday1.neoorigins.compat.OriginsCompatPowerLoader;
import com.cyberday1.neoorigins.command.OriginCommand;
import com.cyberday1.neoorigins.compat.OriginsPackFinder;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.mojang.logging.LogUtils;
import net.minecraft.server.packs.PackType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

@Mod(NeoOrigins.MOD_ID)
public class NeoOrigins {

    public static final String MOD_ID = "neoorigins";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoOrigins(IEventBus modEventBus) {
        LOGGER.info("NeoOrigins initializing...");

        // Create originpacks/ folder in game directory on first launch
        try {
            java.nio.file.Files.createDirectories(FMLPaths.GAMEDIR.get().resolve("originpacks"));
        } catch (java.io.IOException e) {
            LOGGER.error("Failed to create originpacks/ folder", e);
        }

        // Register custom power type registry
        PowerTypes.register(modEventBus);

        // Register attachment types (origin data + Route B compat state)
        OriginAttachments.register(modEventBus);
        CompatAttachments.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(NeoOriginsNetwork::register);

        // Register client-only keybindings
        if (FMLEnvironment.getDist() == Dist.CLIENT) {
            modEventBus.addListener(com.cyberday1.neoorigins.client.NeoOriginsKeybindings::onRegisterKeyMappings);
        }

        // Register the originpacks/ folder as a datapack source (mod event bus)
        modEventBus.addListener(NeoOrigins::onAddPackFinders);

        // Register data reload listeners (on NeoForge event bus)
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onServerStarting);
    }

    private static void onAddPackFinders(AddPackFindersEvent event) {
        var folder = FMLPaths.GAMEDIR.get().resolve("originpacks");
        // Register for both sides so packs' assets/ directories (textures, models, sprites)
        // are mounted client-side in addition to their data/ directories server-side.
        if (event.getPackType() == PackType.SERVER_DATA || event.getPackType() == PackType.CLIENT_RESOURCES) {
            event.addRepositorySource(new OriginsPackFinder(folder));
            LOGGER.info("Registered originpacks/ for {} at {}", event.getPackType(), folder);
        }
    }

    private static void onAddReloadListeners(net.neoforged.neoforge.event.AddServerReloadListenersEvent event) {
        // Load order matters:
        //   1. power_data         — native Route A powers + compat translation
        //   2. origins_compat_b   — Route B powers injected into PowerDataManager
        //   3. origin_data        — reads MULTIPLE_EXPANSION_MAP (now includes Route B IDs); closes log
        //   4. layer_data
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "power_data"),       PowerDataManager.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "origins_compat_b"), OriginsCompatPowerLoader.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "origin_data"),      OriginDataManager.INSTANCE);
        event.addListener(net.minecraft.resources.Identifier.fromNamespaceAndPath(MOD_ID, "layer_data"),       LayerDataManager.INSTANCE);
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
