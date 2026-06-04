package com.cyberday1.neoorigins.compat.top;

import mcjty.theoneprobe.api.ITheOneProbe;
import net.neoforged.fml.InterModComms;

import java.util.function.Function;

/**
 * IMC bridge to The One Probe.
 *
 * <p>Confines every TOP-typed reference to this {@code compat/top} package so a
 * missing TOP never triggers {@code NoClassDefFoundError} in the mod's main
 * class. {@code NeoOrigins} calls {@link #enqueueImc()} only after a
 * {@code ModList.isLoaded("theoneprobe")} guard, so this class is only resolved
 * when TOP is actually installed.
 */
public final class TopIntegration {

    private TopIntegration() {}

    /** TOP IMC handshake: registers {@link NeoOriginsTopProvider} with the probe. */
    public static void enqueueImc() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe",
            () -> (Function<ITheOneProbe, Void>) probe -> {
                probe.registerEntityProvider(new NeoOriginsTopProvider());
                return null;
            });
    }
}
