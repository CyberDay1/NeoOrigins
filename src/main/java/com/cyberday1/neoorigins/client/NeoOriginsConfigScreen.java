package com.cyberday1.neoorigins.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Trampoline that registers NeoOrigins's auto-generated config screen with
 * the mod menu's "Config" button. The screen is rendered by NeoForge's
 * built-in {@link ConfigurationScreen}, which walks the registered
 * {@code ModConfigSpec} and produces controls for every value.
 *
 * <p>Lives in the client package so the dedicated server JVM never resolves
 * the client-only types ({@code ConfigurationScreen}, {@code IConfigScreenFactory}
 * are both under {@code net.neoforged.neoforge.client.gui}). The call site in
 * {@code NeoOrigins} gates on {@code FMLEnvironment.dist.isClient()} so this
 * class is only loaded on the physical client.
 *
 * <p>Same trampoline pattern as {@code feedback_new_clientclass_opcode}: keep
 * client-only types behind a class whose method bodies are verified lazily
 * and only ever loaded on the client side.
 */
public final class NeoOriginsConfigScreen {

    private NeoOriginsConfigScreen() {}

    public static void register(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
