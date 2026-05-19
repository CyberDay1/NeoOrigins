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

    /**
     * Curated "what you can do here by default" reference for the Appearance
     * tab — what each visual power is, what it needs, and a known-good example.
     */
    public static final List<String> DEFAULTS_REFERENCE = List.of(
        "overlay      full-screen texture (e.g. a tint/vignette). Needs a .png you ship;",
        "             pick from installed textures or type assets/<ns>/textures/x.png.",
        "model_color  tints the player model — just RGBA, no asset needed.",
        "shader       a post-process shader. Needs a shaders/post/<name>.json;",
        "             vanilla ships e.g. minecraft:shaders/post/creeper.json.",
        "size_scaling scales the player — numeric only, no asset.",
        "invisibility persistent_effect with minecraft:invisibility (a vanilla effect).",
        "glow         persistent_effect with minecraft:glowing (a vanilla effect).",
        "Use Browse to pick from everything actually installed (vanilla + packs)."
    );
}
