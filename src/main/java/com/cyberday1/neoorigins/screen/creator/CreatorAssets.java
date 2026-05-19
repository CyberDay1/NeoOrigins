package com.cyberday1.neoorigins.screen.creator;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

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

    // ── registry-backed pick sources for common string fields ───────────────

    private static List<String> reg(net.minecraft.core.Registry<?> r) {
        return r.keySet().stream().map(Object::toString).sorted().toList();
    }

    private static final Map<String, Supplier<List<String>>> SOURCES = Map.of(
        "particle", () -> reg(BuiltInRegistries.PARTICLE_TYPE),
        "sound",    () -> reg(BuiltInRegistries.SOUND_EVENT),
        "block",    () -> reg(BuiltInRegistries.BLOCK),
        "item",     () -> reg(BuiltInRegistries.ITEM),
        "entity",   () -> reg(BuiltInRegistries.ENTITY_TYPE),
        "attribute",() -> reg(BuiltInRegistries.ATTRIBUTE),
        "effect",   CreatorAssets::effectIds);

    /** Exact JSON field names that map to a registry pick list. */
    private static final Map<String, String> FIELD_TO_KIND = Map.ofEntries(
        Map.entry("particle", "particle"), Map.entry("particle_type", "particle"),
        Map.entry("sound", "sound"), Map.entry("sound_event", "sound"),
        Map.entry("block", "block"),
        Map.entry("item", "item"),
        Map.entry("entity_type", "entity"), Map.entry("entity", "entity"),
        Map.entry("attribute", "attribute"),
        Map.entry("effect", "effect"), Map.entry("status_effect", "effect"),
        Map.entry("mob_effect", "effect"));

    /** The registry "kind" a string field picks from, or {@code null}. */
    public static String registryKind(String field) {
        return field == null ? null
            : FIELD_TO_KIND.get(field.toLowerCase(Locale.ROOT));
    }

    /** Cached id list for a registry kind ({@link #registryKind}); empty if unknown. */
    public static List<String> registryList(String kind) {
        Supplier<List<String>> s = SOURCES.get(kind);
        return s == null ? List.of() : s.get();
    }

    /**
     * Plain-language one-liners for common config fields, used by the form's
     * hover tooltip when the schema gives no description. Conservative and
     * extended as gaps surface — a missing entry just falls back to the
     * synthesized type line.
     */
    public static final Map<String, String> DOC = Map.ofEntries(
        Map.entry("condition", "Only applies when this condition passes (a DSL condition object)."),
        Map.entry("particle", "The particle to spawn (a registered particle id)."),
        Map.entry("frequency", "How often it triggers, in ticks (20 = 1s). Lower = more often."),
        Map.entry("count", "How many to spawn each time."),
        Map.entry("spread", "Random horizontal scatter radius."),
        Map.entry("offset", "Position offset from the player."),
        Map.entry("speed", "Initial speed/velocity factor."),
        Map.entry("amount", "The magnitude of the effect."),
        Map.entry("duration", "How long it lasts, in ticks (20 = 1s)."),
        Map.entry("amplifier", "Status-effect strength level (0 = level I)."),
        Map.entry("effect", "The status effect to apply (a registered effect id)."),
        Map.entry("attribute", "The attribute to modify (e.g. minecraft:generic.movement_speed)."),
        Map.entry("operation", "How the value is applied: add, multiply base, or multiply total."),
        Map.entry("slot", "Equipment slot this applies in."),
        Map.entry("item", "An item id; optionally with SNBT components."),
        Map.entry("scale", "Multiplier — 1.0 is normal size."),
        Map.entry("cooldown", "Reuse delay, in ticks (20 = 1s)."),
        Map.entry("resource", "The resource/bar this reads or changes."),
        Map.entry("chance", "Probability 0.0–1.0 that it happens."),
        Map.entry("radius", "Effect radius, in blocks."),
        Map.entry("strength", "Intensity (overlay opacity / effect power)."),
        Map.entry("texture", "Resource-pack texture path you ship; use Browse."),
        Map.entry("shader", "Post-shader json path; use Browse."),
        Map.entry("hidden", "Hide this from the origin screen power list."),
        Map.entry("name", "Display name for this power (optional)."),
        Map.entry("description", "Tooltip text for this power (optional)."));
}
