package com.cyberday1.neoorigins;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
        "sculkborn", "enderite", "necromancer", "gorgon", "automaton", "kraken",
        "warden", "dwarf", "breeze", "vampire",
        "air_mage", "darkness_mage", "earth_mage", "fire_mage", "gravity_mage",
        "water_mage", "monster_tamer"
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

    // ── Disabled Classes ────────────────────────────────────────────────
    // Each built-in class can be disabled here. Disabled classes are
    // removed after data loading. If ALL classes are disabled, the class
    // selection screen is skipped entirely.

    private static final String[] BUILT_IN_CLASSES = {
        "class_warrior", "class_archer", "class_miner", "class_beastmaster",
        "class_explorer", "class_sentinel", "class_herbalist", "class_scout",
        "class_berserker", "class_titan", "class_rogue", "class_lumberjack",
        "class_blacksmith", "class_cook", "class_merchant", "class_cleric",
        "class_nitwit"
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

    // ── Power Overrides ───────────────────────────────────────────────────
    // Allows modpack creators to tweak specific power parameters without
    // creating custom datapacks. Edit the values directly — only values you
    // change from their defaults are applied as overrides.

    /** power-id → field-name → config value. Only entries changed from default are applied. */
    static final Map<String, Map<String, ModConfigSpec.ConfigValue<?>>> POWER_OVERRIDES = new LinkedHashMap<>();
    private static String _cp; // current power being registered

    private static void p(String power) { _cp = power; BUILDER.push(power); POWER_OVERRIDES.put("neoorigins:" + power, new LinkedHashMap<>()); }
    private static void f(String field, double def, double min, double max)   { POWER_OVERRIDES.get("neoorigins:" + _cp).put(field, BUILDER.defineInRange(field, def, min, max)); }
    private static void fi(String field, int def, int min, int max)           { POWER_OVERRIDES.get("neoorigins:" + _cp).put(field, BUILDER.defineInRange(field, def, min, max)); }
    private static void fb(String field, boolean def)                          { POWER_OVERRIDES.get("neoorigins:" + _cp).put(field, BUILDER.define(field, def)); }
    private static void ep() { BUILDER.pop(); }

    static {
        BUILDER.comment(
            "Per-power parameter overrides.",
            "Edit any value below to override it. Only values changed from their default are applied.",
            "20 ticks = 1 second. Overrides take effect on /reload or restart."
        ).push("power_overrides");

        // ── Abyssal ──
        p("abyssal_thorns_aura");       f("return_ratio", 0.3, 0, 10); ep();
        p("abyssal_regen_in_water");    f("amount_per_second", 2.0, 0, 100); ep();
        p("abyssal_daylight_damage");   f("damage_per_second", 1.5, 0, 100); fb("ignite", false); ep();
        p("abyssal_land_speed_penalty");f("amount", -0.1, -1, 1); ep();
        p("abyssal_summon_guardian");   fi("max_count", 2, 1, 100); fi("cooldown_ticks", 400, 0, 72000); fi("hunger_cost", 5, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();

        // ── Automaton ──
        p("automaton_iron_frame");      f("amount", 8.0, -100, 100); ep();
        p("automaton_heavy_chassis");   f("amount", -0.15, -1, 1); ep();
        p("automaton_knockback_resist");f("amount", 0.4, -1, 1); ep();
        p("automaton_no_hunger");       f("multiplier", 0.0, 0, 10); ep();
        p("automaton_rigid_joints");    f("multiplier", 0.25, 0, 10); ep();

        // ── Avian ──
        p("avian_no_hunger_sprint");    f("multiplier", 0.25, 0, 10); ep();

        // ── Blazeling ──
        p("blazeling_water_damage");    f("multiplier", 2.5, 0, 100); ep();

        // ── Breeze ──
        p("breeze_wind_charge");        f("speed", 2.0, 0, 10); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("breeze_wind_dash");          f("strength", 2.5, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("breeze_light_frame");        f("scale", 0.9, 0.1, 10); ep();
        p("breeze_reduced_health");     f("amount", -8.0, -100, 100); ep();
        p("breeze_speed_boost");        f("amount", 0.15, -1, 10); ep();

        // ── Caveborn ──
        p("caveborn_daylight_damage");  f("damage_per_second", 1.0, 0, 100); fb("ignite", false); ep();
        p("caveborn_mining_speed");     f("multiplier", 2.0, 0, 100); ep();
        p("caveborn_small_frame");      f("scale", 0.85, 0.1, 10); ep();

        // ── Cinderborn ──
        p("cinderborn_fireball");       f("speed", 1.2, 0, 10); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("cinderborn_lava_regen");     f("amount_per_second", 3.0, 0, 100); ep();
        p("cinderborn_natural_armor");  f("amount", 6.0, -100, 100); ep();
        p("cinderborn_water_weakness"); f("multiplier", 3.0, 0, 100); ep();

        // ── Draconic ──
        p("draconic_active_fireball");  f("speed", 1.5, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("draconic_size");             f("scale", 1.2, 0.1, 10); ep();
        p("draconic_attack_bonus");     f("amount", 2.0, -100, 100); ep();
        p("draconic_hunger_drain");     f("multiplier", 1.5, 0, 10); ep();
        p("draconic_water_weakness");   f("multiplier", 2.0, 0, 100); ep();

        // ── Dwarf ──
        p("dwarf_compact_frame");       f("scale", 0.8, 0.1, 10); ep();
        p("dwarf_stout_constitution");  f("amount", 4.0, -100, 100); ep();
        p("dwarf_sturdy_legs");         f("amount", -0.15, -1, 1); ep();
        p("dwarf_short_reach");         f("amount", -0.5, -10, 10); ep();
        p("dwarf_stonecunning");        f("multiplier", 1.25, 0, 100); ep();
        p("dwarf_mining_hunger");       f("multiplier", 0.75, 0, 10); ep();

        // ── Elytrian ──
        p("elytrian_elytra_boost");     f("strength", 1.5, 0, 10); fi("cooldown_ticks", 40, 0, 72000); ep();

        // ── Enderian ──
        p("enderian_teleport");         f("range", 50.0, 1, 256); fi("cooldown_ticks", 60, 0, 72000); ep();
        p("enderian_water_damage");     f("damage_per_second", 2.0, 0, 100); fb("include_rain", true); ep();

        // ── Enderite ──
        p("enderite_teleport");         f("range", 32.0, 1, 256); fi("cooldown_ticks", 60, 0, 72000); ep();
        p("enderite_phase");            fi("max_depth", 16, 1, 256); fi("cooldown_ticks", 200, 0, 72000); fi("hunger_cost", 0, 0, 100); ep();
        p("enderite_attack_bonus");     f("amount", 3.0, -100, 100); ep();
        p("enderite_water_damage");     f("multiplier", 3.0, 0, 100); ep();
        p("enderite_daylight_weakness");f("multiplier", 1.5, 0, 100); ep();

        // ── Feline ──
        p("feline_active_launch");      f("power", 2.2, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("feline_speed_boost");        f("amount", 0.1, -1, 10); ep();
        p("feline_hunger_drain");       f("multiplier", 1.3, 0, 10); ep();
        p("feline_water_weakness");     f("multiplier", 1.5, 0, 100); ep();

        // ── Frostborn ──
        p("frostborn_freeze_aura");     fi("amplifier", 3, 0, 255); fi("duration_ticks", 100, 1, 72000); f("radius", 6.0, 0, 64); fi("cooldown_ticks", 200, 0, 72000); ep();
        p("frostborn_ice_walk");        f("max_distance", 5.0, 1, 64); fi("cooldown_ticks", 20, 0, 72000); ep();
        p("frostborn_natural_armor");   f("amount", 4.0, -100, 100); ep();
        p("frostborn_fire_weakness");   f("multiplier", 2.0, 0, 100); ep();
        p("frostborn_nether_damage");   f("damage_per_second", 2.0, 0, 100); ep();

        // ── Golem ──
        p("golem_natural_armor");       f("amount", 6.0, -100, 100); ep();
        p("golem_knockback_resist");    f("amount", 0.8, -1, 1); ep();
        p("golem_size");                f("scale", 1.3, 0.1, 10); ep();
        p("golem_slow_movement");       f("amount", -0.25, -1, 1); ep();
        p("golem_fire_weakness");       f("multiplier", 1.8, 0, 100); ep();

        // ── Gorgon ──
        p("gorgon_petrifying_gaze");    fi("amplifier", 4, 0, 255); fi("duration_ticks", 100, 1, 72000); f("radius", 6.0, 0, 64); fi("cooldown_ticks", 300, 0, 72000); ep();
        p("gorgon_stone_fists");        f("amount", 4.0, -100, 100); ep();
        p("gorgon_granite_hide");       f("amount", 6.0, -100, 100); ep();
        p("gorgon_knockback_resist");   f("amount", 0.5, -1, 1); ep();
        p("gorgon_size");               f("scale", 1.15, 0.1, 10); ep();
        p("gorgon_heavy_frame");        f("amount", -0.2, -1, 1); ep();
        p("gorgon_hunger_drain");       f("multiplier", 1.5, 0, 10); ep();

        // ── Hiveling ──
        p("hiveling_sting");            f("speed", 1.0, 0, 10); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("hiveling_crop_growth");      fi("radius", 6, 1, 64); fi("tick_interval", 30, 1, 72000); fi("growths_per_interval", 3, 1, 100); ep();
        p("hiveling_size");             f("scale", 0.6, 0.1, 10); ep();
        p("hiveling_reduced_health");   f("amount", -6.0, -100, 100); ep();
        p("hiveling_hunger_drain");     f("multiplier", 1.5, 0, 10); ep();

        // ── Inchling ──
        p("inchling_size");             f("scale", 0.25, 0.1, 10); ep();
        p("inchling_speed_boost");      f("amount", 0.15, -1, 10); ep();
        p("inchling_reduced_health");   f("amount", -10.0, -100, 100); ep();
        p("inchling_hunger_efficiency");f("multiplier", 0.5, 0, 10); ep();

        // ── Kraken ──
        p("kraken_tentacle_lash");      fi("amplifier", 2, 0, 255); fi("duration_ticks", 80, 1, 72000); f("radius", 5.0, 0, 64); fi("cooldown_ticks", 160, 0, 72000); ep();
        p("kraken_ink_shot");           f("speed", 1.2, 0, 10); fi("cooldown_ticks", 120, 0, 72000); ep();
        p("kraken_massive");            f("scale", 1.3, 0.1, 10); ep();
        p("kraken_pressure_armor");     f("amount", 6.0, -100, 100); ep();
        p("kraken_deep_current");       f("amount", 0.8, -1, 10); ep();
        p("kraken_regen_in_water");     f("amount_per_second", 2.0, 0, 100); ep();
        p("kraken_summon_guardian");    fi("max_count", 2, 1, 100); fi("cooldown_ticks", 400, 0, 72000); fi("hunger_cost", 5, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();
        p("kraken_beached");            f("amount", -0.3, -1, 1); ep();
        p("kraken_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();

        // ── Merling ──
        p("merling_aquatic_speed");     f("amount", 0.6, -1, 10); ep();
        p("merling_land_slowdown");     f("amount", -0.1, -1, 1); ep();

        // ── Necromancer ──
        p("necromancer_summon_skeleton");fi("max_count", 3, 1, 100); fi("cooldown_ticks", 400, 0, 72000); fi("hunger_cost", 4, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();
        p("necromancer_summon_wither"); fi("max_count", 2, 1, 100); fi("cooldown_ticks", 600, 0, 72000); fi("hunger_cost", 6, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();
        p("necromancer_reduced_health");f("amount", -6.0, -100, 100); ep();
        p("necromancer_slow_regen");    f("multiplier", 0.4, 0, 10); ep();
        p("necromancer_daylight_damage");f("damage_per_second", 1.5, 0, 100); ep();

        // ── Phantom ──
        p("phantom_form");              fb("invisibility", true); fb("no_gravity", true); ep();

        // ── Piglin ──
        p("piglin_attack_bonus");       f("amount", 2.0, -100, 100); ep();
        p("piglin_soul_fire_damage");   f("damage_per_second", 1.0, 0, 100); ep();

        // ── Revenant ──
        p("revenant_active_bolt");      f("speed", 1.2, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("revenant_active_phase");     fi("max_depth", 8, 1, 256); fi("cooldown_ticks", 60, 0, 72000); fi("hunger_cost", 0, 0, 100); ep();
        p("revenant_slow_regen");       f("multiplier", 0.4, 0, 10); ep();
        p("revenant_daylight_damage");  f("damage_per_second", 2.0, 0, 100); ep();

        // ── Sculkborn ──
        p("sculkborn_sonic_bolt");      f("speed", 1.5, 0, 10); fi("cooldown_ticks", 120, 0, 72000); ep();
        p("sculkborn_darkness_aura");   fi("amplifier", 0, 0, 255); fi("duration_ticks", 200, 1, 72000); f("radius", 8.0, 0, 64); fi("cooldown_ticks", 300, 0, 72000); ep();
        p("sculkborn_natural_armor");   f("amount", 8.0, -100, 100); ep();
        p("sculkborn_knockback_resist");f("amount", 0.5, -1, 1); ep();
        p("sculkborn_reduced_health");  f("amount", -4.0, -100, 100); ep();
        p("sculkborn_slow_movement");   f("amount", -0.15, -1, 1); ep();
        p("sculkborn_daylight_damage"); f("damage_per_second", 2.0, 0, 100); ep();

        // ── Shulk ──
        p("shulk_natural_armor");       f("amount", 8.0, -100, 100); ep();
        p("shulk_slow_movement");       f("amount", -0.25, -1, 1); ep();

        // ── Siren ──
        p("siren_aquatic_speed");       f("amount", 0.8, -1, 10); ep();
        p("siren_regen_in_water");      f("amount_per_second", 1.0, 0, 100); ep();
        p("siren_land_slowdown");       f("amount", -0.15, -1, 1); ep();
        p("siren_reduced_health");      f("amount", -4.0, -100, 100); ep();

        // ── Sporeling ──
        p("sporeling_spore_cloud");     fi("amplifier", 1, 0, 255); fi("duration_ticks", 100, 1, 72000); f("radius", 5.0, 0, 64); fi("cooldown_ticks", 240, 0, 72000); ep();
        p("sporeling_natural_armor");   f("amount", 4.0, -100, 100); ep();
        p("sporeling_slow_movement");   f("amount", -0.1, -1, 1); ep();
        p("sporeling_daylight_damage"); f("damage_per_second", 1.0, 0, 100); ep();

        // ── Stoneguard ──
        p("stoneguard_thorns_aura");    f("return_ratio", 0.2, 0, 10); ep();
        p("stoneguard_natural_armor");  f("amount", 3.0, -100, 100); ep();
        p("stoneguard_knockback_resist");f("amount", 0.5, -1, 1); ep();
        p("stoneguard_active_glowstone");f("max_distance", 5.0, 1, 64); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("stoneguard_stone_mining");   f("multiplier", 2.0, 0, 100); ep();
        p("stoneguard_slow_movement");  f("amount", -0.1, -1, 1); ep();
        p("stoneguard_no_mob_spawns");  fi("radius", 24, 1, 256); ep();

        // ── Strider ──
        p("strider_lava_regen");        f("amount_per_second", 2.0, 0, 100); ep();
        p("strider_natural_armor");     f("amount", 6.0, -100, 100); ep();
        p("strider_water_weakness");    f("multiplier", 3.0, 0, 100); ep();

        // ── Sylvan ──
        p("sylvan_active_root");        fi("amplifier", 5, 0, 255); fi("duration_ticks", 80, 1, 72000); f("radius", 6.0, 0, 64); fi("cooldown_ticks", 200, 0, 72000); ep();
        p("sylvan_crop_growth");        fi("radius", 4, 1, 64); fi("tick_interval", 40, 1, 72000); fi("growths_per_interval", 2, 1, 100); ep();
        p("sylvan_regen_in_water");     f("amount_per_second", 1.0, 0, 100); ep();
        p("sylvan_nether_damage");      f("damage_per_second", 1.0, 0, 100); ep();

        // ── Tiny ──
        p("tiny_size");                 f("scale", 0.5, 0.1, 10); ep();
        p("tiny_speed_boost");          f("amount", 0.2, -1, 10); ep();
        p("tiny_item_magnetism");       f("radius", 4.0, 0, 64); ep();
        p("tiny_attack_penalty");       f("amount", -2.0, -100, 100); ep();
        p("tiny_hunger_drain");         f("multiplier", 1.8, 0, 10); ep();

        // ── Umbral ──
        p("umbral_shadow_orb");         fi("max_orbs", 4, 1, 100); f("radius", 28.0, 1, 128); fi("cooldown_ticks", 60, 0, 72000); fi("tick_interval", 20, 1, 72000); ep();
        p("umbral_active_dash");        f("power", 2.0, 0, 10); fi("cooldown_ticks", 60, 0, 72000); ep();
        p("umbral_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();

        // ── Vampire ──
        p("vampire_attack_bonus");      f("amount", 2.0, -100, 100); ep();
        p("vampire_speed_boost");       f("amount", 0.15, -1, 10); ep();
        p("vampire_daylight_damage");   f("damage_per_second", 2.0, 0, 100); ep();
        p("vampire_slow_regen");        f("multiplier", 0.4, 0, 10); ep();
        p("vampire_water_weakness");    f("multiplier", 2.0, 0, 100); ep();

        // ── Verdant ──
        p("verdant_harvest_bonus");     fi("extra_drops", 1, 0, 100); ep();
        p("verdant_nether_damage");     f("damage_per_second", 2.0, 0, 100); ep();

        // ── Voidwalker ──
        p("voidwalker_active_teleport");f("range", 24.0, 1, 256); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("voidwalker_active_phase");   fi("max_depth", 10, 1, 256); fi("cooldown_ticks", 80, 0, 72000); fi("hunger_cost", 3, 0, 100); ep();
        p("voidwalker_water_damage");   f("multiplier", 1.75, 0, 100); ep();

        // ── Warden ──
        p("warden_sonic_boom");         f("speed", 1.0, 0, 10); fi("cooldown_ticks", 400, 0, 72000); ep();
        p("warden_strength");           f("amount", 4.0, -100, 100); ep();
        p("warden_ancient_hide");       f("amount", 10.0, -100, 100); ep();
        p("warden_hulking_frame");      f("scale", 1.15, 0.1, 10); ep();
        p("warden_lumbering");          f("amount", -0.30, -1, 1); ep();
        p("warden_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();

        // ── Classes ──
        p("class_warrior_damage");      f("amount", 1.0, -100, 100); ep();
        p("class_warrior_knockback_resist");f("amount", 0.3, -1, 1); ep();
        p("class_archer_agility");      f("amount", 0.15, -1, 10); ep();
        p("class_miner_speed");         f("multiplier", 1.5, 0, 100); ep();
        p("class_miner_efficiency");    f("multiplier", 0.7, 0, 10); ep();
        p("class_beastmaster_diffusal");f("radius", 16.0, 0, 128); ep();
        p("class_beastmaster_potions"); f("multiplier", 1.5, 0, 10); ep();
        p("class_explorer_stamina");    f("multiplier", 0.6, 0, 10); ep();
        p("class_sentinel_armor");      f("amount", 4.0, -100, 100); ep();
        p("class_sentinel_knockback_resist");f("amount", 0.2, -1, 1); ep();
        p("class_sentinel_thorns");     f("return_ratio", 0.25, 0, 10); ep();
        p("class_herbalist_growth");    fi("radius", 5, 1, 64); fi("tick_interval", 40, 1, 72000); fi("growths_per_interval", 2, 1, 100); ep();
        p("class_herbalist_harvest_bonus");fi("extra_drops", 1, 0, 100); ep();
        p("class_scout_speed");         f("amount", 0.2, -1, 10); ep();
        p("class_berserker_damage");    f("amount", 3.0, -100, 100); ep();
        p("class_berserker_hunger");    f("multiplier", 1.5, 0, 10); ep();
        p("class_titan_size");          f("scale", 1.25, 0.1, 10); ep();
        p("class_titan_health");        f("amount", 4.0, -100, 100); ep();
        p("class_titan_reach");         f("amount", 0.5, -10, 10); ep();
        p("class_rogue_sneaky");        f("detection_multiplier", 0.3, 0, 10); ep();
        p("class_rogue_stealth");       fi("activation_ticks", 200, 0, 72000); ep();
        p("class_lumberjack_tree_felling");fi("max_blocks", 64, 1, 1024); ep();
        p("class_lumberjack_bonus_planks");fi("bonus_count", 2, 0, 100); ep();
        p("class_merchant_trades");     fi("scan_interval", 40, 1, 72000); f("radius", 8.0, 0, 128); ep();
        p("class_cleric_enchanting");   fi("bonus_levels", 5, 0, 100); ep();
        p("class_cleric_potions");      f("multiplier", 2.0, 0, 10); ep();
        p("class_cook_food");           f("saturation_bonus", 0.4, 0, 10); ep();
        p("class_cook_smoker_xp");      f("multiplier", 2.0, 0, 100); ep();
        p("class_blacksmith_quality");  fi("unbreaking_level", 1, 0, 10); ep();
        p("class_blacksmith_repairs");  f("cost_multiplier", 0.5, 0, 10); ep();

        BUILDER.pop(); // power_overrides
    }

    // ── Random Origin Assignment ─────────────────────────────────────────
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

    public static final ModConfigSpec SPEC = BUILDER.build();

    public static RandomMode getRandomMode() {
        return RANDOM_MODE.get();
    }

    // ── Parsed dimension restriction cache ──────────────────────────────
    // Rebuilt on each access from the TOML list to keep in sync with config reloads.

    private static volatile Map<String, Set<ResourceKey<Level>>> parsedRestrictions;
    private static volatile int lastConfigHash;

    /**
     * Returns true if the given power ID is restricted in the player's current dimension.
     */
    public static boolean isPowerRestrictedInDimension(ResourceLocation powerId, ResourceKey<Level> dimension) {
        Map<String, Set<ResourceKey<Level>>> map = getParsedRestrictions();
        Set<ResourceKey<Level>> denied = map.get(powerId.toString());
        return denied != null && denied.contains(dimension);
    }

    /**
     * Version counter for the dimension-restrictions config. Bumps whenever the rules list
     * content changes. Used by ActiveOriginService's per-player power cache for invalidation.
     */
    public static int restrictionsVersion() {
        return DIMENSION_RESTRICTIONS.get().hashCode();
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
                        dimSet.add(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(trimmed)));
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

    // ── Power override lookup ──────────────────────────────────────────

    /**
     * Returns config overrides for the given power ID as field→value pairs.
     * Only returns fields whose config value differs from the default.
     * Returns null if there are no overrides for this power.
     */
    public static Map<String, Object> getPowerOverrides(String powerId) {
        Map<String, ModConfigSpec.ConfigValue<?>> fields = POWER_OVERRIDES.get(powerId);
        if (fields == null) return null;

        Map<String, Object> changed = new LinkedHashMap<>();
        for (var entry : fields.entrySet()) {
            ModConfigSpec.ConfigValue<?> cv = entry.getValue();
            Object val = cv.get();
            Object def = cv.getDefault();
            if (!val.equals(def)) {
                changed.put(entry.getKey(), val);
            }
        }
        return changed.isEmpty() ? null : changed;
    }
}
