package com.cyberday1.neoorigins.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-power parameter overrides (COMMON, {@code config/neoorigins/power_overrides.toml}).
 *
 * <p>Allows modpack creators to tweak specific power parameters without
 * creating custom datapacks. Only values changed from their defaults are
 * applied as overrides, merged into the power JSON by
 * {@code PowerDataManager.applyConfigOverrides} BEFORE codec parsing and
 * BEFORE {@code LegacyPowerTypeAliases.apply} — that ordering is load-bearing
 * (override values must be visible to the alias remap step).
 *
 * <p>Part of the 2.2.2 config split — see {@link GameplayConfig},
 * {@link AdminConfig} and {@link ContentTogglesConfig}. The file keeps the
 * {@code [power_overrides.*]} table prefix so legacy values migrate 1:1.
 */
public final class PowerOverridesConfig {

    private PowerOverridesConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** power-id → field-name → config value. Only entries changed from default are applied. */
    static final Map<String, Map<String, ModConfigSpec.ConfigValue<?>>> POWER_OVERRIDES = new LinkedHashMap<>();

    /** Inclusive numeric bound a {@code defineInRange} field was registered with. */
    public record NumericRange(double min, double max) {}

    /**
     * power-id → field-name → the (min,max) the field was registered with.
     * {@link ModConfigSpec.ConfigValue} only exposes {@code getDefault()}, so the
     * range is otherwise discarded after spec build; the 2.1 creator needs it to
     * render sliders/clamp inputs for codec-only powers. Populated alongside
     * {@link #POWER_OVERRIDES} at registration. */
    static final Map<String, Map<String, NumericRange>> POWER_RANGES = new LinkedHashMap<>();
    private static String _cp; // current power being registered

    private static void p(String power) {
        _cp = power; BUILDER.push(power);
        POWER_OVERRIDES.put("neoorigins:" + power, new LinkedHashMap<>());
        POWER_RANGES.put("neoorigins:" + power, new LinkedHashMap<>());
    }
    private static void f(String field, double def, double min, double max)   { POWER_OVERRIDES.get("neoorigins:" + _cp).put(field, BUILDER.defineInRange(field, def, min, max)); POWER_RANGES.get("neoorigins:" + _cp).put(field, new NumericRange(min, max)); }
    private static void fi(String field, int def, int min, int max)           { POWER_OVERRIDES.get("neoorigins:" + _cp).put(field, BUILDER.defineInRange(field, def, min, max)); POWER_RANGES.get("neoorigins:" + _cp).put(field, new NumericRange(min, max)); }
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
        p("abyssal_aquatic_speed");     f("amount", 0.8, -1, 10); ep();
        p("abyssal_land_speed_penalty");f("amount", -0.1, -1, 1); ep();
        p("abyssal_summon_guardian");   fi("max_count", 2, 1, 100); fi("cooldown_ticks", 400, 0, 72000); fi("hunger_cost", 5, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();

        // ── Automaton ──
        p("automaton_iron_frame");      fi("amplifier", 0, 0, 4); ep();
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
        p("breeze_wind_dash");          f("power", 2.5, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("breeze_light_frame");        f("scale", 0.9, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();
        p("breeze_reduced_health");     f("amount", -8.0, -100, 100); ep();
        p("breeze_speed_boost");        f("amount", 0.15, -1, 10); ep();

        // ── Caveborn ──
        p("caveborn_daylight_damage");  f("damage_per_second", 1.0, 0, 100); fb("ignite", false); ep();
        p("caveborn_mining_speed");     f("multiplier", 2.0, 0, 100); ep();
        p("caveborn_small_frame");      f("scale", 0.85, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();

        // ── Cinderborn ──
        p("cinderborn_fireball");       f("speed", 1.2, 0, 10); fi("cooldown_ticks", 100, 0, 72000); ep();
        // (cinderborn_lava_regen heal amount is nested in entity_action;
        // shallow power_overrides can't reach it. Edit JSON directly to retune.)
        p("cinderborn_natural_armor");  fi("amplifier", 0, 0, 4); ep();
        p("cinderborn_water_weakness"); f("multiplier", 3.0, 0, 100); ep();

        // ── Draconic ──
        p("draconic_active_fireball");  f("speed", 1.5, 0, 10); fi("cooldown_ticks", 80, 0, 72000); ep();
        p("draconic_size");             f("scale", 1.2, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();
        p("draconic_attack_bonus");     f("amount", 2.0, -100, 100); ep();
        p("draconic_hunger_drain");     f("multiplier", 1.5, 0, 10); ep();
        p("draconic_water_weakness");   f("multiplier", 2.0, 0, 100); ep();

        // ── Dwarf ──
        p("dwarf_compact_frame");       f("scale", 0.8, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();
        p("dwarf_stout_constitution");  fi("amplifier", 0, 0, 4); ep();
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
        p("frostborn_natural_armor");   fi("amplifier", 0, 0, 4); ep();
        p("frostborn_fire_weakness");   f("multiplier", 2.0, 0, 100); ep();
        p("frostborn_nether_damage");   f("damage_per_second", 2.0, 0, 100); ep();

        // ── Golem ──
        p("golem_natural_armor");       fi("amplifier", 0, 0, 4); ep();
        p("golem_knockback_resist");    f("amount", 0.8, -1, 1); ep();
        p("golem_size");                f("scale", 1.3, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();
        p("golem_slow_movement");       f("amount", -0.25, -1, 1); ep();
        p("golem_fire_weakness");       f("multiplier", 1.8, 0, 100); ep();

        // ── Gorgon ──
        p("gorgon_petrifying_gaze");    fi("amplifier", 4, 0, 255); fi("duration_ticks", 100, 1, 72000); f("radius", 6.0, 0, 64); fi("cooldown_ticks", 300, 0, 72000); ep();
        p("gorgon_stone_fists");        f("amount", 4.0, -100, 100); ep();
        p("gorgon_granite_hide");       fi("amplifier", 0, 0, 4); ep();
        p("gorgon_knockback_resist");   f("amount", 0.5, -1, 1); ep();
        p("gorgon_size");               f("scale", 1.15, 0.1, 10); fb("modify_reach", true); f("reach_bonus", 0.0, -10, 10); ep();
        p("gorgon_heavy_frame");        f("amount", -0.2, -1, 1); ep();
        p("gorgon_hunger_drain");       f("multiplier", 1.5, 0, 10); ep();

        // ── Hiveling ──
        p("hiveling_sting");            f("speed", 1.0, 0, 10); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("hiveling_crop_growth");      fi("radius", 6, 1, 64); fi("tick_interval", 30, 1, 72000); fi("growths_per_interval", 3, 1, 100); ep();
        p("hiveling_size");             f("scale", 0.6, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.0, -10, 10); ep();
        p("hiveling_reduced_health");   f("amount", -6.0, -100, 100); ep();
        p("hiveling_hunger_drain");     f("multiplier", 1.5, 0, 10); ep();

        // ── Inchling ──
        p("inchling_size");             f("scale", 0.25, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 1.0, -10, 10); ep();
        p("inchling_speed_boost");      f("amount", 0.15, -1, 10); ep();
        p("inchling_reduced_health");   f("amount", -10.0, -100, 100); ep();
        p("inchling_hunger_efficiency");f("multiplier", 0.5, 0, 10); ep();

        // ── Kraken ──
        p("kraken_tentacle_lash");      fi("amplifier", 2, 0, 255); fi("duration_ticks", 80, 1, 72000); f("radius", 5.0, 0, 64); fi("cooldown_ticks", 160, 0, 72000); ep();
        p("kraken_ink_shot");           f("speed", 1.2, 0, 10); fi("cooldown_ticks", 120, 0, 72000); ep();
        p("kraken_massive");            f("scale", 1.3, 0.1, 10); fb("modify_reach", true); f("reach_bonus", 0.0, -10, 10); ep();
        p("kraken_pressure_armor");     fi("amplifier", 0, 0, 4); ep();
        p("kraken_deep_current");       f("amount", 0.8, -1, 10); ep();
        p("kraken_summon_guardian");    fi("max_count", 2, 1, 100); fi("cooldown_ticks", 400, 0, 72000); fi("hunger_cost", 5, 0, 100); fi("despawn_ticks", 18000, 0, 1000000); f("death_damage", 1.0, 0, 100); ep();
        p("kraken_beached");            f("amount", -0.3, -1, 1); ep();
        p("kraken_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();

        // ── Merling ──
        p("merling_aquatic_speed");     f("amount", 0.6, -1, 10); ep();
        p("merling_land_slowdown");     f("amount", -0.1, -1, 1); ep();
        // (aquatic_fish_diet_bonus values are nested inside if_else_list
        // actions; the shallow power_overrides system can't reach them.
        // Pack authors who want different cooked-equivalent values should
        // copy aquatic_fish_diet_bonus.json into their pack and edit directly.)

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
        p("sculkborn_natural_armor");   fi("amplifier", 0, 0, 4); ep();
        p("sculkborn_knockback_resist");f("amount", 0.5, -1, 1); ep();
        p("sculkborn_reduced_health");  f("amount", -4.0, -100, 100); ep();
        p("sculkborn_slow_movement");   f("amount", -0.15, -1, 1); ep();
        p("sculkborn_daylight_damage"); f("damage_per_second", 2.0, 0, 100); ep();

        // ── Shulk ──
        p("shulk_natural_armor");       fi("amplifier", 0, 0, 4); ep();
        p("shulk_slow_movement");       f("amount", -0.25, -1, 1); ep();

        // ── Siren ──
        p("siren_aquatic_speed");       f("amount", 0.8, -1, 10); ep();
        p("siren_land_slowdown");       f("amount", -0.15, -1, 1); ep();
        p("siren_reduced_health");      f("amount", -4.0, -100, 100); ep();

        // ── Skeleton ──
        // (skeleton_daylight_damage and skeleton_ascended_daylight_damage have
        // tuneable values nested in entity_action; shallow overrides can't reach them.)
        p("skeleton_speed");            f("amount", 0.2, -1, 10); ep();
        p("skeleton_jump");             f("amount", 0.3, -1, 10); ep();
        p("skeleton_brittle_frame");    f("amount", -6.0, -100, 100); ep();
        p("skeleton_marksmanship");     f("multiplier", 1.5, 0, 100); ep();
        p("skeleton_eat_bone_meal");    fi("nutrition", 3, 0, 20); f("saturation", 0.4, 0, 10); ep();
        p("skeleton_evolved_marksmanship");f("multiplier", 1.75, 0, 100); ep();
        p("skeleton_evolved_hp");       f("amount", 2.0, -100, 100); ep();
        p("skeleton_ascended_speed");   f("amount", 0.3, -1, 10); ep();
        p("skeleton_apex_brittle_frame");f("amount", -2.0, -100, 100); ep();

        // ── Slime ──
        // (slime_ascended_sticky values are nested in entity_action;
        // shallow overrides can't reach them. Edit JSON directly to retune.)
        p("slime_moisture");            f("drain_per_tick", 0.0004, 0, 0.01); f("dry_biome_drain_multiplier", 3.0, 0, 100); f("fire_drain_multiplier", 10.0, 0, 100); f("water_refill_per_tick", 0.005, 0, 0.1); f("regen_threshold", 0.75, 0, 1.0); f("armor_penalty_threshold", 0.10, 0, 1.0); f("dot_damage", 1.0, 0, 100); fi("dot_interval", 40, 1, 72000); ep();
        p("slime_death_save");          f("moisture_threshold", 0.75, 0, 1.0); fi("teleport_distance", 50, 1, 256); fi("teleport_y_range", 10, 0, 256); f("split_max_hp", 4.0, 1, 100); fi("recovery_ticks", 2400, 0, 72000); ep();
        p("slime_level_hp");            fi("levels_per_hp", 10, 1, 100); fi("max_bonus_hp", 20, 0, 100); ep();
        p("slime_evolved_hp");          f("amount", 2.0, -100, 100); ep();
        p("slime_apex_hp");             f("amount", 6.0, -100, 100); ep();

        // ── Sporeling ──
        p("sporeling_spore_cloud");     fi("amplifier", 1, 0, 255); fi("duration_ticks", 100, 1, 72000); f("radius", 5.0, 0, 64); fi("cooldown_ticks", 240, 0, 72000); ep();
        p("sporeling_natural_armor");   fi("amplifier", 0, 0, 4); ep();
        p("sporeling_slow_movement");   f("amount", -0.1, -1, 1); ep();
        p("sporeling_daylight_damage"); f("damage_per_second", 1.0, 0, 100); ep();

        // ── Stoneguard ──
        p("stoneguard_thorns_aura");    f("return_ratio", 0.2, 0, 10); ep();
        p("stoneguard_natural_armor");  f("amount", 6.0, -100, 100); ep();
        p("stoneguard_knockback_resist");f("amount", 0.5, -1, 1); ep();
        p("stoneguard_active_glowstone");f("max_distance", 5.0, 1, 64); fi("cooldown_ticks", 100, 0, 72000); ep();
        p("stoneguard_stone_mining");   f("multiplier", 2.0, 0, 100); ep();
        p("stoneguard_slow_movement");  f("amount", -0.1, -1, 1); ep();
        p("stoneguard_no_mob_spawns");  fi("radius", 24, 1, 256); ep();

        // ── Strider ──
        // (strider_lava_regen heal amount is nested in entity_action;
        // shallow power_overrides can't reach it. Edit JSON directly to retune.)
        p("strider_natural_armor");     fi("amplifier", 0, 0, 4); ep();
        p("strider_water_weakness");    f("multiplier", 3.0, 0, 100); ep();

        // ── Sylvan ──
        p("sylvan_active_root");        fi("amplifier", 5, 0, 255); fi("duration_ticks", 80, 1, 72000); f("radius", 6.0, 0, 64); fi("cooldown_ticks", 200, 0, 72000); ep();
        p("sylvan_crop_growth");        fi("radius", 4, 1, 64); fi("tick_interval", 40, 1, 72000); fi("growths_per_interval", 2, 1, 100); ep();
        p("sylvan_regen_in_water");     f("amount_per_second", 1.0, 0, 100); ep();
        p("sylvan_nether_damage");      f("damage_per_second", 1.0, 0, 100); ep();

        // ── Tiny ──
        p("tiny_size");                 f("scale", 0.5, 0.1, 10); fb("modify_reach", false); f("reach_bonus", 0.5, -10, 10); ep();
        p("tiny_speed_boost");          f("amount", 0.2, -1, 10); ep();
        p("tiny_item_magnetism");       f("radius", 4.0, 0, 64); ep();
        p("tiny_attack_penalty");       f("amount", -2.0, -100, 100); ep();
        p("tiny_hunger_drain");         f("multiplier", 1.8, 0, 10); ep();

        // ── Umbral ──
        p("umbral_shadow_orb");         fi("max_orbs", 4, 1, 100); f("radius", 28.0, 1, 128); fi("cooldown_ticks", 60, 0, 72000); fi("tick_interval", 20, 1, 72000); ep();
        // umbral_active_dash strength is nested in entity_action.dash; cooldown
        // is at the top level on this active_ability JSON so it CAN be overridden.
        p("umbral_active_dash");        fi("cooldown_ticks", 60, 0, 72000); ep();
        p("umbral_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();

        // ── Wraith ──
        // (wraith_ascended_daylight_damage and wraith_ascended_weakness_aura
        // have tuneable values nested in entity_action; shallow overrides
        // can't reach them. Edit JSON directly to retune.)
        p("wraith_phase");              fb("always_on", false); f("exhaustion_per_tick", 0.15, 0, 1.0); ep();
        p("wraith_evolved_phase");      fb("always_on", false); f("exhaustion_per_tick", 0.1125, 0, 1.0); ep();
        p("wraith_apex_phase");         fb("always_on", false); f("exhaustion_per_tick", 0.075, 0, 1.0); ep();
        p("wraith_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();
        p("wraith_hunger_drain");       f("value", 1.75, 0, 100); ep();
        p("wraith_apex_hunger_drain");  f("value", 1.25, 0, 100); ep();

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
        p("warden_ancient_hide");       fi("amplifier", 1, 0, 4); ep();
        p("warden_hulking_frame");      f("scale", 1.15, 0.1, 10); fb("modify_reach", true); f("reach_bonus", 0.0, -10, 10); ep();
        p("warden_lumbering");          f("amount", -0.30, -1, 1); ep();
        p("warden_daylight_damage");    f("damage_per_second", 1.0, 0, 100); ep();
        // Dark-vision powers (issue #101): players can toggle these in-game via the
        // skill keybind; admins set enabled:false here to remove them entirely.
        p("warden_night_vision");       fb("enabled", true); ep();
        p("warden_echolocation");       fb("enabled", true); ep();
        p("warden_tremor_sense");       fb("enabled", true); ep();

        // ── Eastern / wuxia origins ──
        // persistent_effect / condition_passive / creative_flight powers honor a
        // top-level "enabled" kill-switch (admins set false to remove them entirely).
        // persistent_effect also honors a top-level "amplifier" that retunes the
        // first effect's strength without touching the nested effects[] array.

        // Asura
        p("asura_frail_frame");         f("amount", -6, -100, 100); ep();
        p("asura_bloodrage_1");         fb("enabled", true); fi("amplifier", 0, 0, 255); ep();
        p("asura_bloodrage_2");         fb("enabled", true); fi("amplifier", 1, 0, 255); ep();
        p("asura_bloodrage_3");         fb("enabled", true); fi("amplifier", 2, 0, 255); ep();
        p("asura_unmovable");           f("amount", 1.0, -1, 1); ep();
        p("asura_slam");                fi("cooldown_ticks", 80, 0, 72000); ep();
        p("asura_frenzy");              fi("cooldown_ticks", 400, 0, 72000); ep();
        p("asura_undying_rage");        fb("enabled", true); fi("amplifier", 1, 0, 255); ep();
        // (asura_blood_tithe heal amount is nested in entity_action; shallow
        // overrides can't reach it. Edit JSON directly to retune.)
        p("asura_wrath_eruption");      fi("cooldown_ticks", 500, 0, 72000); ep();

        // Windwalker
        // (windwalker_featherfall is a prevent_action with no numeric tunables.)
        p("windwalker_air_jumps");      fi("min", 0, 0, 10000); fi("max", 2, 1, 100000); fi("start_value", 2, 0, 100000); fi("regen_rate", 2, -1000, 1000); fi("regen_interval", 1, 1, 72000); ep();
        p("windwalker_lofty_leap");     f("amount", 0.12, -1, 10); ep();
        p("windwalker_cloud_steps");    fi("cooldown_ticks", 0, 0, 72000); ep();
        // (windwalker_wall_grace / windwalker_sky_dancer have no tunable fields.)
        p("windwalker_gale_dash");      f("power", 2.0, 0, 10); fi("cooldown_ticks", 60, 0, 72000); ep();
        p("windwalker_swift_current");  f("amount", 0.02, -1, 10); ep();
        // (windwalker_typhoon / windwalker_eye_of_storm tornado params are nested
        // in entity_action; only cooldown_ticks sits at the top level.)
        p("windwalker_typhoon");        fi("cooldown_ticks", 800, 0, 72000); ep();
        p("windwalker_riding_wind");    fb("enabled", true); ep();
        p("windwalker_eye_of_storm");   fi("cooldown_ticks", 700, 0, 72000); ep();

        // Qi Cultivator
        p("qi_resource");               fi("min", 0, 0, 10000); fi("max", 100, 1, 100000); fi("start_value", 50, 0, 100000); fi("regen_rate", 1, -1000, 1000); fi("regen_interval", 20, 1, 72000); ep();
        // (qi_meditation_charge / qi_dantian_expansion resource gain is nested in
        // entity_action; only interval sits at the top level.)
        p("qi_meditation_charge");      fi("interval", 10, 1, 72000); fb("enabled", true); ep();
        p("qi_meditation_heal");        fb("enabled", true); fi("amplifier", 0, 0, 255); ep();
        p("qi_vibrating_palm");         fi("cooldown_ticks", 20, 0, 72000); fi("resource_cost_amount", 25, 0, 100000); ep();
        p("qi_hardened");               fi("cooldown_ticks", 160, 0, 72000); fi("resource_cost_amount", 30, 0, 100000); ep();
        p("qi_dantian_expansion");      fi("interval", 20, 1, 72000); fb("enabled", true); ep();
        p("qi_core_pressure");          f("multiplier", 1.3, 0, 100); ep();
        p("qi_flying_sword");           fi("cooldown_ticks", 100, 0, 72000); fi("resource_cost_amount", 40, 0, 100000); ep();

        // Golden Body (golden_bell_*)
        p("golden_bell_iron_shirt");    f("amount", 8, -100, 100); ep();
        p("golden_bell_rooted");        f("amount", 0.6, -1, 1); ep();
        p("golden_bell_hard_qigong");   f("damage", 4, 0, 100); fi("fire_ticks", 0, 0, 72000); ep();
        p("golden_bell_ring");          fi("cooldown_ticks", 600, 0, 72000); ep();
        p("golden_bell_heavy_stance");  f("amount", -0.02, -1, 1); ep();
        p("golden_bell_diamond_body");  f("amount", 6, -100, 100); ep();
        p("golden_bell_reflected_force");f("damage", 8, 0, 100); fi("fire_ticks", 0, 0, 72000); ep();
        p("golden_bell_bell_toll");     fi("cooldown_ticks", 400, 0, 72000); ep();

        // Iron Monk
        p("iron_monk_stamina");         fi("min", 0, 0, 10000); fi("max", 100, 1, 100000); fi("start_value", 100, 0, 100000); fi("regen_rate", 2, -1000, 1000); fi("regen_interval", 10, 1, 72000); ep();
        p("iron_monk_guard");           fb("enabled", true); fi("amplifier", 0, 0, 255); ep();
        // (iron_monk_guard_drain / iron_monk_sea_of_stamina stamina change is nested
        // in entity_action; only interval sits at the top level.)
        p("iron_monk_guard_drain");     fi("interval", 20, 1, 72000); fb("enabled", true); ep();
        p("iron_monk_guard_block");     f("multiplier", 0.1, 0, 100); ep();
        // (iron_monk_guard_react sound/stamina drain is nested in entity_action.)
        p("iron_monk_palm_strike");     fi("cooldown_ticks", 40, 0, 72000); fi("resource_cost_amount", 20, 0, 100000); ep();
        p("iron_monk_iron_resolve");    f("amount", 0.4, -1, 1); ep();
        // (iron_monk_counter_stance damage_attacker amount_ratio is nested in
        // entity_action; shallow overrides can't reach it.)
        p("iron_monk_sea_of_stamina");  fi("interval", 20, 1, 72000); fb("enabled", true); ep();
        p("iron_monk_lohan_palm");      fi("cooldown_ticks", 120, 0, 72000); fi("resource_cost_amount", 30, 0, 100000); ep();

        // Sword Immortal (jianxian_*)
        p("jianxian_sword_heart");      f("amount", 4, -100, 100); ep();
        p("jianxian_keen_edge");        f("amount", 1, -100, 100); ep();
        p("jianxian_sword_qi");         fi("cooldown_ticks", 25, 0, 72000); ep();
        p("jianxian_riding_sword");     fb("enabled", true); ep();
        p("jianxian_flickering_slash"); f("power", 2.5, 0, 10); fi("cooldown_ticks", 80, 0, 72000); f("damage", 6.0, 0, 100); f("damage_radius", 2.5, 0, 64); f("weapon_damage_scale", 0.5, 0, 10); ep();
        // (jianxian_immortal_body is a prevent_action with no numeric tunables.)
        p("jianxian_ten_thousand_swords");fi("cooldown_ticks", 600, 0, 72000); ep();
        p("jianxian_sword_heart_unity");f("amount", 2, -100, 100); ep();
        p("jianxian_heavenly_formation");fi("cooldown_ticks", 400, 0, 72000); ep();
        p("jianxian_heaven_severing_slash");fi("cooldown_ticks", 300, 0, 72000); ep();

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
        p("class_titan_size");          f("scale", 1.25, 0.1, 10); fb("modify_reach", true); f("reach_bonus", 0.0, -10, 10); ep();
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

        // ── Fisher ──
        p("class_fisher_swim_speed");   f("amount", 0.15, -1, 10); ep();
        p("class_fisher_waters_luck");  f("amount", 1.0, -10, 10); ep();
        p("class_fisher_sea_legs");     f("multiplier", 0.5, 0, 10); ep();

        // ── Mason ──
        p("class_mason_block_reach");   f("amount", 1.0, -10, 10); ep();
        p("class_mason_strong_grip");   f("amount", 1.0, -100, 100); ep();
        p("class_mason_stone_speed");   f("multiplier", 1.25, 0, 100); ep();

        // ── Paladin ──
        p("class_paladin_holy_armor");  f("amount", 2.0, -100, 100); ep();
        p("class_paladin_turn_undead"); fi("duration", 80, 0, 72000); fi("amplifier", 0, 0, 255); ep();
        p("class_paladin_beacon_regen");f("radius", 8.0, 0, 128); ep();

        // ── Mount ──
        p("mount"); f("range", 5.0, 1, 64); fi("cooldown_ticks", 100, 0, 72000); fi("hunger_cost", 0, 0, 100); fb("allow_players", true); fb("allow_mobs", true); fb("block_bosses", true); ep();

        // ── KubeJS-defined custom powers ──
        // Form-author fills in js_id (free text) to point at a JS-registered behavior.
        p("js_custom"); ep();
        p("js_active"); fi("cooldown_ticks", 20, 0, 72000); fi("hunger_cost", 0, 0, 100); ep();

        BUILDER.pop(); // power_overrides
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

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

    /**
     * The (min,max) a numeric override field was registered with, or {@code null}
     * if this power/field has no ranged override. Used by the 2.1 creator's form
     * renderer to bound/slider numeric inputs even for codec-only powers.
     */
    public static NumericRange getPowerRange(String powerId, String field) {
        Map<String, NumericRange> fields = POWER_RANGES.get(powerId);
        return fields == null ? null : fields.get(field);
    }

    /** The default value a numeric/bool override field was registered with, or
     *  {@code null} if absent. Lets the creator pre-fill effective defaults that
     *  codec {@code optionalFieldOf} lambdas hide from record reflection. */
    public static Object getPowerDefault(String powerId, String field) {
        Map<String, ModConfigSpec.ConfigValue<?>> fields = POWER_OVERRIDES.get(powerId);
        if (fields == null) return null;
        ModConfigSpec.ConfigValue<?> cv = fields.get(field);
        return cv == null ? null : cv.getDefault();
    }
}
