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
 * {@code ModList.isLoaded("theoneprobe")} guard.
 */
public final class TopIntegration {

    private TopIntegration() {}

    /** TOP IMC handshake: registers {@link NeoOriginsTopProvider} with the probe. */
    public static void enqueueImc() {
        InterModComms.sendTo("theoneprobe", "getTheOneProbe", TopIntegration::registrar);
    }

    /** The function TOP applies to its {@link ITheOneProbe} when it processes our message. */
    static Function<ITheOneProbe, Void> registrar() {
        return probe -> {
            probe.registerEntityProvider(new NeoOriginsTopProvider());
            return null;
        };
    }
}
