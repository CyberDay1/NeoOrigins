package com.cyberday1.neoorigins.config;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Admin / permissions config (COMMON, {@code config/neoorigins/admin.toml}).
 *
 * <p>Server-operator policy knobs: the command-power blacklist, command
 * access ({@code public_origin_get}), per-power dimension restrictions,
 * the global taming/scare entity blacklist, compat
 * origin filtering and debug flags. COMMON loads early enough to be readable
 * during the boot-time datapack reload; it is not synced to clients.
 *
 * <p>Part of the 2.2.2 config split — see {@link GameplayConfig},
 * {@link PowerOverridesConfig} and {@link ContentTogglesConfig}.
 */
public final class AdminConfig {

    private AdminConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Debug flags ─────────────────────────────────────────────────────
    // Kept top-level (no section) so values migrate 1:1 from the legacy
    // neoorigins-common.toml, where they were also top-level.

    public static final ModConfigSpec.BooleanValue DEBUG_POWER_LOADING =
        BUILDER
            .comment("Log per-namespace power counts after each data reload.",
                     "Useful for addon and datapack authors debugging load issues.")
            .define("debug_power_loading", false);

    public static final ModConfigSpec.BooleanValue DEBUG_COMPAT_ACTIONS =
        BUILDER
            .comment("Send in-game chat feedback when a compat power action resolves to no-op (unsupported action type).",
                     "Useful for pack authors debugging why their imported powers aren't working.")
            .define("debug_compat_actions", false);

    /** Convenience accessor for hot-path checks. */
    public static boolean isDebugCompatActions() { return DEBUG_COMPAT_ACTIONS.get(); }

    public static final ModConfigSpec.BooleanValue DEBUG_HUD =
        BUILDER
            .comment("Diagnostic logging for the ability HUD cluster and flight-ability",
                     "syncing: logs hover-state changes on the cooldown cluster (client)",
                     "and every server-side grant/clear/accept of flight abilities.",
                     "One line per state change, not per tick. Leave off in normal play.")
            .define("debug_hud", false);

    /**
     * True when {@code debug_hud} diagnostics are on. Safe to call before the
     * config file loads (returns false instead of throwing).
     */
    public static boolean isDebugHud() {
        try {
            return DEBUG_HUD.get();
        } catch (IllegalStateException e) {
            return false;
        }
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
            .defineListAllowEmpty("rules", List.of(), AdminConfig::validateRestrictionRule);

    static { BUILDER.pop(); }

    // ── Compat Origin Filtering ─────────────────────────────────────────
    public static final ModConfigSpec.DoubleValue COMPAT_MIN_POWER_RATIO;

    static {
        BUILDER.comment(
            "Compat origin filtering.",
            "Origins from addon mods are hidden if fewer than this fraction of their",
            "powers loaded successfully. Set to 0.0 to show all origins regardless.",
            "Default 0.5 = origins with <50% of powers working are hidden."
        ).push("compat_filtering");

        COMPAT_MIN_POWER_RATIO = BUILDER
            .comment("Minimum ratio of loaded powers (0.0-1.0) for an addon origin to appear.")
            .defineInRange("min_power_ratio", 0.5, 0.0, 1.0);

        BUILDER.pop();
    }

    // ── Commands ────────────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue PUBLIC_ORIGIN_GET;

    static {
        BUILDER.comment(
            "Command access tuning."
        ).push("commands");

        PUBLIC_ORIGIN_GET = BUILDER
            .comment("Allow non-OP players to run /neoorigins get <player> to look up",
                     "another player's origin. Operators (permission level 2) can always",
                     "run it regardless of this setting. Set to false to make origins",
                     "visible only to staff.")
            .define("public_origin_get", true);

        BUILDER.pop();
    }

    /** True if any player (not just OPs) may run {@code /neoorigins get <player>}. */
    public static boolean isPublicOriginGetAllowed() {
        return PUBLIC_ORIGIN_GET.get();
    }

    // ── Command-power blacklist ─────────────────────────────────────────
    // Datapack powers can run arbitrary server commands (the `command`/
    // `execute_command` actions, the raycast command_along_ray/command_at_hit
    // extensions, and the `command` condition). Without a guard, a pack could
    // ship `/op @s` and silently escalate any player to operator. This list
    // names command roots that are refused at execution time regardless of the
    // power's permission level. Matching is case-insensitive on the effective
    // command root, and `execute ... run <cmd>` is unwrapped so a blacklisted
    // command can't be smuggled behind an execute chain.

    public static final ModConfigSpec.ConfigValue<List<? extends String>> COMMAND_POWER_BLACKLIST;

    static {
        BUILDER.comment(
            "Commands that NeoOrigins powers are NEVER allowed to run.",
            "Applies to the command/execute_command actions, the raycast",
            "command_along_ray and command_at_hit extensions, and the command",
            "condition. A blocked command is refused and logged instead of run.",
            "Match is case-insensitive on the command root; `execute ... run X`",
            "is unwrapped so X is what gets checked. List the root only (no slash)."
        ).push("command_powers");

        COMMAND_POWER_BLACKLIST = BUILDER
            .comment("Command roots forbidden from power execution.")
            .defineList("command_power_blacklist",
                List.of("op", "deop", "ban", "ban-ip", "pardon", "pardon-ip",
                        "kick", "whitelist", "stop", "save-all", "save-off",
                        "save-on", "setidletimeout", "debug", "perf", "datapack",
                        "reload"),
                obj -> obj instanceof String);

        BUILDER.pop();
    }

    /**
     * True if the given command root is blacklisted from power execution.
     * {@code root} should already be the unwrapped, slash-stripped first token
     * (see {@code CommandPowerGuard.extractRoot}); matching is case-insensitive.
     */
    public static boolean isCommandPowerBlocked(String root) {
        if (root == null || root.isEmpty()) return false;
        for (String blocked : COMMAND_POWER_BLACKLIST.get()) {
            if (blocked.equalsIgnoreCase(root)) return true;
        }
        return false;
    }

    // ── Taming / scare entity exclusions ────────────────────────────────
    // Global blacklist for the mob-control power family: tame_mob,
    // scare_entities and mobs_ignore_player. Entities listed here can never
    // be tamed, scared or made to ignore a player by ANY power, on top of
    // the hardcoded boss-tier exclusion (Warden, Ender Dragon, Wither) and
    // any per-power entity_blacklist. Shared matching lives in
    // EntityExclusions.

    public static final ModConfigSpec.ConfigValue<List<? extends String>> TAME_SCARE_ENTITY_BLACKLIST;

    static {
        BUILDER.comment(
            "Global entity exclusions for taming and scare powers (tame_mob,",
            "scare_entities, mobs_ignore_player). Listed entities can never be",
            "tamed, scared, or made to ignore a player by any power. The Warden,",
            "Ender Dragon and Wither are ALWAYS excluded and need not be listed.",
            "Format: entity ids (e.g. \"minecraft:elder_guardian\") or entity-type",
            "tags (e.g. \"#mymod:untameable\")."
        ).push("entity_exclusions");

        TAME_SCARE_ENTITY_BLACKLIST = BUILDER
            .comment("Entity ids / #tags excluded from all taming and scare powers.")
            .defineList("tame_scare_entity_blacklist",
                List.of(),
                obj -> obj instanceof String);

        BUILDER.pop();
    }

    /**
     * Raw global taming/scare blacklist entries (entity ids and {@code #tags}).
     * Matching is done by {@code EntityExclusions.isConfigBlacklisted}.
     */
    public static List<? extends String> tameScareEntityBlacklist() {
        return TAME_SCARE_ENTITY_BLACKLIST.get();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ── Parsed dimension restriction cache ──────────────────────────────
    // Rebuilt on each access from the TOML list to keep in sync with config reloads.

    private static volatile Map<String, Set<ResourceKey<Level>>> parsedRestrictions;
    private static volatile int lastConfigHash;
    private static volatile int restrictionsVersionCounter;

    public static boolean isPowerRestrictedInDimension(Identifier powerId, ResourceKey<Level> dimension) {
        Map<String, Set<ResourceKey<Level>>> map = getParsedRestrictions();
        Set<ResourceKey<Level>> denied = map.get(powerId.toString());
        return denied != null && denied.contains(dimension);
    }

    /**
     * Version counter for the dimension-restrictions config. Bumps whenever the rules list
     * content changes. Used by ActiveOriginService's per-player power cache for invalidation.
     */
    public static int restrictionsVersion() {
        // Use the monotonic counter bumped on each config reload rather than
        // hashCode() which has collision risk across different rule sets.
        getParsedRestrictions(); // ensure counter is up to date
        return restrictionsVersionCounter;
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
            restrictionsVersionCounter++;
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
