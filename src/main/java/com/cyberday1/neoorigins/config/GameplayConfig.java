package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.Set;

/**
 * Gameplay tuning config (COMMON, {@code config/neoorigins/gameplay.toml}).
 *
 * <p>Player-facing gameplay systems: Orb of Origins XP cost, auto-human mode,
 * random origin assignment, essence evolution, ocean-origin behaviour, sun
 * damage helmet absorption, mount consent, friendly-fire filtering and armor
 * class lists. COMMON loads early enough to be readable during the boot-time
 * datapack reload; it is NOT network-synced, which is fine because these
 * values are baked into the power/origin data that is synced separately.
 *
 * <p>Part of the 2.2.2 config split — admin/permissions knobs live in
 * {@link AdminConfig}, per-power overrides in {@link PowerOverridesConfig},
 * synced origin/class toggles in {@link ContentTogglesConfig}.
 */
public final class GameplayConfig {

    private GameplayConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Config version stamp ────────────────────────────────────────────

    /**
     * Schema version written into {@code gameplay.toml}. Bumped only when
     * {@link ConfigMigrator} gains a new one-time heal that has to be able to
     * tell "already handled" from "predates the change".
     *
     * <p>This key MUST stay declared in the spec. {@code ModConfigSpec.correct}
     * deletes every key in the file that the spec does not declare, so an
     * undeclared stamp would be stripped on the very load that follows the
     * migrator writing it, and the heal would re-run on every single boot.
     */
    public static final int CURRENT_CONFIG_VERSION = 1;

    public static final ModConfigSpec.IntValue CONFIG_VERSION;

    static {
        CONFIG_VERSION = BUILDER
            .comment("Written by the mod to track one-time config migrations. Do not edit by hand.",
                     "The mod uses this to tell a config it has already fixed up from one written",
                     "by an older version, so that each migration runs exactly once. Deleting it",
                     "makes the mod treat this file as pre-2.2.22 again.")
            .defineInRange("config_version", CURRENT_CONFIG_VERSION, 0, Integer.MAX_VALUE);
    }

    public static int configVersion() { return CONFIG_VERSION.get(); }

    // ── Orb of Origins ──────────────────────────────────────────────────
    public static final ModConfigSpec.IntValue ORB_LEVELS_PER_USE;
    public static final ModConfigSpec.BooleanValue ORB_SCALE_COST;
    public static final ModConfigSpec.IntValue ORB_OF_CLASS_LEVELS_PER_USE;

    static {
        BUILDER.comment(
            "Orb of Origins settings.",
            "Controls XP cost behaviour when a player uses an Orb of Origin."
        ).push("orb_of_origins");

        ORB_SCALE_COST = BUILDER
            .comment("Whether the XP cost scales with the number of prior orb uses.",
                     "true  (default): Cost = levels_per_use * previous orb uses (first use free, then ramps).",
                     "false: flat cost. Every use (including the first) costs exactly levels_per_use levels.")
            .define("scale_cost", true);

        ORB_LEVELS_PER_USE = BUILDER
            .comment("XP levels charged per orb use.",
                     "When scale_cost=true:  Cost = this value * number of previous orb uses (first use free).",
                     "When scale_cost=false: Cost = this value, flat, on every use.",
                     "Set to 0 to disable XP cost entirely.")
            .defineInRange("levels_per_use", 5, 0, 1000);

        ORB_OF_CLASS_LEVELS_PER_USE = BUILDER
            .comment("Flat XP levels charged per Orb of Class use.",
                     "The Orb of Class only resets the class layer (keeping the main origin),",
                     "so it is intentionally cheaper than the full Orb of Origin reset.",
                     "Set to 0 to disable its XP cost entirely.")
            .defineInRange("class_levels_per_use", 2, 0, 1000);

        BUILDER.pop();
    }

    public static int orbLevelsPerUse() { return ORB_LEVELS_PER_USE.get(); }
    public static boolean orbScaleCost() { return ORB_SCALE_COST.get(); }
    public static int orbOfClassLevelsPerUse() { return ORB_OF_CLASS_LEVELS_PER_USE.get(); }

    // ── Auto-Human Mode ─────────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue AUTO_HUMAN;

    static {
        BUILDER.comment(
            "Auto-human mode.",
            "When enabled, new players are automatically assigned neoorigins:human",
            "on the origin layer and skip straight to the class selection screen.",
            "Useful for servers that want to bypass origin selection entirely."
        ).push("auto_human");

        AUTO_HUMAN = BUILDER
            .comment("Automatically assign human origin and open class selection only.")
            .define("enabled", false);

        BUILDER.pop();
    }

    public static boolean isAutoHuman() { return AUTO_HUMAN.get(); }

    // ── Skip Initial Selection ──────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue SKIP_INITIAL_SELECTION;

    static {
        BUILDER.comment(
            "Skip the initial origin/class selection entirely.",
            "When enabled, new players spawn with NO origin and the selection",
            "screen never opens on first join. They play as an origin-less",
            "player until granted one later (e.g. via an Orb of Origin).",
            "Unlike auto-human mode this assigns nothing, and unlike disabling",
            "every class it does not leave the player stuck invulnerable.",
            "Takes priority over auto-human and random-assignment modes."
        ).push("skip_initial_selection");

        SKIP_INITIAL_SELECTION = BUILDER
            .comment("Spawn new players with no origin and no selection screen.")
            .define("enabled", false);

        BUILDER.pop();
    }

    public static boolean isSkipInitialSelection() { return SKIP_INITIAL_SELECTION.get(); }

    // ── Random Origin Assignment ────────────────────────────────────────
    public static final ModConfigSpec.EnumValue<RandomMode> RANDOM_MODE;
    public static final ModConfigSpec.IntValue RANDOM_REROLLS;

    public enum RandomMode { DISABLED, FIRST_JOIN, EVERY_DEATH }

    static {
        BUILDER.comment(
            "Random origin assignment mode.",
            "DISABLED: players choose their origin normally.",
            "FIRST_JOIN: origins are randomly assigned on first join (no selection screen).",
            "EVERY_DEATH: origins are randomly re-assigned on each respawn."
        ).push("random_assignment");

        RANDOM_MODE = BUILDER
            .comment("When to randomly assign origins.")
            .defineEnum("mode", RandomMode.DISABLED);

        RANDOM_REROLLS = BUILDER
            .comment("Number of times a player may reroll after random assignment.",
                     "0 = no rerolls (stuck with what you get).",
                     "-1 = unlimited rerolls via Orb of Origin.")
            .defineInRange("rerolls", 0, -1, 100);

        BUILDER.pop();
    }

    public static RandomMode getRandomMode() { return RANDOM_MODE.get(); }

    // ── Essence Evolution ───────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue EVOLUTION_ENABLED;
    public static final ModConfigSpec.IntValue EVOLUTION_TIER_1_KILLS;
    public static final ModConfigSpec.IntValue EVOLUTION_TIER_2_KILLS;
    public static final ModConfigSpec.IntValue EVOLUTION_TIER_3_KILLS;
    public static final ModConfigSpec.IntValue EVOLUTION_MESSAGE_INTERVAL;

    static {
        BUILDER.comment(
            "Essence Evolution system. Origins evolve through 3 tiers",
            "(Evolved → Ascended → Apex) by accumulating mob kills.",
            "An Orb of Origin resets the player to base tier."
        ).push("evolution");

        EVOLUTION_ENABLED = BUILDER
            .comment("Enable the evolution system. When false, kills are not tracked",
                     "and evolution prompts never appear.")
            .define("enabled", true);

        EVOLUTION_TIER_1_KILLS = BUILDER
            .comment("Mob kills required to reach Evolved (tier 1).")
            .defineInRange("tier_1_kills", 1000, 1, 1000000);

        EVOLUTION_TIER_2_KILLS = BUILDER
            .comment("Mob kills required to reach Ascended (tier 2).")
            .defineInRange("tier_2_kills", 2500, 1, 1000000);

        EVOLUTION_TIER_3_KILLS = BUILDER
            .comment("Mob kills required to reach Apex (tier 3).")
            .defineInRange("tier_3_kills", 5000, 1, 1000000);

        EVOLUTION_MESSAGE_INTERVAL = BUILDER
            .comment("Chat milestone message interval (every N kills).")
            .defineInRange("message_interval", 100, 10, 10000);

        BUILDER.pop();
    }

    public static boolean isEvolutionEnabled() { return EVOLUTION_ENABLED.get(); }
    public static int evolutionTier1Kills()    { return EVOLUTION_TIER_1_KILLS.get(); }
    public static int evolutionTier2Kills()    { return EVOLUTION_TIER_2_KILLS.get(); }
    public static int evolutionTier3Kills()    { return EVOLUTION_TIER_3_KILLS.get(); }
    public static int evolutionMessageInterval() { return EVOLUTION_MESSAGE_INTERVAL.get(); }

    public static int killsForTier(int tier) {
        return switch (tier) {
            case 1 -> evolutionTier1Kills();
            case 2 -> evolutionTier2Kills();
            case 3 -> evolutionTier3Kills();
            default -> Integer.MAX_VALUE;
        };
    }

    // ── Origin spawn location ───────────────────────────────────────────
    // Global kill switch for spawn_location teleports, covering built-in,
    // datapack and compat origins alike.

    public static final ModConfigSpec.BooleanValue SPAWN_LOCATION_TELEPORTS_ENABLED;

    static {
        BUILDER.comment(
            "Origin spawn location behaviour.",
            "Origins may declare a spawn_location (e.g. ocean origins, Nether origins)",
            "that teleports the player there when the origin is first picked."
        ).push("spawn_location");

        SPAWN_LOCATION_TELEPORTS_ENABLED = BUILDER
            .comment("Master toggle for ALL origin spawn_location teleports.",
                     "When false, no origin relocates the player on origin pick.",
                     "Built-in, datapack and compat origins all spawn at the world's",
                     "normal spawn point. Overrides ocean_origins.spawn_in_ocean.")
            .define("teleports_enabled", true);

        BUILDER.pop();
    }

    // ── Ocean Origins ───────────────────────────────────────────────────
    // Per-feature toggles for the built-in ocean origins (abyssal, kraken,
    // merling, siren). Both default on.

    public static final ModConfigSpec.BooleanValue OCEAN_ORIGINS_SPAWN_IN_OCEAN;
    public static final ModConfigSpec.BooleanValue OCEAN_ORIGINS_DRIES_OUT;
    public static final ModConfigSpec.IntValue OCEAN_ORIGINS_DRAIN_RATE_TICKS;
    public static final ModConfigSpec.DoubleValue OCEAN_ORIGINS_DROWN_DAMAGE;
    public static final ModConfigSpec.BooleanValue OCEAN_ORIGINS_FISH_DIET_REQUIRED;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> OCEAN_ORIGINS_EXTRA_FISH_FOODS;

    private static final Set<String> OCEAN_ORIGIN_PATHS =
        Set.of("abyssal", "kraken", "merling", "siren");

    static {
        BUILDER.comment(
            "Per-feature toggles for built-in ocean origins (abyssal, kraken, merling, siren)."
        ).push("ocean_origins");

        OCEAN_ORIGINS_SPAWN_IN_OCEAN = BUILDER
            .comment("Teleport ocean origins to a random ocean biome on first origin pick.",
                     "Turn off to let them spawn at the world's normal spawn point.")
            .define("spawn_in_ocean", true);

        OCEAN_ORIGINS_DRIES_OUT = BUILDER
            .comment("Ocean origins slowly lose air while out of water (Minecraft-fish style).",
                     "Turn off to disable the on-land suffocation entirely.")
            .define("dries_out", true);

        OCEAN_ORIGINS_DRAIN_RATE_TICKS = BUILDER
            .comment("Ticks per single air point lost while out of water.",
                     "Default 1 matches vanilla cod and salmon exactly: air starts at 300,",
                     "so 300 ticks (15 seconds) on land before drown damage begins.",
                     "Larger values mean a slower drain: the total land time in seconds is",
                     "roughly (300 * this value) / 20, so 2 gives 30s and 4 gives 1 minute.",
                     "Replaces the per-power drain_rate field in built-in dries_out JSONs.")
            .defineInRange("drain_rate_ticks", 1, 1, 1200);

        OCEAN_ORIGINS_DROWN_DAMAGE = BUILDER
            .comment("Damage applied per second once a dried-out aquatic player's virtual air",
                     "is exhausted. Mirrors WaterAnimal.handleAirSupply cadence (vanilla cod / salmon).",
                     "Default 2.0 (= 1 heart per second). Set to 0 to make dry-out non-lethal.")
            .defineInRange("drown_damage_per_second", 2.0, 0.0, 100.0);

        OCEAN_ORIGINS_FISH_DIET_REQUIRED = BUILDER
            .comment("Pescivore restriction: when true, ocean origins (Abyssal, Kraken, Merling,",
                     "Siren) can only eat items in the neoorigins:fish_foods tag; non-fish food",
                     "is silently cancelled. Set to false to let them eat anything (powered by",
                     "the aquatic_fish_diet power's runtime check on this flag).",
                     "Default true: matches the long-standing pescivore design.")
            .define("fish_diet_required", true);

        OCEAN_ORIGINS_EXTRA_FISH_FOODS = BUILDER
            .comment("Extra items ocean origins (Abyssal, Kraken, Merling, Siren) may ALSO eat",
                     "on top of the neoorigins:fish_foods tag. Additive: an item counts as fish",
                     "food if it is in that tag OR listed here. Use this to whitelist modded fish",
                     "(Aquaculture, Hybrid Aquatic, etc.) without editing a datapack.",
                     "Format: item ids (e.g. \"aquaculture:tuna\") or tags (e.g. \"#aquaculture:fishes\").",
                     "Referenced from JSON via the food_item_in_config_list condition with",
                     "key \"ocean_origins.extra_fish_foods\".")
            .defineListAllowEmpty("extra_fish_foods", List.of(), () -> "", o -> o instanceof String);

        BUILDER.pop();
    }

    /**
     * True if the spawn_location teleport should apply to this origin.
     * Gated first by the global {@link #SPAWN_LOCATION_TELEPORTS_ENABLED}
     * kill switch (covers every origin: built-in, datapack, compat); then,
     * for the four built-in ocean origins, by
     * {@link #OCEAN_ORIGINS_SPAWN_IN_OCEAN}.
     */
    public static boolean shouldApplySpawnLocation(ResourceLocation originId) {
        if (!SPAWN_LOCATION_TELEPORTS_ENABLED.get()) return false;
        if (!NeoOrigins.MOD_ID.equals(originId.getNamespace())) return true;
        if (!OCEAN_ORIGIN_PATHS.contains(originId.getPath())) return true;
        return OCEAN_ORIGINS_SPAWN_IN_OCEAN.get();
    }

    public static boolean isOceanOriginsDriesOutEnabled() {
        return OCEAN_ORIGINS_DRIES_OUT.get();
    }

    /** True if ocean origins are restricted to the {@code neoorigins:fish_foods} tag. */
    public static boolean isOceanOriginsFishDietRequired() {
        return OCEAN_ORIGINS_FISH_DIET_REQUIRED.get();
    }

    /**
     * Extra item ids and {@code #tag} refs ocean origins may ALSO eat, additive to
     * the {@code neoorigins:fish_foods} tag. Entries may be a bare item id or a
     * {@code #}-prefixed tag ref. Consumed by the {@code food_item_in_config_list}
     * condition via config key {@code ocean_origins.extra_fish_foods}.
     */
    @SuppressWarnings("unchecked")
    public static List<String> oceanOriginsExtraFishFoods() {
        return (List<String>) (List<?>) OCEAN_ORIGINS_EXTRA_FISH_FOODS.get();
    }

    /** Master drain rate in ticks per air point lost while an aquatic player is out of water. */
    public static int oceanOriginsDrainRateTicks() {
        return OCEAN_ORIGINS_DRAIN_RATE_TICKS.get();
    }

    /** Drown damage applied per second once virtual air is exhausted. Capped at 0 by config range. */
    public static float oceanOriginsDrownDamage() {
        return OCEAN_ORIGINS_DROWN_DAMAGE.get().floatValue();
    }

    // ── Sun damage helmet protection ────────────────────────────────────
    public static final ModConfigSpec.BooleanValue SUN_HELMET_PROTECTION;
    public static final ModConfigSpec.DoubleValue SUN_HELMET_DURA_DAMAGE_CHANCE;

    static {
        BUILDER.comment(
            "Tuning for the helmet absorption rule on sun-damage origins",
            "(Abyssal, Caveborn, Vampire, Phantom, Warden, etc.). When the",
            "player is in direct sun and wearing a damageable helmet, the",
            "helmet absorbs the burn at the cost of its own durability."
        ).push("sun_damage");

        SUN_HELMET_PROTECTION = BUILDER
            .comment("When true (default), wearing any helmet cancels sun burn for",
                     "sun-damage origins. This is the current, vanilla-like behaviour.",
                     "When false, sun-damage origins burn in daylight even with a",
                     "helmet equipped, and the helmet takes no durability damage",
                     "since it is no longer protecting the player.")
            .define("helmet_protection", true);

        SUN_HELMET_DURA_DAMAGE_CHANCE = BUILDER
            .comment("Per-evaluation chance (0.0 – 1.0) that a damageable helmet takes",
                     "1 durability while the player is in direct sun. The condition is",
                     "evaluated once per condition_passive interval (~1 second), so a",
                     "value of 0.07 averages 1 durability per ~14 seconds, about 40",
                     "minutes of continuous sun for a 165-durability iron helmet.",
                     "Unbreaking still stacks via vanilla hurtAndBreak. Set to 0 so",
                     "helmets never lose durability from sun protection; set to 1 to",
                     "match vanilla zombie/skeleton wear rate (very fast).",
                     "Fire-resistant helmets (the minecraft:fire_resistant",
                     "component — netherite in vanilla) ignore this value and",
                     "never wear out from sun protection, as do unbreakable ones.",
                     "To make players burn even while wearing a helmet, set",
                     "helmet_protection = false above.")
            .defineInRange("helmet_dura_damage_chance", 0.07, 0.0, 1.0);

        BUILDER.pop();
    }

    public static boolean sunHelmetProtection() {
        return SUN_HELMET_PROTECTION.get();
    }

    public static float sunHelmetDuraDamageChance() {
        return SUN_HELMET_DURA_DAMAGE_CHANCE.get().floatValue();
    }

    // ── Mount Power ─────────────────────────────────────────────────────
    // Consent mode for player-to-player mounting.

    public enum ConsentMode { ALWAYS, PROMPT, TEAM }

    public static final ModConfigSpec.EnumValue<ConsentMode> MOUNT_CONSENT_MODE;
    public static final ModConfigSpec.IntValue MOUNT_REQUEST_TIMEOUT_SECONDS;

    static {
        BUILDER.comment(
            "Mount power settings. Controls how player-to-player mounting",
            "consent works. ALWAYS: mount any player without consent.",
            "PROMPT: target must click [ACCEPT] or run /neoorigins mount accept.",
            "TEAM: auto-allow if both players share a team (FTB Teams or",
            "Open Parties and Claims); falls back to ALWAYS if no team mod is loaded."
        ).push("mount");

        MOUNT_CONSENT_MODE = BUILDER
            .comment("Consent mode for mounting other players.")
            .defineEnum("consent_mode", ConsentMode.ALWAYS);

        MOUNT_REQUEST_TIMEOUT_SECONDS = BUILDER
            .comment("Seconds before a mount request expires (only used in PROMPT mode).")
            .defineInRange("request_timeout_seconds", 30, 5, 300);

        BUILDER.pop();
    }

    public static ConsentMode mountConsentMode() { return MOUNT_CONSENT_MODE.get(); }
    public static int mountRequestTimeoutSeconds() { return MOUNT_REQUEST_TIMEOUT_SECONDS.get(); }

    // ── Friendly-fire filter ────────────────────────────────────────────
    // Controls which entity types are excluded from origin AOE attacks
    // (poison stings, fire bursts, ink shots, etc.). The AOE action only
    // affects mobs that are NOT excluded. Owned tames + tracked minions
    // are always protected when their toggles are on; the broader Animal /
    // Villager / IronGolem toggles let pack authors decide whether passive
    // mob types are valid combat targets.

    public static final ModConfigSpec.BooleanValue FF_PROTECT_OWNED_PETS;
    public static final ModConfigSpec.BooleanValue FF_PROTECT_MINIONS;
    public static final ModConfigSpec.BooleanValue FF_PROTECT_ANIMALS;
    public static final ModConfigSpec.BooleanValue FF_PROTECT_VILLAGERS;
    public static final ModConfigSpec.BooleanValue FF_PROTECT_IRON_GOLEMS;

    static {
        BUILDER.comment(
            "Friendly-fire filter for origin area-of-effect actions (poison sting,",
            "fire burst, ink shot, etc.). When a toggle is true, that category of",
            "mob is excluded from the AOE target list and will not take effects",
            "or damage from the player's own abilities."
        ).push("friendly_fire");

        FF_PROTECT_OWNED_PETS = BUILDER
            .comment("Protect TamableAnimals owned by the casting player (wolves, cats, etc.).")
            .define("protect_owned_pets", true);

        FF_PROTECT_MINIONS = BUILDER
            .comment("Protect mobs tracked as minions of the casting player",
                     "(necromancer skeletons, beastmaster summons, etc.).")
            .define("protect_minions", true);

        FF_PROTECT_ANIMALS = BUILDER
            .comment("Protect ALL passive animals (sheep, cow, pig, horse, fox, ...).",
                     "Default false: an active combat AOE should hit livestock; otherwise",
                     "abilities like Hiveling Sting silently no-op against passive mobs.",
                     "Turn on if your pack treats farm animals as untouchable allies.")
            .define("protect_animals", false);

        FF_PROTECT_VILLAGERS = BUILDER
            .comment("Protect villagers and wandering traders from origin AOEs.",
                     "Default true: avoids accidentally aggroing or killing your trade hub.")
            .define("protect_villagers", true);

        FF_PROTECT_IRON_GOLEMS = BUILDER
            .comment("Protect iron golems from origin AOEs.",
                     "Default true: village-built golems represent player investment and",
                     "their protection is consistent with vanilla golem AI rules.")
            .define("protect_iron_golems", true);

        BUILDER.pop();
    }

    public static boolean ffProtectOwnedPets()   { return FF_PROTECT_OWNED_PETS.get(); }
    public static boolean ffProtectMinions()     { return FF_PROTECT_MINIONS.get(); }
    public static boolean ffProtectAnimals()     { return FF_PROTECT_ANIMALS.get(); }
    public static boolean ffProtectVillagers()   { return FF_PROTECT_VILLAGERS.get(); }
    public static boolean ffProtectIronGolems()  { return FF_PROTECT_IRON_GOLEMS.get(); }

    // ── Armor Classes ───────────────────────────────────────────────────
    // Configurable armor categories used by restrict_armor powers.
    // Items can be specified as item IDs (e.g. "minecraft:diamond_helmet")
    // or tags (e.g. "#minecraft:trimmable_armor"). These lists supplement
    // the neoorigins:heavy_armor and neoorigins:light_armor item tags —
    // items in EITHER the tag or the config list are considered part of
    // that armor class.

    public static final ModConfigSpec.ConfigValue<List<? extends String>> HEAVY_ARMOR_ITEMS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> LIGHT_ARMOR_ITEMS;

    static {
        BUILDER.comment(
            "Armor class definitions. Items listed here are ADDED to the",
            "neoorigins:heavy_armor / neoorigins:light_armor item tags.",
            "Use this to add modded armor to the correct class without a datapack.",
            "Format: item IDs (e.g. \"modid:my_helmet\") or tags (e.g. \"#modid:my_armor_tag\")."
        ).push("armor_classes");

        HEAVY_ARMOR_ITEMS = BUILDER
            .comment("Additional items/tags to treat as heavy armor.",
                     "Default heavy armor (iron, gold, diamond, netherite) is defined in the",
                     "neoorigins:heavy_armor item tag and does not need to be listed here.")
            .defineListAllowEmpty("heavy_armor", List.of(), () -> "", o -> o instanceof String);

        LIGHT_ARMOR_ITEMS = BUILDER
            .comment("Additional items/tags to treat as light armor.",
                     "Default light armor (leather + chainmail) is defined in the",
                     "neoorigins:light_armor item tag and does not need to be listed here.")
            .defineListAllowEmpty("light_armor", List.of(), () -> "", o -> o instanceof String);

        BUILDER.pop();
    }

    @SuppressWarnings("unchecked")
    public static List<String> getHeavyArmorItems() { return (List<String>) (List<?>) HEAVY_ARMOR_ITEMS.get(); }
    @SuppressWarnings("unchecked")
    public static List<String> getLightArmorItems() { return (List<String>) (List<?>) LIGHT_ARMOR_ITEMS.get(); }

    // ── Ability cooldowns ───────────────────────────────────────────────
    public static final ModConfigSpec.BooleanValue CREATIVE_NO_COOLDOWN;

    static {
        BUILDER.comment(
            "Active-ability cooldown behaviour."
        ).push("cooldowns");

        CREATIVE_NO_COOLDOWN = BUILDER
            .comment("When true, players in Creative mode ignore active-ability cooldowns",
                     "entirely: every keybind / on-hit / on-kill power can fire without",
                     "waiting. Cooldowns still apply normally in Survival/Adventure, so a",
                     "creative-mode player switching back to survival resumes any in-flight",
                     "cooldown. Useful for testing powers without spam-waiting.")
            .define("creative_no_cooldown", true);

        BUILDER.pop();
    }

    /**
     * True when {@code player} should bypass active-ability cooldowns: the
     * {@link #CREATIVE_NO_COOLDOWN} toggle is on AND the player is in Creative
     * ({@code instabuild} is creative-only). Centralised so every cooldown gate
     * shares one rule.
     */
    public static boolean creativeCooldownBypass(net.minecraft.world.entity.player.Player player) {
        return CREATIVE_NO_COOLDOWN.get() && player.getAbilities().instabuild;
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
