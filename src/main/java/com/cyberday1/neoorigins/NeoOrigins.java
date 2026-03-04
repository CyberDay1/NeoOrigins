package com.cyberday1.neoorigins;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(NeoOrigins.MOD_ID)
public class NeoOrigins {

    public static final String MOD_ID = "neoorigins";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoOrigins(IEventBus modEventBus) {
        LOGGER.info("NeoOrigins initializing...");
    }
}
