package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Content toggles config (SERVER, {@code neoorigins/content.toml}).
 *
 * <p>Gameplay toggles the client must observe: origin/class enable toggles
 * and the global resource-bar disable. This spec MUST stay SERVER type —
 * NeoForge auto-syncs SERVER configs to every connecting client, which is
 * what fixed the origin-toggle desync (disabling an origin server-side now
 * correctly hides it on remote clients). These values are only read after a
 * world is active, so the SERVER load-timing restriction (not loaded during
 * the boot-time datapack reload) does not bite.
 *
 * <p>The file loads from {@code config/neoorigins/content.toml} by default;
 * a per-world copy at {@code <world>/serverconfig/neoorigins/content.toml}
 * overrides it (standard NeoForge server-config resolution).
 *
 * <p>Part of the 2.2.2 config split — see {@link GameplayConfig},
 * {@link AdminConfig} and {@link PowerOverridesConfig}.
 */
public final class ContentTogglesConfig {

    private ContentTogglesConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue DISABLE_RESOURCE_BARS =
        BUILDER
            .comment("Disable all resource bars (mana, stamina, rage, etc.) globally.",
                     "When true, resource bars are hidden from the HUD and any active",
                     "power that would normally cost a resource falls back to costing",
                     "hunger instead (resource_cost_amount food points are deducted).",
                     "Useful for packs that prefer vanilla hunger as the universal cost.")
            .define("disable_resource_bars", false);

    // ── Disabled Origins ────────────────────────────────────────────────
    // Each built-in origin can be disabled here. Disabled origins are hidden
    // from the origin selection screen but remain registered for commands.

    private static final String[] BUILT_IN_ORIGINS = {
        "human", "merling", "avian", "blazeling", "elytrian", "enderian",
        "arachnid", "shulk", "phantom", "feline", "golem", "caveborn",
        "sylvan", "draconic", "revenant", "tiny", "abyssal", "voidwalker",
        "stoneguard", "verdant", "umbral", "inchling", "sporeling",
        "frostborn", "strider", "siren", "piglin", "hiveling", "cinderborn",
        "sculkborn", "enderite", "necromancer", "gorgon", "automaton", "kraken",
        "warden", "dwarf", "breeze", "vampire",
        "air_mage", "darkness_mage", "earth_mage", "fire_mage", "gravity_mage",
        "water_mage", "monster_tamer",
        "skeleton", "slime", "wraith"
    };

    public static final Map<String, ModConfigSpec.BooleanValue> ORIGIN_TOGGLES;

    static {
        BUILDER.comment(
            "Enable or disable built-in origins.",
            "Set to false to hide an origin from the selection screen.",
            "Disabled origins can still be assigned via /neoorigins set.",
            "Datapack and originpack origins are not affected by these toggles."
        ).push("origins");

        Map<String, ModConfigSpec.BooleanValue> toggles = new LinkedHashMap<>();
        for (String name : BUILT_IN_ORIGINS) {
            toggles.put(name, BUILDER.define(name, true));
        }
        ORIGIN_TOGGLES = Collections.unmodifiableMap(toggles);
        BUILDER.pop();
    }

    // ── Disabled Classes ────────────────────────────────────────────────
    // Each built-in class can be disabled here. Disabled classes are
    // removed after data loading. If ALL classes are disabled, the class
    // selection screen is skipped entirely.

    private static final String[] BUILT_IN_CLASSES = {
        "class_warrior", "class_archer", "class_miner", "class_beastmaster",
        "class_explorer", "class_sentinel", "class_herbalist", "class_scout",
        "class_berserker", "class_titan", "class_rogue", "class_lumberjack",
        "class_blacksmith", "class_cook", "class_merchant", "class_cleric",
        "class_nitwit", "class_fisher", "class_mason", "class_paladin"
    };

    public static final Map<String, ModConfigSpec.BooleanValue> CLASS_TOGGLES;

    static {
        BUILDER.comment(
            "Enable or disable built-in classes.",
            "Set to false to remove a class from the selection screen.",
            "If all classes are disabled, the class selection screen is skipped entirely."
        ).push("classes");

        Map<String, ModConfigSpec.BooleanValue> toggles = new LinkedHashMap<>();
        for (String name : BUILT_IN_CLASSES) {
            toggles.put(name, BUILDER.define(name, true));
        }
        CLASS_TOGGLES = Collections.unmodifiableMap(toggles);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean isResourceBarsDisabled() {
        return DISABLE_RESOURCE_BARS.get();
    }

    /**
     * Returns true if the given origin/class is disabled via config toggles.
     * Checks both [origins] and [classes] sections.
     */
    public static boolean isOriginDisabled(ResourceLocation originId) {
        if (!NeoOrigins.MOD_ID.equals(originId.getNamespace())) return false;
        String path = originId.getPath();
        ModConfigSpec.BooleanValue toggle = ORIGIN_TOGGLES.get(path);
        if (toggle == null) toggle = CLASS_TOGGLES.get(path);
        return toggle != null && !toggle.get();
    }
}
