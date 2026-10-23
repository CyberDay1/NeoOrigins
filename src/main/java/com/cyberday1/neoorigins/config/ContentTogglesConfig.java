package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    public static final ModConfigSpec.BooleanValue DISABLE_NIGHT_VISION =
        BUILDER
            .comment("Disable night-vision globally.",
                     "When true, any power that would apply the minecraft:night_vision",
                     "effect (the neoorigins:night_vision / persistent_effect path) is",
                     "suppressed: the player's screen is never brightened by the effect.",
                     "Other effects on the same power still apply; only night_vision is",
                     "stripped. Useful for packs that want darkness to stay threatening.")
            .define("disable_night_vision", false);

    public static final ModConfigSpec.BooleanValue DISABLE_ENHANCED_VISION =
        BUILDER
            .comment("Disable enhanced-vision (low-light brightness boost) globally.",
                     "When true, the neoorigins:enhanced_vision capability is never sent",
                     "to clients, so the client-side brightness-curve boost (cat eyes,",
                     "salamander, oculus drone, etc.) stays off. Independent of the",
                     "night-vision toggle above. These are two separate mechanisms.")
            .define("disable_enhanced_vision", false);

    public static final ModConfigSpec.BooleanValue CLASSIC_MODE =
        BUILDER
            .comment("Classic mode: offer only the original Origins mod's origins and the original",
                     "Origins: Classes classes, with their original power sets.",
                     "When true, every other built-in origin and class is hidden from selection,",
                     "whatever its toggle below says. The toggles still work on the classic ones:",
                     "set a classic origin or class to false below and it stays hidden.",
                     "Datapack and originpack origins are not affected.",
                     "WARNING: if you turn this on after a world is created, players who already",
                     "hold an origin or class that classic mode hides keep it until it is reset.",
                     "They need to reset it with an Orb of Origin or Orb of Class, or an admin can",
                     "run /neoorigins reset <player>. They are told this in chat when they join.")
            .define("classic_mode", false);

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
        "skeleton", "slime", "wraith",
        "asura", "windwalker", "qi_cultivator", "golden_body", "iron_monk", "sword_immortal"
    };

    /** Built-in origins with no toggle of their own (the dragons). Classic mode still hides them. */
    private static final String[] UNTOGGLED_BUILT_IN_ORIGINS = {
        "cave_dragon", "forest_dragon", "sea_dragon"
    };

    /**
     * The original Origins mod's ten origins, by NeoOrigins path (Blazeborn is
     * {@code blazeling} here). Each has a classic variant under
     * {@code data/neoorigins/origins/classic_origins/}.
     */
    public static final Set<String> CLASSIC_ORIGINS = Set.of(
        "human", "merling", "avian", "blazeling", "elytrian", "enderian",
        "arachnid", "shulk", "phantom", "feline");

    /** The fourteen Origins: Classes classes, by NeoOrigins path. */
    public static final Set<String> CLASSIC_CLASSES = Set.of(
        "class_archer", "class_beastmaster", "class_blacksmith", "class_cleric",
        "class_cook", "class_explorer", "class_farmer", "class_lumberjack",
        "class_merchant", "class_miner", "class_nitwit", "class_rancher",
        "class_rogue", "class_warrior");

    /** Classic classes NeoOrigins does not otherwise ship: shown only in classic mode. */
    public static final Set<String> CLASSIC_ONLY = Set.of("class_farmer", "class_rancher");

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

    public static boolean isNightVisionDisabled() {
        return DISABLE_NIGHT_VISION.get();
    }

    public static boolean isEnhancedVisionDisabled() {
        return DISABLE_ENHANCED_VISION.get();
    }

    /**
     * Classic mode as currently loaded. False before the SERVER spec loads (it
     * does not exist during the boot-time datapack reload), so callers that run
     * that early see the normal content and are re-run on the config's Loading event.
     */
    public static boolean isClassicMode() {
        return SPEC.isLoaded() && CLASSIC_MODE.get();
    }

    /** Every built-in origin and class path NeoOrigins ships, classic-only ones included. */
    public static boolean isBuiltIn(String path) {
        return ORIGIN_TOGGLES.containsKey(path) || CLASS_TOGGLES.containsKey(path)
            || CLASSIC_ONLY.contains(path)
            || java.util.Arrays.asList(UNTOGGLED_BUILT_IN_ORIGINS).contains(path);
    }

    public static boolean isClassic(String path) {
        return CLASSIC_ORIGINS.contains(path) || CLASSIC_CLASSES.contains(path);
    }

    /**
     * Returns true if the given origin/class is disabled via config toggles.
     * Checks both [origins] and [classes] sections.
     */
    public static boolean isOriginDisabled(Identifier originId) {
        if (!NeoOrigins.MOD_ID.equals(originId.getNamespace())) return false;
        return isBuiltInDisabled(originId.getPath(), isClassicMode(), path -> {
            ModConfigSpec.BooleanValue toggle = ORIGIN_TOGGLES.get(path);
            if (toggle == null) toggle = CLASS_TOGGLES.get(path);
            return toggle != null && !toggle.get();
        });
    }

    /**
     * The disable rule for a {@code neoorigins:} path, apart from the config
     * plumbing so it can be tested. A path's own toggle always wins when it is
     * off. Classic mode additionally hides every built-in that is not classic;
     * outside classic mode the classic-only classes are hidden instead.
     * Non-built-in paths (a datapack adding to our namespace) are never touched.
     */
    public static boolean isBuiltInDisabled(String path, boolean classicMode, Predicate<String> toggledOff) {
        if (toggledOff.test(path)) return true;
        if (!isBuiltIn(path)) return false;
        if (classicMode) return !isClassic(path);
        return CLASSIC_ONLY.contains(path);
    }
}
