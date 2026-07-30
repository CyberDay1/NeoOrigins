package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Translates Origins-format power JSONs to NeoOrigins format.
 * Returns Optional.empty() for types that cannot or should not be translated.
 * All failures are logged to CompatTranslationLog before returning empty.
 */
public final class OriginsPowerTranslator {

    /**
     * Origins power types that have no Route A equivalent.
     * Route B (OriginsCompatPowerLoader) handles those marked accordingly.
     * These produce [SKIP] lines rather than [FAIL] lines.
     */
    private static final Set<String> SKIP_TYPES = Set.of(
        // Route B handles these — passes to OriginsCompatPowerLoader
        "origins:active_self",           "apace:active_self",
        "origins:action_over_time",      "apace:action_over_time",
        "origins:resource",              "apace:resource",
        "origins:toggle",                "apace:toggle",
        "origins:action_on_hit",         "apace:action_on_hit",
        "origins:action_on_being_hit",   "apace:action_on_being_hit",
        "origins:self_action_when_hit",  "apace:self_action_when_hit",
        "origins:self_action_on_hit",    "apace:self_action_on_hit",
        "origins:damage_over_time",      "apace:damage_over_time",
        "origins:action_on_callback",    "apace:action_on_callback",
        "origins:conditioned_attribute", "apace:conditioned_attribute",
        "origins:conditioned_status_effect", "apace:conditioned_status_effect",
        "origins:action_on_kill",        "apace:action_on_kill",
        // Phase 3: Now handled by Route B
        "origins:fire_projectile",       "apace:fire_projectile",
        "origins:target_action_on_hit",  "apace:target_action_on_hit",
        "origins:self_action_on_kill",   "apace:self_action_on_kill",
        "origins:launch",               "apace:launch",
        "origins:entity_glow",          "apace:entity_glow",
        "origins:self_glow",            "apace:self_glow",
        // prevent_death is now Route A (translatePreventDeath); it falls back
        // to Route B only when a damage_condition is present (see that method).
        "origins:action_when_hit",      "apace:action_when_hit",
        "origins:action_when_damage_taken", "apace:action_when_damage_taken",
        "origins:attacker_action_when_hit", "apace:attacker_action_when_hit",
        "origins:action_on_land",       "apace:action_on_land",
        // Phase 5: Event-based powers (Route B)
        "origins:prevent_item_use",      "apace:prevent_item_use",
        "origins:restrict_armor",        "apace:restrict_armor",
        "origins:prevent_sleep",         "apace:prevent_sleep",
        "origins:prevent_block_use",     "apace:prevent_block_use",
        "origins:prevent_entity_use",    "apace:prevent_entity_use",
        "origins:modify_food",           "apace:modify_food",
        "origins:modify_jump",           "apace:modify_jump",
        // origins:overlay — translated in doTranslate()
        // origins:shader — translated in doTranslate()
        // origins:particle is translated in doTranslate() — was previously skipped
        // origins:lava_vision — translated in doTranslate()
        // origins:model_color — translated in doTranslate()
        // origins:shaking — handled by Route B (freeze ticks visual)
        // origins:swim_speed — translated in doTranslate()
        "origins:air_acceleration",      "apace:air_acceleration",
        // origins:modify_swim_speed translates in doTranslate() to
        // attribute_modifier on minecraft:water_movement_efficiency.
        // Misc behaviours without a direct equivalent
        // origins:keep_inventory — translated in doTranslate()
        // origins:ignore_water — translated in doTranslate()
        // origins:climbing — translated in doTranslate()
        // origins:phasing — translated in doTranslate()
        // origins:burn — translated in doTranslate()
        // origins:exhaust — translated in doTranslate()
        // origins:modify_status_effect_amplifier — handled by Route B
        // origins:modify_falling — handled by Route B
        // origins:modify_player_spawn — translated in doTranslate()
        // origins:action_on_wake_up — translated in doTranslate()
        // origins:action_on_item_use — translated in doTranslate()
        // origins:inventory — translated in doTranslate()
        // origins:recipe — handled by Route B
        "origins:recipe",                "apace:recipe",
        // origins:starting_equipment — translated in doTranslate()
        // origins:walk_on_fluid — translated in doTranslate()
        // Phase 8: Route B compat
        "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor",
        "origins:freeze",                "apace:freeze",
        "origins:modify_harvest",        "apace:modify_harvest",
        // NOTE: origins:simple / origins:tooltip are NOT skipped — they are
        // translated to neoorigins:simple in doTranslate() so their name/
        // description still render in the GUI (a SKIP here would discard them).
        // origins:cooldown — Route B parses it into a countdown compat
        // resource that trigger_cooldown can arm and resource conditions /
        // hud_render can read (the Apoli "readable cooldown" pattern).
        "origins:cooldown",              "apace:cooldown",
        // Issue #110 follow-up (Origins++ 2.4): Route B
        "origins:swimming",              "apace:swimming",
        "origins:action_on_being_used",  "apace:action_on_being_used",
        "origins:prevent_block_selection", "apace:prevent_block_selection"
    );

    /**
     * Cross-mod (apace-fork) power type ids that the editor schema's authorable
     * {@code type} enum should recognise as valid, even though they are NOT native
     * NeoOrigins types.
     *
     * <p>These are import-only ids: on load they are rewritten to a first-class
     * {@code neoorigins:} type by {@link #doTranslate} (the {@code apace:*} cases
     * in that switch are their <i>actual</i> runtime mapping). They appear in real
     * imported Apace/Origins packs, so the schema lists them so such files validate
     * in the editor rather than tripping the permissive fallback branch.
     *
     * <p>This is the canonical source of truth for these ids — the dev-time schema
     * generator ({@code PowerSchemaGenerator}) reads it instead of hard-coding the
     * list, so the compat layer (not the generator) owns the cross-mod surface.
     * Each id here MUST have a matching {@code apace:*} case in {@link #doTranslate};
     * the set is intentionally curated (the apace prefixes seen in the wild whose
     * targets are authorable {@code neoorigins:} types), not the full translator
     * source surface.
     */
    public static final Set<String> SCHEMA_RECOGNIZED_IMPORT_IDS = Set.of(
        "apace:night_vision",    // -> neoorigins:night_vision     (doTranslate)
        "apace:status_effect",   // -> neoorigins:status_effect    (doTranslate)
        "apace:water_breathing"  // -> neoorigins:water_breathing  (doTranslate)
    );

    /**
     * Lookup table: Origins Classes power IDs (origins:simple) → NeoOrigins power JSON.
     * When a power with type origins:simple is encountered, we check its ID against this map.
     * If found, the NeoOrigins JSON is returned directly (no further translation needed).
     */
    private static final Map<String, java.util.function.Supplier<JsonObject>> SIMPLE_POWER_OVERRIDES = Map.ofEntries(
        Map.entry("origins-classes:no_sprint_exhaustion",   () -> simpleType("neoorigins:exhaustion_filter", "sources", listOf("sprint"))),
        Map.entry("origins-classes:no_mining_exhaustion",   () -> simpleType("neoorigins:exhaustion_filter", "sources", listOf("mining"))),
        Map.entry("origins-classes:better_bone_meal",       () -> simpleType("neoorigins:better_bone_meal")),
        Map.entry("origins-classes:more_animal_loot",       () -> simpleType("neoorigins:more_animal_loot")),
        Map.entry("origins-classes:twin_breeding",          () -> simpleType("neoorigins:twin_breeding")),
        Map.entry("origins-classes:less_bow_slowdown",      () -> simpleType("neoorigins:less_item_use_slowdown", "item_type", "bow")),
        Map.entry("origins-classes:less_shield_slowdown",   () -> simpleType("neoorigins:less_item_use_slowdown", "item_type", "shield")),
        Map.entry("origins-classes:no_projectile_divergence", () -> simpleType("neoorigins:no_projectile_divergence")),
        Map.entry("origins-classes:longer_potions",         () -> simpleType("neoorigins:longer_potions")),
        Map.entry("origins-classes:better_enchanting",      () -> simpleType("neoorigins:better_enchanting")),
        Map.entry("origins-classes:efficient_repairs",      () -> simpleType("neoorigins:efficient_repairs")),
        Map.entry("origins-classes:quality_equipment",      () -> simpleType("neoorigins:quality_equipment")),
        Map.entry("origins-classes:better_crafted_food",    () -> simpleType("neoorigins:better_crafted_food")),
        Map.entry("origins-classes:more_smoker_xp",         () -> simpleType("neoorigins:more_smoker_xp")),
        Map.entry("origins-classes:trade_availability",     () -> simpleType("neoorigins:trade_availability")),
        Map.entry("origins-classes:rare_wandering_loot",    () -> simpleType("neoorigins:rare_wandering_loot")),
        Map.entry("origins-classes:sneaky",                 () -> simpleType("neoorigins:sneaky")),
        Map.entry("origins-classes:stealth",                () -> simpleType("neoorigins:stealth")),
        Map.entry("origins-classes:tree_felling",           () -> simpleType("neoorigins:tree_felling")),
        Map.entry("origins-classes:more_planks_from_logs",  () -> simpleType("neoorigins:craft_amount_bonus")),
        Map.entry("origins-classes:tamed_animal_boost",     () -> simpleType("neoorigins:tamed_animal_boost")),
        Map.entry("origins-classes:tamed_potion_diffusal",  () -> simpleType("neoorigins:tamed_potion_diffusal")),
        Map.entry("origins-classes:stealth_descriptor",     () -> simpleType("neoorigins:more_smoker_xp")), // display-only, no-op
        Map.entry("origins-classes:double_teleport_range",  () -> simpleType("neoorigins:teleport_range_modifier")),
        // Built-in Origins power IDs referenced by addon packs (no JSON file, hardcoded in Origins)
        Map.entry("origins:elytra",             () -> simpleType("neoorigins:natural_glide")),
        Map.entry("origins:fire_immunity",      () -> simpleType("neoorigins:prevent_action", "action", "fire")),
        Map.entry("origins:fresh_air",          () -> simpleType("neoorigins:prevent_action", "action", "sleep")),
        Map.entry("origins:like_water",         () -> simpleType("neoorigins:ignore_water")),
        Map.entry("origins:aquatic",            () -> simpleType("neoorigins:dries_out")),
        Map.entry("origins:water_vision",       () -> simpleType("neoorigins:lava_vision")),
        Map.entry("origins:aqua_affinity",      () -> simpleType("neoorigins:underwater_mining_speed")),
        Map.entry("origins:conduit_power_on_land", () -> simpleType("neoorigins:conduit_power")),
        Map.entry("origins:air_from_potions",   () -> simpleType("neoorigins:water_breathing")),
        Map.entry("origins:water_breathing",    () -> simpleType("neoorigins:water_breathing")),
        Map.entry("origins:swim_speed",         () -> simpleType("neoorigins:attribute_modifier", "attribute", "minecraft:water_movement_efficiency", "amount", 0.5, "operation", "add_value")),
        Map.entry("origins:night_vision",       () -> simpleType("neoorigins:night_vision")),
        Map.entry("origins:slow_falling",       () -> simpleType("neoorigins:prevent_action", "action", "fall_damage")),
        Map.entry("origins:climbing",           () -> simpleType("neoorigins:wall_climbing")),
        Map.entry("origins:shulker_inventory",  () -> simpleType("neoorigins:extra_inventory")),
        Map.entry("origins:phantomize",         () -> simpleType("neoorigins:phantom_form")),
        Map.entry("origins:translucent",        () -> simpleType("neoorigins:model_color", "red", 1.0, "green", 1.0, "blue", 1.0, "alpha", 0.5))
    );

    private static JsonObject simpleType(String type) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        return out;
    }

    private static JsonObject simpleType(String type, String key, String value) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.addProperty(key, value);
        return out;
    }

    private static JsonObject simpleType(String type, String key, JsonArray value) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.add(key, value);
        return out;
    }

    private static JsonObject simpleType(String type, String k1, String v1, String k2, double v2, String k3, String v3) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.addProperty(k1, v1); out.addProperty(k2, v2); out.addProperty(k3, v3);
        return out;
    }

    private static JsonObject simpleType(String type, String k1, double v1, String k2, double v2, String k3, double v3, String k4, double v4) {
        JsonObject out = new JsonObject();
        out.addProperty("type", type);
        out.addProperty(k1, v1); out.addProperty(k2, v2); out.addProperty(k3, v3); out.addProperty(k4, v4);
        return out;
    }

    private static JsonArray listOf(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    private OriginsPowerTranslator() {}

    /**
     * Top-level keys that are safe to copy verbatim onto the translated output
     * when the type-specific translator did not already emit them. Unlike
     * {@link OriginsOriginTranslator}'s "everything not handled" passthrough,
     * this is a deliberately CURATED allowlist: the per-type translate* methods
     * frequently RENAME source fields during translation (modifier →
     * attribute_modifier shapes, block_condition → block_tag/blocked_blocks,
     * hidden → kept as hidden but value-checked, ...), and each type consumes a
     * different key set — a blanket copy would reintroduce already-translated
     * source keys under their old names and could change behavior. The keys
     * here are read by PowerDataManager at the top level (required_mods,
     * power_condition, power_condition_mode) or are harmless Apoli metadata the
     * native codec ignores (badges, loading_priority), and are never renamed
     * by any per-type translator.
     */
    private static final Set<String> PASSTHROUGH_KEYS = Set.of(
        "required_mods",        // ModGate top-level gate + creator raw-snapshot fidelity
        "badges",               // Apoli display metadata; codec ignores unknown keys
        "loading_priority",     // Apoli load-order metadata; harmless if unused
        "power_condition",      // native top-level condition gate (hybrid files)
        "power_condition_mode"  // native gate mode (hybrid files)
    );

    /**
     * Copies curated unhandled top-level keys from the source power onto the
     * translated output, and routes an Apoli power-level {@code condition} to
     * the native {@code power_condition} gate. Apoli semantics are "power is
     * active while the condition holds", which is the native ALLOW mode (the
     * native default is DENY), and power_condition is parsed at load time by
     * the compat ConditionParser, so origins:/apace:/apoli: condition DSL is
     * honored rather than copied somewhere it would be ignored. Both steps
     * skip keys the type-specific translator already consumed (e.g. the
     * translators that fold {@code condition} into the native type's own
     * condition field, or bail out entirely when one is present).
     */
    private static void passThroughUnhandledKeys(JsonObject src, JsonObject out) {
        for (String key : PASSTHROUGH_KEYS) {
            if (src.has(key) && !out.has(key)) {
                out.add(key, src.get(key));
            }
        }
        // condition → power_condition: must NOT be copied under its source name —
        // the native loader deliberately uses "power_condition" to avoid colliding
        // with power types whose own config codecs read a "condition" field.
        if (src.has("condition") && !out.has("condition") && !out.has("power_condition")) {
            out.add("power_condition", src.get("condition"));
            if (!out.has("power_condition_mode")) {
                out.addProperty("power_condition_mode", "ALLOW");
            }
        }
    }

    /**
     * Translate an Origins-format power JSON to NeoOrigins format.
     * Returns Optional.empty() for skip/fail cases — these are already logged.
     * Does NOT handle origins:multiple — that is handled by OriginsMultipleExpander before this is called.
     */
    public static Optional<JsonObject> translate(Identifier id, JsonObject json) {
        String type = OriginsFormatDetector.getType(json);

        // Check for origins:simple powers with known ID overrides (Origins Classes etc.)
        String idStr = id.toString();
        var override = SIMPLE_POWER_OVERRIDES.get(idStr);
        if (override != null) {
            JsonObject out = override.get();
            if (json.has("name") && !out.has("name"))               out.add("name", json.get("name"));
            if (json.has("description") && !out.has("description")) out.add("description", json.get("description"));
            if (json.has("hidden") && !out.has("hidden"))           out.add("hidden", json.get("hidden"));
            passThroughUnhandledKeys(json, out);
            String mappedType = out.has("type") ? out.get("type").getAsString() : "?";
            CompatTranslationLog.pass(id, type + " -> " + mappedType + " (simple override)");
            return Optional.of(out);
        }

        if (SKIP_TYPES.contains(type)) {
            CompatTranslationLog.skip(id, type, "no equivalent in Route A, requires Route B");
            return Optional.empty();
        }

        try {
            Optional<JsonObject> result = doTranslate(id, type, json);
            if (result.isPresent()) {
                JsonObject out = result.get();
                // Preserve display name, description and the hidden flag from the original Origins JSON.
                if (json.has("name") && !out.has("name"))               out.add("name", json.get("name"));
                if (json.has("description") && !out.has("description")) out.add("description", json.get("description"));
                if (json.has("hidden") && !out.has("hidden"))           out.add("hidden", json.get("hidden"));
                passThroughUnhandledKeys(json, out);
                String mappedType = out.has("type") ? out.get("type").getAsString() : "?";
                CompatTranslationLog.pass(id, type + " -> " + mappedType);
            }
            return result;
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            NeoOrigins.LOGGER.warn("[CompatA] Failed to translate power {} ({}): {}", id, type, reason);
            CompatTranslationLog.fail(id, type + ": " + reason);
            return Optional.empty();
        }
    }

    /**
     * Every {@code type} id {@link #doTranslate} has a translation case for, verbatim.
     *
     * <p>Distinct from {@link #SCHEMA_RECOGNIZED_IMPORT_IDS}, which is the curated
     * subset whose native target the schema models field-for-field. This set is the
     * full Route A acceptance surface: an id in here loads (the pack is rewritten to
     * a native power), an id outside it and outside
     * {@link OriginsCompatPowerLoader#ROUTE_B_TYPES} logs "no Route A translation"
     * and is dropped — so this is exactly what the schema may advertise.
     *
     * <p>MUST mirror the switch below exactly: {@code PowerEnumCheck} parses the
     * case labels out of this file and fails in BOTH directions. Apoli-family
     * variants are derived, not listed — see
     * {@link OriginsFormatDetector#legacyPowerTypeSurface()}.
     */
    public static final java.util.Set<String> ROUTE_A_TYPES = java.util.Set.of(
        "origins:attribute", "apace:attribute",
        "origins:modify_attribute", "apace:modify_attribute",
        "origins:elytra_flight", "apace:elytra_flight", "apoli:elytra_flight",
        "origins:creative_flight", "apace:creative_flight",
        "origins:night_vision", "apace:night_vision",
        "origins:water_breathing", "apace:water_breathing",
        "origins:stacking_status_effect", "apace:stacking_status_effect",
        "origins:status_effect", "apace:status_effect",
        "origins:effect_immunity", "apace:effect_immunity",
        "origins:modify_damage_taken",
        "origins:modify_damage_dealt",
        "origins:invulnerability",
        "origins:prevent_death", "apace:prevent_death",
        "origins:disable_regen",
        "origins:slow_falling",
        "origins:walk_speed", "apace:walk_speed",
        "origins:modify_swim_speed", "apace:modify_swim_speed",
        "origins:climbing", "apace:climbing",
        "origins:entity_size", "apace:entity_size",
        "origins:modify_break_speed", "apace:modify_break_speed",
        "origins:entity_group", "apace:entity_group",
        "origins:invisibility", "apace:invisibility",
        "origins:modify_exhaustion", "apace:modify_exhaustion",
        "origins:fire_immunity", "apace:fire_immunity",
        "origins:toggle_night_vision", "apace:toggle_night_vision",
        "origins:food_restriction", "apace:food_restriction",
        "origins:edible_item", "apace:edible_item",
        "origins:keep_inventory", "apace:keep_inventory",
        "origins:ignore_water", "apace:ignore_water",
        "origins:phasing", "apace:phasing",
        "origins:burn", "apace:burn",
        "origins:swim_speed", "apace:swim_speed",
        "origins:overlay", "apace:overlay",
        "origins:model_color", "apace:model_color",
        "origins:lava_vision", "apace:lava_vision",
        "origins:shader", "apace:shader",
        "origins:particle", "apace:particle", "apoli:particle", "apugli:particle",
        "origins:modify_player_spawn", "apace:modify_player_spawn",
        "origins:action_on_block_break", "apace:action_on_block_break",
        "origins:action_on_block_use", "apace:action_on_block_use",
        "origins:action_on_entity_use", "apace:action_on_entity_use",
        "origins:bonemeal", "apace:bonemeal",
        "origins:status_bar_texture", "apace:status_bar_texture",
        "origins:simple", "apace:simple",
        "origins:tooltip", "apace:tooltip",
        "origins:prevent_elytra_flight", "apace:prevent_elytra_flight",
        "origins:modify_projectile_damage", "apace:modify_projectile_damage",
        "origins:modify_air_speed", "apace:modify_air_speed",
        "origins:action_on_item_use", "apace:action_on_item_use",
        "origins:action_on_wake_up", "apace:action_on_wake_up",
        "origins:exhaust", "apace:exhaust",
        "origins:starting_equipment", "apace:starting_equipment",
        "origins:walk_on_fluid", "apace:walk_on_fluid",
        "origins:inventory", "apace:inventory");

    private static Optional<JsonObject> doTranslate(Identifier id, String type, JsonObject src) {
        return switch (type) {
            case "origins:attribute",              "apace:attribute"              -> translateAttribute(src);
            case "origins:modify_attribute",       "apace:modify_attribute"       -> translateModifyAttribute(src);
            case "origins:elytra_flight",          "apace:elytra_flight",
                 "apoli:elytra_flight"                                             -> translateElytraFlight(src);
            case "origins:creative_flight",        "apace:creative_flight"        -> translateSimple("neoorigins:flight");
            case "origins:night_vision",           "apace:night_vision"           -> translateSimple("neoorigins:night_vision");
            case "origins:water_breathing",        "apace:water_breathing"        -> translateSimple("neoorigins:water_breathing");
            case "origins:stacking_status_effect", "apace:stacking_status_effect" -> translateStackingStatusEffect(src);
            case "origins:status_effect",          "apace:status_effect"          -> translateStatusEffect(src);
            case "origins:effect_immunity",        "apace:effect_immunity"        -> translateEffectImmunity(src);
            case "origins:modify_damage_taken"                                    -> translateModifyDamage(src, "in");
            case "origins:modify_damage_dealt"                                    -> translateModifyDamage(src, "out");
            case "origins:invulnerability"                                        -> translateInvulnerability(src);
            case "origins:prevent_death",          "apace:prevent_death"          -> translatePreventDeath(src);
            case "origins:disable_regen"                                          -> translateSimplePrevent("SPRINT_FOOD");
            case "origins:slow_falling"                                           -> translateSimplePrevent("FALL_DAMAGE");
            case "origins:walk_speed",             "apace:walk_speed"             -> translateWalkSpeed(src);
            case "origins:modify_swim_speed",      "apace:modify_swim_speed"      -> translateModifySwimSpeed(src);
            case "origins:climbing",               "apace:climbing"               -> translateClimbing(src);
            case "origins:entity_size",            "apace:entity_size"            -> translateEntitySize(src);
            case "origins:modify_break_speed",     "apace:modify_break_speed"     -> translateModifyBreakSpeed(src);
            case "origins:entity_group",           "apace:entity_group"           -> translateEntityGroup(src);
            case "origins:invisibility",           "apace:invisibility"           -> translateInvisibility(src);
            case "origins:modify_exhaustion",      "apace:modify_exhaustion"      -> translateModifyExhaustion(src);
            // Phase 4: New Route A translations
            case "origins:fire_immunity",          "apace:fire_immunity"          -> translateSimplePrevent("FIRE");
            // Upstream's toggle_night_vision is a keybind-able active power. This
            // mapping used to silently drop the "toggle" half and leave packs with
            // a permanently-on effect. It no longer does: neoorigins:night_vision
            // is now switchable via the dedicated "Toggle Night Vision" keybind,
            // so the pack's intent (player can turn this on and off) is honoured.
            // The only difference is WHICH key does it — deliberately not the
            // origin skill slot upstream would have used, because slot-based
            // night-vision toggles are what caused the accidental-disable bug.
            // Kept as a shim-layer decision so legacy packs run unedited.
            case "origins:toggle_night_vision",    "apace:toggle_night_vision"    -> translateSimple("neoorigins:night_vision");
            case "origins:food_restriction",       "apace:food_restriction"       -> translateFoodRestriction(src);
            case "origins:edible_item",            "apace:edible_item"            -> translateEdibleItem(src);
            case "origins:keep_inventory",         "apace:keep_inventory"         -> translateSimple("neoorigins:keep_inventory");
            case "origins:ignore_water",           "apace:ignore_water"           -> translateSimple("neoorigins:ignore_water");
            case "origins:phasing",                "apace:phasing"                -> translatePhasing(id, src);
            case "origins:burn",                   "apace:burn"                   -> translateBurn(src);
            case "origins:swim_speed",             "apace:swim_speed"             -> translateSwimSpeed(src);
            case "origins:overlay",                "apace:overlay"                -> translateOverlay(src);
            case "origins:model_color",            "apace:model_color"            -> translateModelColor(src);
            case "origins:lava_vision",            "apace:lava_vision"            -> translateLavaVision(src);
            case "origins:shader",                 "apace:shader"                 -> translateShader(src);
            case "origins:particle",               "apace:particle",
                 "apoli:particle",                 "apugli:particle"              -> translateParticle(src);
            case "origins:modify_player_spawn",    "apace:modify_player_spawn"    -> translateModifyPlayerSpawn(src);
            // Phase 8: Origins++ compat
            case "origins:action_on_block_break",  "apace:action_on_block_break"  -> translateActionOnBlockBreak(src);
            case "origins:action_on_block_use",    "apace:action_on_block_use"    -> translateActionOnBlockUse(src);
            case "origins:action_on_entity_use",   "apace:action_on_entity_use"   -> translateActionOnEntityUse(src);
            case "origins:bonemeal",               "apace:bonemeal"               -> translateBonemeal(src);
            case "origins:status_bar_texture",      "apace:status_bar_texture"     -> translateDisplayNoop();
            // Display-only powers with no gameplay effect — map to the native
            // neoorigins:simple marker so the name/description still show in the
            // GUI. (Known origins:simple id-overrides are handled earlier in
            // translate() before reaching here.)
            case "origins:simple",                  "apace:simple",
                 "origins:tooltip",                 "apace:tooltip"                -> translateSimple("neoorigins:simple");
            case "origins:prevent_elytra_flight",   "apace:prevent_elytra_flight"  -> translateSimplePrevent("ELYTRA");
            case "origins:modify_projectile_damage","apace:modify_projectile_damage"-> translateModifyProjectileDamage(src);
            case "origins:modify_air_speed",        "apace:modify_air_speed"        -> translateModifyAirSpeed(src);
            case "origins:action_on_item_use",      "apace:action_on_item_use"      -> translateActionOnItemUse(src);
            case "origins:action_on_wake_up",       "apace:action_on_wake_up"       -> translateActionOnWakeUp(src);
            case "origins:exhaust",                 "apace:exhaust"                 -> translateExhaust(src);
            case "origins:starting_equipment",      "apace:starting_equipment"      -> translateStartingEquipment(id, src);
            case "origins:walk_on_fluid",           "apace:walk_on_fluid"           -> translateWalkOnFluid(src);
            case "origins:inventory",              "apace:inventory"              -> translateInventory(src);
            default -> {
                CompatTranslationLog.skip(id, type, "no Route A translation for this type");
                yield Optional.empty();
            }
        };
    }

    // ---- Helpers ----

    private static Optional<JsonObject> translateSimple(String neoType) {
        JsonObject out = new JsonObject();
        out.addProperty("type", neoType);
        return Optional.of(out);
    }

    /**
     * origins:climbing → neoorigins:wall_climbing. Unconditioned powers stay a
     * plain translation. Apoli's {@code condition} (climb only while it holds),
     * {@code hold_condition} (keep climbing after the trigger stops) and
     * {@code allow_holding} are folded into a {@code neoorigins:climbing_gate}
     * power_condition, which carries the hold state machine. Emitting the gate
     * here (instead of letting passThroughUnhandledKeys route the raw condition)
     * is what preserves hold semantics — the plain routing would drop
     * hold_condition entirely.
     */
    private static Optional<JsonObject> translateClimbing(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:wall_climbing");
        if (!src.has("condition") && !src.has("hold_condition")) {
            return Optional.of(out);
        }
        JsonObject gate = new JsonObject();
        gate.addProperty("type", "neoorigins:climbing_gate");
        if (src.has("condition"))      gate.add("condition", src.get("condition").deepCopy());
        if (src.has("hold_condition")) gate.add("hold_condition", src.get("hold_condition").deepCopy());
        if (src.has("allow_holding"))  gate.add("allow_holding", src.get("allow_holding").deepCopy());
        out.add("power_condition", gate);
        out.addProperty("power_condition_mode", "ALLOW");
        return Optional.of(out);
    }

    // Rewrites Origins/Apoli/Apugli :particle to neoorigins:particle. Field
    // remap is straightforward — they pass `particle` (string or object with a
    // `type` field), `frequency` (ticks), and optionally `visible_in_first_person`
    // (which we map to `visible_to_self`, currently informational only). count /
    // spread / offset have no Origins-side equivalent — leave defaults.
    private static Optional<JsonObject> translateParticle(JsonObject src) {
        if (!src.has("particle")) {
            return Optional.empty();
        }
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:particle");
        out.add("particle", src.get("particle"));
        if (src.has("frequency")) out.add("frequency", src.get("frequency"));
        if (src.has("visible_in_first_person")) out.add("visible_to_self", src.get("visible_in_first_person"));
        return Optional.of(out);
    }

    /**
     * Maps {@code origins:modify_player_spawn} to {@code neoorigins:modify_player_spawn}.
     * Origins puts dimension/biome/structure at the top level; NeoOrigins nests them
     * under a {@code location} object matching {@code LocationCondition}'s codec.
     */
    private static Optional<JsonObject> translateModifyPlayerSpawn(JsonObject src) {
        JsonObject location = new JsonObject();
        if (src.has("dimension")) location.addProperty("dimension", src.get("dimension").getAsString());
        if (src.has("biome"))     location.addProperty("biome",     src.get("biome").getAsString());
        if (src.has("structure")) location.addProperty("structure",  src.get("structure").getAsString());
        if (location.size() == 0) return Optional.empty(); // nothing to spawn into

        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:modify_player_spawn");
        out.add("location", location);
        return Optional.of(out);
    }

    // Rewrites to neoorigins:food_restriction; LegacyPowerTypeAliases then
    // remaps it to action_on_event (food_eaten event). Packs commonly declare
    // the origins:/apace: prefix — without this case the power silently drops.
    private static Optional<JsonObject> translateFoodRestriction(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:food_restriction");
        if (src.has("item_tag")) out.add("item_tag", src.get("item_tag"));
        if (src.has("mode"))     out.add("mode", src.get("mode"));
        return Optional.of(out);
    }

    // Apoli/Apace edible_item uses a nested food_component + singular item/tag
    // fields. Flatten to our schema: items/tags lists + top-level nutrition /
    // saturation / always_edible. food_component.effect / action / return_stack
    // are dropped (documented non-goals — pack authors chain effects via
    // action_on_event.ITEM_USE_FINISH instead).
    private static Optional<JsonObject> translateEdibleItem(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:edible_item");
        if (src.has("item")) {
            JsonArray items = new JsonArray();
            items.add(src.get("item").getAsString());
            out.add("items", items);
        }
        if (src.has("items")) out.add("items", src.get("items"));
        if (src.has("tag")) {
            JsonArray tags = new JsonArray();
            tags.add(src.get("tag").getAsString());
            out.add("tags", tags);
        }
        if (src.has("tags")) out.add("tags", src.get("tags"));
        JsonObject fc = src.has("food_component") && src.get("food_component").isJsonObject()
            ? src.getAsJsonObject("food_component") : src;
        if (fc.has("nutrition"))             out.addProperty("nutrition", fc.get("nutrition").getAsInt());
        if (fc.has("saturation_modifier"))   out.addProperty("saturation", fc.get("saturation_modifier").getAsFloat());
        else if (fc.has("saturation"))       out.addProperty("saturation", fc.get("saturation").getAsFloat());
        if (fc.has("always_edible"))         out.addProperty("always_edible", fc.get("always_edible").getAsBoolean());
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateSimplePrevent(String action) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:prevent_action");
        out.addProperty("action", action);
        return Optional.of(out);
    }

    /** Display-only power type — load successfully, no gameplay effect. */
    private static Optional<JsonObject> translateDisplayNoop() {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:toggle");
        out.addProperty("hidden", true);
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:action_on_block_break} to
     * {@code neoorigins:action_on_event} with event {@code block_break}.
     * Carries over entity_action, condition, and block_condition.
     */
    private static Optional<JsonObject> translateActionOnBlockBreak(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "block_break");
        if (src.has("entity_action"))   out.add("entity_action", src.get("entity_action"));
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        if (src.has("block_condition")) out.add("block_condition", src.get("block_condition"));
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:action_on_block_use} to
     * {@code neoorigins:action_on_event} with event {@code block_use}.
     * Carries over condition, block_condition and the hand filter directly.
     * {@code block_action} and {@code entity_action} both run as the event's
     * entity_action (block actions in the compat parser self-resolve the
     * clicked BlockPos from the dispatch context); an Apoli
     * {@code action_result} of SUCCESS/CONSUME/CONSUME_PARTIAL/FAIL — all of
     * which stop vanilla processing — appends a {@code cancel_event} so the
     * interaction outcome matches.
     */
    private static Optional<JsonObject> translateActionOnBlockUse(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "block_use");
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        if (src.has("block_condition")) out.add("block_condition", src.get("block_condition"));
        if (src.has("hands"))           out.add("hands", src.get("hands"));
        else if (src.has("hand"))       out.add("hand", src.get("hand"));

        com.google.gson.JsonArray actions = new com.google.gson.JsonArray();
        if (src.has("block_action"))  actions.add(src.get("block_action"));
        if (src.has("entity_action")) actions.add(src.get("entity_action"));
        String result = src.has("action_result")
            ? src.get("action_result").getAsString().toUpperCase(Locale.ROOT) : "";
        if (result.equals("SUCCESS") || result.equals("CONSUME")
                || result.equals("CONSUME_PARTIAL") || result.equals("FAIL")) {
            JsonObject cancel = new JsonObject();
            cancel.addProperty("type", "neoorigins:cancel_event");
            actions.add(cancel);
        }
        if (actions.size() == 1) {
            out.add("entity_action", actions.get(0));
        } else if (actions.size() > 1) {
            JsonObject and = new JsonObject();
            and.addProperty("type", "neoorigins:and");
            and.add("actions", actions);
            out.add("entity_action", and);
        }
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:bonemeal} (Apoli-family addon shape) to
     * {@code neoorigins:action_on_event} with event {@code bonemeal}.
     * Carries over entity_action/block_action, condition and block_condition —
     * the same context plumbing as block_use (BlockInteractContext carries the
     * bonemealed BlockPos, so block actions self-resolve).
     */
    private static Optional<JsonObject> translateBonemeal(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "bonemeal");
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        if (src.has("block_condition")) out.add("block_condition", src.get("block_condition"));

        com.google.gson.JsonArray actions = new com.google.gson.JsonArray();
        if (src.has("block_action"))  actions.add(src.get("block_action"));
        if (src.has("entity_action")) actions.add(src.get("entity_action"));
        if (actions.size() == 1) {
            out.add("entity_action", actions.get(0));
        } else if (actions.size() > 1) {
            JsonObject and = new JsonObject();
            and.addProperty("type", "neoorigins:and");
            and.add("actions", actions);
            out.add("entity_action", and);
        }
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:action_on_entity_use} to
     * {@code neoorigins:action_on_event} with event {@code entity_use}.
     * Carries over entity_action and condition.
     */
    private static Optional<JsonObject> translateActionOnEntityUse(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "entity_use");
        if (src.has("entity_action"))   out.add("entity_action", src.get("entity_action"));
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:modify_projectile_damage} to
     * {@code neoorigins:modify_damage} with direction {@code out} and
     * damage_type filter for projectiles.
     */
    private static Optional<JsonObject> translateModifyProjectileDamage(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:modify_damage");
        out.addProperty("direction", "out");
        out.addProperty("damage_type", "#minecraft:is_projectile");

        // Extract multiplier from the modifier object
        if (src.has("modifier") && src.get("modifier").isJsonObject()) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
            String op = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
            // Convert Apoli operation to a multiplier
            float multiplier = switch (op.toLowerCase(Locale.ROOT)) {
                case "addition", "add_value", "add_base_early", "add_base_late" -> (float)(1.0 + value);
                case "multiply_base", "multiply_base_additive" -> (float)(1.0 + value);
                case "multiply_total", "multiply_total_multiplicative" -> (float) value;
                default -> (float)(1.0 + value);
            };
            out.addProperty("multiplier", multiplier);
        } else {
            out.addProperty("multiplier", 1.5f); // sensible default
        }

        return Optional.of(out);
    }

    /**
     * Translates {@code origins:modify_air_speed} to a conditioned attribute
     * modifier on movement_speed that only applies while airborne.
     * In practice, Origins++ uses this for flight-speed-in-air bonuses.
     */
    private static Optional<JsonObject> translateModifyAirSpeed(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");
        out.addProperty("attribute", "minecraft:movement_speed");

        if (src.has("modifier") && src.get("modifier").isJsonObject()) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
            String op = mod.has("operation") ? mod.get("operation").getAsString() : "multiply_base";
            out.addProperty("amount", value);
            out.addProperty("operation", OriginsOperationMapper.mapOperation(op));
        } else {
            // Some packs use a flat value at top level
            double value = src.has("value") ? src.get("value").getAsDouble() : 0.2;
            out.addProperty("amount", value);
            out.addProperty("operation", "add_multiplied_base");
        }

        // No direct "airborne only" condition in attribute_modifier, but
        // the modifier applies globally — acceptable approximation for
        // pack compat (Origins' air_speed bonus is also active on ground
        // in many packs since the movement_speed attribute affects both).
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:action_on_item_use} to
     * {@code neoorigins:action_on_event} with event {@code item_use}.
     * Carries over entity_action, condition, and item_condition.
     */
    private static Optional<JsonObject> translateActionOnItemUse(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "item_use");
        if (src.has("entity_action"))   out.add("entity_action", src.get("entity_action"));
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        if (src.has("item_condition"))  out.add("item_condition", src.get("item_condition"));
        return Optional.of(out);
    }

    /**
     * Translates {@code origins:action_on_wake_up} to
     * {@code neoorigins:action_on_event} with event {@code wake_up}.
     */
    /**
     * Translates Origins/Apace {@code prevent_death} to the native
     * {@code neoorigins:prevent_death} power. The power-level {@code condition}
     * and {@code entity_action} pass straight through — the native power parses
     * them with the same ConditionParser / ActionParser, so no remapping is
     * needed and the condition is finally honored (the old Route B
     * parsePreventDeath silently dropped it).
     *
     * <p>Powers that use a {@code damage_condition} return empty, falling back
     * to Route B so their exact prior behavior is preserved: an arbitrary
     * Apoli damage-condition tree cannot be reduced losslessly to the native
     * {@code damage_types} filter string, and guessing would be worse than the
     * known legacy behavior. Net effect: strictly better, never worse.
     */
    private static Optional<JsonObject> translatePreventDeath(JsonObject src) {
        if (src.has("damage_condition")) return Optional.empty();
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:prevent_death");
        if (src.has("entity_action")) out.add("entity_action", src.get("entity_action"));
        if (src.has("condition"))     out.add("condition", src.get("condition"));
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateActionOnWakeUp(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:action_on_event");
        out.addProperty("event", "wake_up");
        if (src.has("entity_action"))   out.add("entity_action", src.get("entity_action"));
        if (src.has("condition"))       out.add("condition", src.get("condition"));
        return Optional.of(out);
    }

    /**
     * Apoli's {@code origins:exhaust} is a tick-based action — it applies
     * a fixed {@code exhaustion} amount every {@code interval} ticks. The
     * previous translation routed it to {@code mod_exhaustion} (an event
     * modifier on vanilla's per-event exhaustion calls), which produces
     * completely different semantics: instead of "add exhaustion every
     * 30 ticks", the modifier inflated every vanilla exhaustion event by
     * the configured amount, draining hunger ~268x faster. Returning empty
     * here defers to Route B's {@code parseExhaust}, which ticks the
     * exhaustion correctly via {@code player.causeFoodExhaustion()}.
     * Reported by Hop's pack tester (2026-05-25).
     */
    private static Optional<JsonObject> translateExhaust(JsonObject src) {
        return Optional.empty();
    }

    /**
     * Translates {@code origins:starting_equipment} to
     * {@code neoorigins:starting_equipment}. Origins format uses a
     * {@code stack} object or {@code stacks} array; NeoOrigins uses a
     * flat {@code item} + {@code count} + {@code grant_id}. Best-effort:
     * only the first stack item is taken.
     */
    /**
     * Translates {@code origins:walk_on_fluid} to {@code neoorigins:walk_on_fluid}.
     * Origins format uses a {@code fluid} field with a fluid tag ID.
     */
    /**
     * Translates {@code origins:inventory} to {@code neoorigins:extra_inventory}.
     * Maps the Origins inventory size to slot count.
     */
    private static Optional<JsonObject> translateInventory(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:extra_inventory");

        // Origins uses "container_type" or defaults to 9 slots. Accepts both the
        // numeric/grid tokens (9x6, 54, 3x3) and the vanilla container names Apoli
        // packs use (double_chest, chest, hopper, dispenser, dropper). The native
        // extra_inventory is chest-backed (rows of 9), so non-9-wide containers are
        // mapped to the nearest chest size.
        int size = 9;
        if (src.has("container_type")) {
            String ct = src.get("container_type").getAsString().toLowerCase(Locale.ROOT);
            if (ct.contains("double_chest") || ct.contains("9x6") || ct.contains("54")) size = 54;
            else if (ct.contains("9x5") || ct.contains("45")) size = 45;
            else if (ct.contains("9x4") || ct.contains("36")) size = 36;
            else if (ct.contains("9x3") || ct.contains("27") || ct.equals("chest")) size = 27;
            else if (ct.contains("9x2") || ct.contains("18")) size = 18;
            else if (ct.contains("hopper")) size = 9; // 5 slots -> nearest chest row
            else if (ct.contains("dispenser") || ct.contains("dropper") || ct.contains("3x3")) size = 9;
            else if (ct.contains("9x1") || ct.contains("9")) size = 9;
        }

        out.addProperty("size", size);
        if (src.has("drop_on_death")) out.addProperty("drop_on_death", src.get("drop_on_death").getAsBoolean());
        // Carry the custom container title so the native power shows it instead of
        // the default "Extra Inventory" label.
        if (src.has("title") && src.get("title").isJsonPrimitive()) {
            out.addProperty("title", src.get("title").getAsString());
        }
        // Preserve the pack-declared activation key so the compat loader can bind
        // this native active power to a named hotkey instead of a skill slot.
        if (src.has("key")) out.add("key", src.get("key"));
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateWalkOnFluid(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:walk_on_fluid");

        // Origins format: { "fluid": "minecraft:water" } or fluid tag
        if (src.has("fluid")) {
            String fluid = src.get("fluid").getAsString();
            if (fluid.contains("water")) out.addProperty("fluid", "water");
            else if (fluid.contains("lava")) out.addProperty("fluid", "lava");
            else out.addProperty("fluid", "both");
        }
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateStartingEquipment(Identifier id, JsonObject src) {
        String itemId = null;
        int count = 1;

        if (src.has("stack") && src.get("stack").isJsonObject()) {
            JsonObject stack = src.getAsJsonObject("stack");
            itemId = stack.has("item") ? stack.get("item").getAsString() : null;
            if (stack.has("amount")) count = stack.get("amount").getAsInt();
            else if (stack.has("count")) count = stack.get("count").getAsInt();
        } else if (src.has("stacks") && src.get("stacks").isJsonArray()) {
            JsonArray stacks = src.getAsJsonArray("stacks");
            if (!stacks.isEmpty() && stacks.get(0).isJsonObject()) {
                JsonObject first = stacks.get(0).getAsJsonObject();
                itemId = first.has("item") ? first.get("item").getAsString() : null;
                if (first.has("amount")) count = first.get("amount").getAsInt();
                else if (first.has("count")) count = first.get("count").getAsInt();
            }
        } else if (src.has("item")) {
            itemId = src.get("item").getAsString();
            if (src.has("count")) count = src.get("count").getAsInt();
        }

        if (itemId == null) return Optional.empty();

        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:starting_equipment");
        out.addProperty("item", itemId);
        out.addProperty("count", count);
        out.addProperty("grant_id", id.toString());
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateAttribute(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");

        // Origins attribute has a nested "modifier" object or "modifiers" array
        if (src.has("modifier")) {
            extractModifierFields(src.getAsJsonObject("modifier"), out);
        } else if (src.has("modifiers")) {
            JsonArray modifiers = src.getAsJsonArray("modifiers");
            if (modifiers.isEmpty()) throw new IllegalArgumentException("origins:attribute 'modifiers' array is empty");
            // Take first modifier only (Route A limitation — stacking modifiers not supported)
            extractModifierFields(modifiers.get(0).getAsJsonObject(), out);
        } else {
            // Might be flat format
            extractModifierFields(src, out);
        }

        if (!out.has("attribute")) throw new IllegalArgumentException("origins:attribute missing 'attribute' field");
        if (!out.has("amount"))    throw new IllegalArgumentException("origins:attribute missing 'value'/'amount' field");

        return Optional.of(out);
    }

    /**
     * Translates {@code origins:modify_attribute} to {@code neoorigins:attribute_modifier}.
     *
     * <p>Distinct schema from {@code origins:attribute}: the target {@code attribute} sits at
     * the <i>top level</i> with a sibling {@code modifier} (or {@code modifiers} array) that
     * carries only {@code operation}/{@code value}. {@code origins:attribute}, by contrast,
     * nests the attribute id <i>inside</i> each modifier — so the two cannot share a handler
     * (routing this shape through {@link #translateAttribute} would throw "missing attribute").
     *
     * <p>Attribute ids pass through verbatim; {@code AttributeModifierPower} resolves the
     * {@code generic.}/{@code player.} prefix variance across 1.21.1 (prefixed) and 26.1
     * (unprefixed) at runtime, so legacy {@code minecraft:generic.max_health} names work as-is.
     *
     * <p>Route A limitation: when the modifier carries a {@code resource} key the value is
     * driven by a runtime resource (e.g. health that tracks a counter). A static
     * attribute_modifier cannot reproduce that — it applies the declared base {@code value}
     * only. Such powers load (so their origin is no longer hidden) but the dynamic behaviour
     * is not replicated; a faithful port would need a resource-aware native attribute power.
     */
    private static Optional<JsonObject> translateModifyAttribute(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");

        JsonObject mod = null;
        if (src.has("modifier") && src.get("modifier").isJsonObject()) {
            mod = src.getAsJsonObject("modifier");
        } else if (src.has("modifiers") && src.get("modifiers").isJsonArray()
                   && !src.getAsJsonArray("modifiers").isEmpty()) {
            // Route A takes the first modifier only — stacking is not represented.
            mod = src.getAsJsonArray("modifiers").get(0).getAsJsonObject();
        }
        if (mod != null) {
            // Apoli clamp/set operations (min/max/set, base or total) have NO vanilla
            // AttributeModifier equivalent. Mapping them to add_value corrupts the
            // attribute — e.g. a "max_total": 60 max-health CAP becomes a flat +60 HP
            // bonus (this pinned Deano's mage at ~37 hearts instead of the intended 7).
            // Drop the whole modifier rather than mis-apply it as a flat addition.
            if (mod.has("operation")
                && !OriginsOperationMapper.isRepresentable(mod.get("operation").getAsString())) {
                NeoOrigins.LOGGER.debug(
                    "OriginsCompat: dropping origins:modify_attribute with non-representable "
                    + "operation '{}' (clamp/set ops have no vanilla modifier equivalent)",
                    mod.get("operation").getAsString());
                return Optional.empty();
            }
            extractModifierFields(mod, out);
        }

        // Top-level attribute is authoritative for this shape — set it after the
        // modifier extraction so it wins even if a stray attribute hides in the modifier.
        if (src.has("attribute")) {
            out.addProperty("attribute", src.get("attribute").getAsString());
        }

        if (!out.has("attribute")) throw new IllegalArgumentException("origins:modify_attribute missing 'attribute' field");
        if (!out.has("amount"))    throw new IllegalArgumentException("origins:modify_attribute missing 'value'/'amount' field");

        return Optional.of(out);
    }

    /** Extracts attribute/value/operation fields from a modifier object into the target. */
    private static void extractModifierFields(JsonObject mod, JsonObject target) {
        if (mod.has("attribute")) {
            target.addProperty("attribute", mod.get("attribute").getAsString());
        }
        if (mod.has("value")) {
            target.addProperty("amount", mod.get("value").getAsDouble());
        } else if (mod.has("amount")) {
            target.addProperty("amount", mod.get("amount").getAsDouble());
        }
        if (mod.has("operation")) {
            target.addProperty("operation", OriginsOperationMapper.mapOperation(mod.get("operation").getAsString()));
        }
    }

    /**
     * Apoli's {@code modify_swim_speed} maps cleanly onto vanilla's
     * {@code minecraft:water_movement_efficiency} attribute — the same
     * registry entry that handles depth-strider and water-mob navigation
     * speed. Same nested-modifier shape as {@code origins:walk_speed}, just
     * a different target attribute. Without this, packs ship the swim power
     * but it silently no-ops because the type was in SKIP_TYPES with no
     * Route B handler. Reported by the Mido pack tester (2026-04-27).
     */
    private static Optional<JsonObject> translateModifySwimSpeed(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");
        out.addProperty("attribute", "minecraft:water_movement_efficiency");

        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
            String op = mod.has("operation") ? mod.get("operation").getAsString() : "multiply_base";
            out.addProperty("amount", value);
            out.addProperty("operation", OriginsOperationMapper.mapOperation(op));
        } else {
            throw new IllegalArgumentException("origins:modify_swim_speed missing 'modifier' field");
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateWalkSpeed(JsonObject src) {
        // Walk speed modifier — wrap in attribute translator structure
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");
        out.addProperty("attribute", "minecraft:movement_speed");

        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
            String op = mod.has("operation") ? mod.get("operation").getAsString() : "multiply_base";
            out.addProperty("amount", value);
            out.addProperty("operation", OriginsOperationMapper.mapOperation(op));
        } else {
            throw new IllegalArgumentException("origins:walk_speed missing 'modifier' field");
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateStatusEffect(JsonObject src) {
        // Single origins:status_effect
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:status_effect");

        if (src.has("effect")) {
            JsonElement effectEl = src.get("effect");
            if (effectEl.isJsonPrimitive()) {
                out.addProperty("effect", effectEl.getAsString());
            } else if (effectEl.isJsonObject() && effectEl.getAsJsonObject().has("id")) {
                out.addProperty("effect", effectEl.getAsJsonObject().get("id").getAsString());
            } else {
                throw new IllegalArgumentException("origins:status_effect 'effect' field has unexpected format");
            }
        } else {
            throw new IllegalArgumentException("origins:status_effect missing 'effect' field");
        }

        if (src.has("amplifier")) out.addProperty("amplifier", src.get("amplifier").getAsInt());
        if (src.has("ambient"))   out.addProperty("ambient", src.get("ambient").getAsBoolean());
        if (src.has("show_particles")) out.addProperty("show_particles", src.get("show_particles").getAsBoolean());

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateStackingStatusEffect(JsonObject src) {
        // Maps to the native neoorigins:stacking_status_effects, which keeps ALL
        // effects from the source — previously only the first was retained.
        // Three field variants seen in the wild:
        //   "effects": [{...}, ...]  — array of effect objects
        //   "effects": {...}         — single effect object
        //   "effect":  {...}         — singular effect object (some packs omit the 's')
        JsonArray entries = new JsonArray();

        if (src.has("effects")) {
            JsonElement effectsEl = src.get("effects");
            if (effectsEl.isJsonArray()) {
                for (JsonElement el : effectsEl.getAsJsonArray()) {
                    if (el.isJsonObject()) entries.add(buildEffectEntry(el.getAsJsonObject()));
                }
            } else if (effectsEl.isJsonObject()) {
                entries.add(buildEffectEntry(effectsEl.getAsJsonObject()));
            }
        } else if (src.has("effect")) {
            JsonElement effectEl = src.get("effect");
            if (effectEl.isJsonObject()) {
                entries.add(buildEffectEntry(effectEl.getAsJsonObject()));
            } else if (effectEl.isJsonPrimitive()) {
                JsonObject entry = new JsonObject();
                entry.addProperty("effect", effectEl.getAsString());
                entries.add(entry);
            }
        }

        if (entries.isEmpty()) throw new IllegalArgumentException("no 'effects'/'effect' field found");

        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:stacking_status_effects");
        out.add("effects", entries);
        return Optional.of(out);
    }

    /** Normalises a single Origins effect object into a persistent_effect effects-array entry JSON. */
    private static JsonObject buildEffectEntry(JsonObject src) {
        // Apoli uses "effect", but some packs use "id" (vanilla potion format).
        String effectId = src.has("effect") ? src.get("effect").getAsString()
            : src.has("id") ? src.get("id").getAsString() : null;
        if (effectId == null) throw new IllegalArgumentException("effect object missing 'effect'/'id' field");
        JsonObject entry = new JsonObject();
        entry.addProperty("effect", effectId);
        if (src.has("amplifier"))      entry.addProperty("amplifier", src.get("amplifier").getAsInt());
        if (src.has("ambient"))        entry.addProperty("ambient", src.get("ambient").getAsBoolean());
        if (src.has("show_particles")) entry.addProperty("show_particles", src.get("show_particles").getAsBoolean());
        return entry;
    }

    /**
     * Effect ids without a namespace are canonicalized to {@code minecraft:*}
     * so packs that write {@code "effect": "wither"} resolve the same as
     * {@code "minecraft:wither"} — vanilla's registry lookup requires a
     * namespace and silently drops bare ids otherwise. v2.1.4.
     */
    private static String canonicalizeEffectId(String raw) {
        if (raw == null || raw.isEmpty()) return raw;
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }

    private static Optional<JsonObject> translateEffectImmunity(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:effect_immunity");

        JsonArray effects = new JsonArray();
        if (src.has("effects")) {
            for (JsonElement el : src.getAsJsonArray("effects")) {
                if (el.isJsonPrimitive()) {
                    effects.add(canonicalizeEffectId(el.getAsString()));
                } else if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    // Origins uses {"effect": "minecraft:poison"} in some formats
                    if (obj.has("effect")) effects.add(canonicalizeEffectId(obj.get("effect").getAsString()));
                    else if (obj.has("id")) effects.add(canonicalizeEffectId(obj.get("id").getAsString()));
                }
            }
        } else if (src.has("effect")) {
            effects.add(canonicalizeEffectId(src.get("effect").getAsString()));
        }

        // Apoli's `inverted` flips the list into an exception list: immune to
        // every effect EXCEPT those listed. inverted:true with NO effects is a
        // legitimate "immune to all effects" power (e.g. Chaotic Chemist's
        // Immunity Shot, gated by a power condition) — so only reject an empty
        // list in the non-inverted form, where it would mean "immune to nothing".
        boolean inverted = src.has("inverted") && src.get("inverted").getAsBoolean();
        if (effects.isEmpty() && !inverted) {
            throw new IllegalArgumentException("origins:effect_immunity: no effects specified");
        }

        out.add("effects", effects);
        if (inverted) out.addProperty("inverted", true);
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateModifyDamage(JsonObject src, String direction) {
        // Conditional damage modifiers cannot be expressed by native ModifyDamagePower,
        // so we leave them for Route B (OriginsCompatPowerLoader.parseConditionedModifyDamageTaken)
        // to compile. Returning empty here ensures Route A doesn't create an
        // unconditional damage modifier from a gated ability.
        if (src.has("condition")) return Optional.empty();

        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:modify_damage");
        out.addProperty("direction", direction);

        // Origins modifier is nested; extract value/amount and operation to compute multiplier.
        // Origins packs use either "value" or "amount" for the modifier number.
        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble()
                : mod.has("amount") ? mod.get("amount").getAsDouble() : Double.NaN;
            if (!Double.isNaN(value)) {
                String op = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
                switch (op) {
                    // set_total replaces the post-multiplier value outright. set_total 0
                    // (often -1+1) = "cancel all damage"; native ModifyDamagePower
                    // applies this clamp directly (no longer faked via a multiplier).
                    case "set_total", "set" -> out.addProperty("set_total", (float) value);
                    // max_total = upper CAP (Math.min(value, amount)); min_total = lower
                    // FLOOR. Despite the names these are NOT the modifier value — they're
                    // absolute clamp thresholds on the resulting damage. Collapsing them
                    // to (1+value) is what made `max_total 4` become a 5x MULTIPLIER.
                    case "max_total" -> out.addProperty("max_total", (float) value);
                    case "min_total" -> out.addProperty("min_total", (float) value);
                    // [LOSSY] multiply_total / multiply_base / addition all collapse to (1 + value).
                    default -> out.addProperty("multiplier", (float)(1.0 + value));
                }
            }
        }

        if (!out.has("multiplier")) out.addProperty("multiplier", 1.0f);

        // Translate damage_condition -> damage_type filter on native ModifyDamagePower.
        if (src.has("damage_condition") && src.get("damage_condition").isJsonObject()) {
            JsonObject dc = src.getAsJsonObject("damage_condition");
            String dcType = dc.has("type") ? dc.get("type").getAsString() : "";
            if (("origins:name".equals(dcType) || "apace:name".equals(dcType)) && dc.has("name")) {
                out.addProperty("damage_type", dc.get("name").getAsString());
            } else if (("origins:type".equals(dcType) || "apace:type".equals(dcType)) && dc.has("damage_type")) {
                out.addProperty("damage_type", dc.get("damage_type").getAsString());
            }
        }

        // Translate target_condition when it's an entity_group check — the most common
        // pattern (e.g. "undead" damage bonuses). Other target_condition shapes skip
        // translation entirely to avoid applying an unconditional buff.
        if (src.has("target_condition")) {
            JsonObject tc = src.getAsJsonObject("target_condition");
            String tcType = tc.has("type") ? tc.get("type").getAsString() : "";
            if (("origins:entity_group".equals(tcType) || "apace:entity_group".equals(tcType))
                && tc.has("group")) {
                out.addProperty("target_group", tc.get("group").getAsString());
            } else {
                // Unsupported target_condition shape -> skip to avoid silent mis-apply.
                return Optional.empty();
            }
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateInvulnerability(JsonObject src) {
        // Native neoorigins:invulnerability supports damage_types / damage_tags / msg_ids filters.
        // If the Origins power has a damage_condition sub-object, try to project it into those fields;
        // otherwise emit an unconditional invulnerability (blocks all damage).
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:invulnerability");

        if (src.has("damage_condition") && src.get("damage_condition").isJsonObject()) {
            JsonObject dc = src.getAsJsonObject("damage_condition");
            String type = dc.has("type") ? dc.get("type").getAsString() : "";

            JsonArray msgIds   = new JsonArray();
            JsonArray dmgTypes = new JsonArray();
            JsonArray dmgTags  = new JsonArray();

            switch (type) {
                case "origins:name", "apace:name" -> {
                    if (dc.has("name")) msgIds.add(dc.get("name").getAsString());
                }
                case "origins:in_tag", "apace:in_tag", "origins:tag", "apace:tag" -> {
                    if (dc.has("tag")) dmgTags.add(dc.get("tag").getAsString());
                }
                case "origins:fire", "apace:fire" -> {
                    dmgTags.add("minecraft:is_fire");
                }
                case "origins:projectile", "apace:projectile" -> {
                    dmgTags.add("minecraft:is_projectile");
                }
                case "origins:fall", "apace:fall" -> {
                    dmgTags.add("minecraft:is_fall");
                }
                case "origins:explosive", "apace:explosive", "origins:explosion", "apace:explosion" -> {
                    dmgTags.add("minecraft:is_explosion");
                }
                case "origins:attacker", "apace:attacker" -> {
                    // [LOSSY] attacker sub-conditions can't be projected into damage-source filters.
                    // Fall back to blocking nothing — let the power act as all-damage-block if
                    // the author intended that, or be pruned if they didn't.
                }
                default -> {
                    // Unknown sub-condition — fall back to no filter (block-all), matching
                    // Origins' behaviour when damage_condition is absent.
                }
            }

            if (!msgIds.isEmpty())   out.add("msg_ids", msgIds);
            if (!dmgTypes.isEmpty()) out.add("damage_types", dmgTypes);
            if (!dmgTags.isEmpty())  out.add("damage_tags", dmgTags);
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateEntitySize(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:size_scaling");

        // Origins uses "width"/"height" float fields; we derive scale from height (base height ~1.8)
        // If a "scale" field is present directly, prefer that.
        if (src.has("scale")) {
            out.addProperty("scale", src.get("scale").getAsFloat());
        } else if (src.has("height")) {
            float height = src.get("height").getAsFloat();
            // [LOSSY] height-based scale: normalised against vanilla player height of 1.8.
            out.addProperty("scale", height / 1.8f);
        } else if (src.has("width")) {
            float width = src.get("width").getAsFloat();
            // [LOSSY] width-based scale: normalised against vanilla player width of 0.6.
            out.addProperty("scale", width / 0.6f);
        } else {
            throw new IllegalArgumentException("origins:entity_size missing 'scale', 'height', or 'width' field");
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateModifyBreakSpeed(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:break_speed_modifier");

        if (!src.has("modifier")) throw new IllegalArgumentException("origins:modify_break_speed missing 'modifier' field");
        JsonObject mod = src.getAsJsonObject("modifier");
        double value = mod.has("value") ? mod.get("value").getAsDouble()
                     : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        // [LOSSY] all Origins operations (addition, multiply_base, multiply_total) collapse to (1 + value).
        out.addProperty("multiplier", (float)(1.0 + value));

        // Best-effort: lift a simple block_condition tag into block_tag so the
        // multiplier is restricted to the same blocks. Apoli's block_condition is a
        // full DSL; we only translate the common single-tag shape
        // ({"type":"...:block","block":"#tag"} or {"tag":"..."}). Anything more
        // complex (and/or/nesting, state predicates) is dropped and the power
        // applies to all blocks — logged so the lossiness is visible.
        if (src.has("block_condition") && src.get("block_condition").isJsonObject()) {
            String tag = extractSimpleBlockTag(src.getAsJsonObject("block_condition"));
            if (tag != null) {
                out.addProperty("block_tag", tag);
            } else {
                NeoOrigins.LOGGER.debug("OriginsCompat: modify_break_speed block_condition is not a simple tag/block filter — block_tag dropped, multiplier applies to all blocks");
            }
        }

        return Optional.of(out);
    }

    /**
     * Pull a single block tag (or block id) out of the common Apoli block_condition
     * shapes, or {@code null} if the condition is anything more complex than a flat
     * tag/block match. Recognised: a {@code "block"}/{@code "tag"}/{@code "blocks"}
     * string field, with or without a {@code "type":"...:block"} wrapper. A leading
     * {@code #} is preserved so the downstream filter treats it as tag-only.
     */
    private static String extractSimpleBlockTag(JsonObject cond) {
        // Reject obviously-composite conditions outright.
        if (cond.has("conditions") || cond.has("condition")) return null;
        for (String key : new String[]{"tag", "block", "blocks"}) {
            if (cond.has(key) && cond.get(key).isJsonPrimitive()) {
                String val = cond.get(key).getAsString();
                if (val == null || val.isBlank()) return null;
                // "tag" is implicitly a tag even without the leading #.
                if (key.equals("tag") && !val.startsWith("#")) return "#" + val;
                return val;
            }
        }
        return null;
    }

    private static Optional<JsonObject> translateEntityGroup(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:entity_group");
        String group = src.has("group") ? src.get("group").getAsString() : "undefined";
        out.addProperty("group", group);
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateElytraFlight(JsonObject src) {
        // Map origins:/apace:/apoli:elytra_flight onto the native neoorigins:elytra_flight
        // power. Pure MAPPING — the native power owns the glide (via natural_glide) AND the
        // cosmetic wing render. Apoli's render_elytra field (effectively always set, default
        // true) is carried through, defaulting to true when the source omits it; the optional
        // texture_location passes through when present. The top-level `condition` gate is
        // routed to power_condition generically by passThroughUnhandledKeys.
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:elytra_flight");
        boolean renderElytra = !src.has("render_elytra") || src.get("render_elytra").getAsBoolean();
        out.addProperty("render_elytra", renderElytra);
        if (src.has("texture_location") && src.get("texture_location").isJsonPrimitive()) {
            out.addProperty("texture_location", src.get("texture_location").getAsString());
        }
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateInvisibility(JsonObject src) {
        // Map origins:/apace:invisibility onto the native neoorigins:invisibility power.
        // This is a pure MAPPING — no behaviour lives here: the native power owns the
        // invisibility effect AND the render_armor armor-hiding. Apoli's render_armor
        // field (default true) is carried straight through; the top-level `condition`
        // gate is routed to power_condition generically by passThroughUnhandledKeys.
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:invisibility");
        boolean renderArmor = !src.has("render_armor") || src.get("render_armor").getAsBoolean();
        out.addProperty("render_armor", renderArmor);
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateModifyExhaustion(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:hunger_drain_modifier");

        double value = 0.0;
        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            value = mod.has("value") ? mod.get("value").getAsDouble()
                  : mod.has("amount") ? mod.get("amount").getAsDouble() : 0.0;
        }
        // [LOSSY] exhaustion modifier is additive in Origins; approximated as (1 + value) multiplier.
        out.addProperty("multiplier", (float)(1.0 + value));

        return Optional.of(out);
    }

    // origins:swim_speed has value/modifier, same shape as modify_swim_speed
    private static Optional<JsonObject> translateSwimSpeed(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:attribute_modifier");
        out.addProperty("attribute", "minecraft:water_movement_efficiency");

        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            double value = mod.has("value") ? mod.get("value").getAsDouble() : 0.0;
            String op = mod.has("operation") ? mod.get("operation").getAsString() : "multiply_base";
            out.addProperty("amount", value);
            out.addProperty("operation", OriginsOperationMapper.mapOperation(op));
        } else if (src.has("value")) {
            out.addProperty("amount", src.get("value").getAsDouble());
            out.addProperty("operation", "add_value");
        } else {
            out.addProperty("amount", 0.5);
            out.addProperty("operation", "add_value");
        }
        return Optional.of(out);
    }

    private static Optional<JsonObject> translatePhasing(Identifier id, JsonObject src) {
        // Origins phasing with a block_condition is selective (e.g. pass through
        // berry bushes only). Mapping that to wraith_phase would give full wall
        // noclip — skip it so it's a safe no-op instead.
        if (src.has("block_condition")) {
            CompatTranslationLog.skip(id, "origins:phasing",
                "selective phasing (block_condition) — cannot map to wraith_phase");
            return Optional.empty();
        }
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:wraith_phase");

        com.google.gson.JsonArray blockedBlocks = new com.google.gson.JsonArray();

        // Carry over blacklist if present
        if (src.has("blacklist") && src.get("blacklist").isJsonArray()) {
            for (var el : src.getAsJsonArray("blacklist")) {
                if (el.isJsonPrimitive()) blockedBlocks.add(el.getAsString());
            }
        }

        // Try to extract block IDs from block_condition for the blocked_blocks list.
        // Partial translation is better than skipping entirely.
        if (src.has("block_condition") && src.get("block_condition").isJsonObject()) {
            JsonObject bc = src.getAsJsonObject("block_condition");
            extractBlockIdsFromCondition(bc, blockedBlocks);
            if (blockedBlocks.isEmpty()) {
                // block_condition was too complex to extract block IDs — emit the
                // power anyway (partial translation) but log a warning.
                NeoOrigins.LOGGER.warn("[CompatA] phasing {} has complex block_condition that " +
                    "could not be fully translated to blocked_blocks — wraith_phase will " +
                    "allow passing through all blocks", id);
            }
        }

        if (!blockedBlocks.isEmpty()) {
            out.add("blocked_blocks", blockedBlocks);
        }
        return Optional.of(out);
    }

    /**
     * Best-effort extraction of block IDs from a block_condition JSON into a flat list.
     * Handles simple cases: single block ID, in_tag, and/or combinators.
     */
    private static void extractBlockIdsFromCondition(JsonObject bc, com.google.gson.JsonArray out) {
        String bcType = bc.has("type") ? bc.get("type").getAsString() : "";
        String bareType = bcType.contains(":") ? bcType.substring(bcType.indexOf(':') + 1) : bcType;

        switch (bareType) {
            case "block" -> {
                String blockId = bc.has("block") ? bc.get("block").getAsString() : null;
                if (blockId != null) out.add(blockId);
            }
            case "in_tag" -> {
                // Tag-based — can't flatten to individual blocks, but add the tag reference
                String tag = bc.has("tag") ? bc.get("tag").getAsString() : null;
                if (tag != null) out.add("#" + (tag.startsWith("#") ? tag.substring(1) : tag));
            }
            case "and", "or", "all_of", "any_of" -> {
                JsonArray conditions = bc.has("conditions") ? bc.getAsJsonArray("conditions") : new JsonArray();
                for (JsonElement el : conditions) {
                    if (el.isJsonObject()) extractBlockIdsFromCondition(el.getAsJsonObject(), out);
                }
            }
            default -> {
                // Too complex — can't extract block IDs
            }
        }
    }

    private static Optional<JsonObject> translateBurn(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:burn");
        if (src.has("interval"))      out.add("interval", src.get("interval"));
        if (src.has("burn_duration"))  out.add("burn_duration", src.get("burn_duration"));
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateOverlay(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:overlay");
        if (src.has("texture"))  out.add("texture", src.get("texture"));
        if (src.has("strength")) out.add("strength", src.get("strength"));
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateModelColor(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:model_color");
        if (src.has("red"))   out.add("red", src.get("red"));
        if (src.has("green")) out.add("green", src.get("green"));
        if (src.has("blue"))  out.add("blue", src.get("blue"));
        if (src.has("alpha")) out.add("alpha", src.get("alpha"));
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateLavaVision(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:lava_vision");
        if (src.has("s")) out.addProperty("strength", src.get("s").getAsFloat());
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateShader(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:shader");
        if (src.has("shader")) out.add("shader", src.get("shader"));
        return Optional.of(out);
    }
}
