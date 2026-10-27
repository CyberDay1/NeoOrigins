package com.cyberday1.neoorigins.rig;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.fml.config.IConfigSpec;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

/**
 * Loads a config spec at its defaults with no file behind it, so code that reads
 * config can run in a test. The harness already loads COMMON specs from disk;
 * SERVER specs stay unloaded. Unload only what {@link #loadIfAbsent} loaded.
 */
public final class InMemoryConfig {

    private InMemoryConfig() {}

    /** True when this call loaded {@code spec}; false when something already had. */
    public static boolean loadIfAbsent(ModConfigSpec spec) {
        if (spec.isLoaded()) return false;
        CommentedConfig config = CommentedConfig.inMemory();
        // Corrected up front, so acceptConfig never calls save() on a config with no file.
        spec.correct(config);
        spec.acceptConfig(loaded(config));
        return true;
    }

    public static void unload(ModConfigSpec spec) {
        spec.acceptConfig(null);
    }

    /** ILoadedConfig is sealed to FML's package-private LoadedConfig record. */
    private static IConfigSpec.ILoadedConfig loaded(CommentedConfig config) {
        try {
            Class<?> type = Class.forName("net.neoforged.fml.config.LoadedConfig");
            Constructor<?> ctor = type.getDeclaredConstructor(CommentedConfig.class, Path.class, ModConfig.class);
            ctor.setAccessible(true);
            return (IConfigSpec.ILoadedConfig) ctor.newInstance(config, null, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not build an in-memory LoadedConfig", e);
        }
    }
}
