package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.command.OriginCommand;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
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

        // Register custom power type registry
        PowerTypes.register(modEventBus);

        // Register attachment types
        OriginAttachments.register(modEventBus);

        // Register network payloads
        modEventBus.addListener(NeoOriginsNetwork::register);

        // Register data reload listeners (on NeoForge event bus)
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(NeoOrigins::onServerStarting);
    }

    private static void onAddReloadListeners(net.neoforged.neoforge.event.AddReloadListenerEvent event) {
        event.addListener(OriginDataManager.INSTANCE);
        event.addListener(LayerDataManager.INSTANCE);
        event.addListener(PowerDataManager.INSTANCE);
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
