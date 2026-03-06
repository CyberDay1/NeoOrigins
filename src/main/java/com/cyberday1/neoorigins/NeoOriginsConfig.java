package com.cyberday1.neoorigins;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NeoForge TOML config for NeoOrigins.
 * Stored at config/neoorigins-common.toml in the game directory.
 */
public final class NeoOriginsConfig {

    private NeoOriginsConfig() {}

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DEBUG_POWER_LOADING =
        BUILDER
            .comment("Log per-namespace power counts after each data reload.",
                     "Useful for addon and datapack authors debugging load issues.")
            .define("debug_power_loading", false);

    public static final ModConfigSpec SPEC = BUILDER.build();
}
