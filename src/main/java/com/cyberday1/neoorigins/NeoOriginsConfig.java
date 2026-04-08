package com.cyberday1.neoorigins;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.*;

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

    // ── Disabled Origins ────────────────────────────────────────────────
    // Each built-in origin can be disabled here. Disabled origins are
    // removed after data loading and will not appear in the origin selection screen.

    private static final String[] BUILT_IN_ORIGINS = {
        "human", "merling", "avian", "blazeling", "elytrian", "enderian",
        "arachnid", "shulk", "phantom", "feline", "golem", "caveborn",
        "sylvan", "draconic", "revenant", "tiny", "abyssal", "voidwalker",
        "stoneguard", "verdant", "umbral", "inchling", "sporeling",
        "frostborn", "strider", "siren", "piglin", "hiveling", "cinderborn",
        "sculkborn", "enderite"
    };

    public static final Map<String, ModConfigSpec.BooleanValue> ORIGIN_TOGGLES;

    static {
        BUILDER.comment(
            "Enable or disable built-in origins.",
            "Set to false to remove an origin from the selection screen.",
            "Datapack and originpack origins are not affected by these toggles."
        ).push("origins");

        Map<String, ModConfigSpec.BooleanValue> toggles = new LinkedHashMap<>();
        for (String name : BUILT_IN_ORIGINS) {
            toggles.put(name, BUILDER.define(name, true));
        }
        ORIGIN_TOGGLES = Collections.unmodifiableMap(toggles);
        BUILDER.pop();
    }

    // ── Dimension Power Restrictions ────────────────────────────────────
    // Per-power dimension deny lists. Powers listed here are suppressed
    // when the player is in the specified dimension(s).
    //
    // Format: "power_id = dimension1, dimension2, ..."
    // Example: "neoorigins:flight = minecraft:the_nether, minecraft:the_end"

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_RESTRICTIONS =
        BUILDER
            .comment(
                "Per-power dimension restrictions.",
                "Powers listed here will be disabled when the player is in the specified dimension(s).",
                "Format: \"<power_id> = <dimension1>, <dimension2>, ...\"",
                "Example: \"neoorigins:elytrian_flight = minecraft:the_nether, minecraft:the_end\"")
            .push("dimension_restrictions")
            .defineListAllowEmpty("rules", List.of(), NeoOriginsConfig::validateRestrictionRule);

    static { BUILDER.pop(); }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Parsed dimension restriction cache ──────────────────────────────
    // Rebuilt on each access from the TOML list to keep in sync with config reloads.

    private static volatile Map<String, Set<ResourceKey<Level>>> parsedRestrictions;
    private static volatile int lastConfigHash;

    /**
     * Returns true if the given power ID is restricted in the player's current dimension.
     */
    public static boolean isPowerRestrictedInDimension(Identifier powerId, ResourceKey<Level> dimension) {
        Map<String, Set<ResourceKey<Level>>> map = getParsedRestrictions();
        Set<ResourceKey<Level>> denied = map.get(powerId.toString());
        return denied != null && denied.contains(dimension);
    }

    /**
     * Returns true if the given origin name (e.g. "merling") is a disabled built-in origin.
     */
    public static boolean isOriginDisabled(Identifier originId) {
        if (!NeoOrigins.MOD_ID.equals(originId.getNamespace())) return false;
        ModConfigSpec.BooleanValue toggle = ORIGIN_TOGGLES.get(originId.getPath());
        return toggle != null && !toggle.get();
    }

    private static Map<String, Set<ResourceKey<Level>>> getParsedRestrictions() {
        List<? extends String> rules = DIMENSION_RESTRICTIONS.get();
        int hash = rules.hashCode();
        if (parsedRestrictions == null || hash != lastConfigHash) {
            Map<String, Set<ResourceKey<Level>>> map = new HashMap<>();
            for (String rule : rules) {
                int eq = rule.indexOf('=');
                if (eq < 0) continue;
                String powerId = rule.substring(0, eq).trim();
                String[] dims = rule.substring(eq + 1).split(",");
                Set<ResourceKey<Level>> dimSet = map.computeIfAbsent(powerId, k -> new HashSet<>());
                for (String dim : dims) {
                    String trimmed = dim.trim();
                    if (!trimmed.isEmpty()) {
                        dimSet.add(ResourceKey.create(Registries.DIMENSION, Identifier.parse(trimmed)));
                    }
                }
            }
            parsedRestrictions = map;
            lastConfigHash = hash;
        }
        return parsedRestrictions;
    }

    private static boolean validateRestrictionRule(Object obj) {
        if (!(obj instanceof String s)) return false;
        int eq = s.indexOf('=');
        if (eq < 0) return false;
        String powerId = s.substring(0, eq).trim();
        return powerId.contains(":");
    }
}
