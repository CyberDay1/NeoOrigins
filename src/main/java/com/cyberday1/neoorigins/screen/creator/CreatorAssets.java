package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;

/**
 * Discovers what an Appearance author can actually reference: status effects
 * from the live registry (vanilla + any mod) and texture / post-shader files
 * from the live resource stack (vanilla + every loaded resource/data pack), so
 * the pickers offer real, installed assets rather than a blank text box.
 *
 * <p>Each list is built once on first use (the resource scan is the expensive
 * part) and reused for the session.
 */
public final class CreatorAssets {

    private CreatorAssets() {}

    private static List<String> effects;
    private static List<String> textures;
    private static List<String> shaders;

    /** Registered status effects (vanilla + modded), e.g. {@code minecraft:glowing}. */
    public static List<String> effectIds() {
        if (effects == null) {
            effects = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .map(Object::toString).sorted().toList();
        }
        return effects;
    }

    /** Every {@code *.png} under {@code textures/} across all loaded packs. */
    public static List<String> textureAssets() {
        if (textures == null) {
            textures = Minecraft.getInstance().getResourceManager()
                .listResources("textures", loc -> loc.getPath().endsWith(".png"))
                .keySet().stream().map(Object::toString).sorted().toList();
        }
        return textures;
    }

    /** Every post-shader json under {@code shaders/post/} across all packs. */
    public static List<String> shaderAssets() {
        if (shaders == null) {
            shaders = Minecraft.getInstance().getResourceManager()
                .listResources("shaders/post", loc -> loc.getPath().endsWith(".json"))
                .keySet().stream().map(Object::toString).sorted().toList();
        }
        return shaders;
    }
}
