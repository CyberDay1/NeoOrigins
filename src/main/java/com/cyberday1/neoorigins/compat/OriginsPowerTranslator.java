package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

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
        "origins:prevent_death",        "apace:prevent_death",
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
        // Visual/rendering — no server-side equivalent
        "origins:overlay",
        "origins:shader",
        "origins:particle",
        "origins:lava_vision",
        "origins:model_color",
        "origins:shaking",
        // Movement variants without a direct equivalent
        "origins:swim_speed",            "apace:swim_speed",
        "origins:air_acceleration",      "apace:air_acceleration",
        "origins:modify_swim_speed",     "apace:modify_swim_speed",
        // Misc behaviours without a direct equivalent
        "origins:keep_inventory",
        "origins:ignore_water",
        // origins:climbing — translated in doTranslate()
        "origins:phasing",
        "origins:burn",
        "origins:exhaust",               "apace:exhaust",
        "origins:modify_status_effect_amplifier",
        "origins:modify_player_spawn",
        "origins:action_on_wake_up",
        "origins:action_on_item_use",
        "origins:inventory",
        "origins:recipe",
        "origins:starting_equipment",
        "origins:action_on_entity_use",  "apace:action_on_entity_use",
        "origins:walk_on_fluid",         "apace:walk_on_fluid",
        // Display-only, no gameplay effect
        "origins:tooltip",               "apace:tooltip",
        "origins:simple",                "apace:simple",
        "origins:cooldown",              "apace:cooldown"
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
        Map.entry("origins-classes:double_teleport_range",  () -> simpleType("neoorigins:teleport_range_modifier"))
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

    private static JsonArray listOf(String... values) {
        JsonArray arr = new JsonArray();
        for (String v : values) arr.add(v);
        return arr;
    }

    private OriginsPowerTranslator() {}

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
                // Preserve display name and description from the original Origins JSON.
                if (json.has("name") && !out.has("name"))               out.add("name", json.get("name"));
                if (json.has("description") && !out.has("description")) out.add("description", json.get("description"));
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

    private static Optional<JsonObject> doTranslate(Identifier id, String type, JsonObject src) {
        return switch (type) {
            case "origins:attribute",              "apace:attribute"              -> translateAttribute(src);
            case "origins:elytra_flight",          "apace:elytra_flight"          -> translateSimple("neoorigins:flight");
            case "origins:creative_flight",        "apace:creative_flight"        -> translateSimple("neoorigins:flight");
            case "origins:night_vision",           "apace:night_vision"           -> translateSimple("neoorigins:night_vision");
            case "origins:water_breathing",        "apace:water_breathing"        -> translateSimple("neoorigins:water_breathing");
            case "origins:stacking_status_effect", "apace:stacking_status_effect" -> translateStackingStatusEffect(src);
            case "origins:status_effect",          "apace:status_effect"          -> translateStatusEffect(src);
            case "origins:effect_immunity",        "apace:effect_immunity"        -> translateEffectImmunity(src);
            case "origins:modify_damage_taken"                                    -> translateModifyDamage(src, "in");
            case "origins:modify_damage_dealt"                                    -> translateModifyDamage(src, "out");
            case "origins:invulnerability"                                        -> translateInvulnerability(src);
            case "origins:disable_regen"                                          -> translateSimplePrevent("SPRINT_FOOD");
            case "origins:slow_falling"                                           -> translateSimplePrevent("FALL_DAMAGE");
            case "origins:walk_speed",             "apace:walk_speed"             -> translateWalkSpeed(src);
            case "origins:climbing",               "apace:climbing"               -> translateSimple("neoorigins:wall_climbing");
            case "origins:entity_size",            "apace:entity_size"            -> translateEntitySize(src);
            case "origins:modify_break_speed",     "apace:modify_break_speed"     -> translateModifyBreakSpeed(src);
            case "origins:entity_group",           "apace:entity_group"           -> translateEntityGroup(src);
            case "origins:invisibility",           "apace:invisibility"           -> translateInvisibility();
            case "origins:modify_exhaustion",      "apace:modify_exhaustion"      -> translateModifyExhaustion(src);
            // Phase 4: New Route A translations
            case "origins:fire_immunity",          "apace:fire_immunity"          -> translateSimplePrevent("FIRE");
            case "origins:toggle_night_vision",    "apace:toggle_night_vision"    -> translateSimple("neoorigins:night_vision");
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

    private static Optional<JsonObject> translateSimplePrevent(String action) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:prevent_action");
        out.addProperty("action", action);
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
        // [LOSSY] stacking_status_effect: only the first effect is applied; the amplifier ramp is lost.
        // Three field variants seen in the wild:
        //   "effects": [{...}, ...]  — array of effect objects
        //   "effects": {...}         — single effect object
        //   "effect":  {...}         — singular effect object (some packs omit the 's')
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:status_effect");

        JsonObject effectObj = null;

        if (src.has("effects")) {
            JsonElement effectsEl = src.get("effects");
            if (effectsEl.isJsonArray()) {
                JsonArray arr = effectsEl.getAsJsonArray();
                if (arr.isEmpty()) throw new IllegalArgumentException("'effects' array is empty");
                effectObj = arr.get(0).getAsJsonObject();
            } else if (effectsEl.isJsonObject()) {
                effectObj = effectsEl.getAsJsonObject();
            }
        } else if (src.has("effect")) {
            JsonElement effectEl = src.get("effect");
            if (effectEl.isJsonObject()) {
                effectObj = effectEl.getAsJsonObject();
            } else if (effectEl.isJsonPrimitive()) {
                // Bare string effect ID — treat directly
                out.addProperty("effect", effectEl.getAsString());
                return Optional.of(out);
            }
        }

        if (effectObj == null) throw new IllegalArgumentException("no 'effects'/'effect' field found");
        if (!effectObj.has("effect")) throw new IllegalArgumentException("effect object missing 'effect' id field");

        out.addProperty("effect", effectObj.get("effect").getAsString());
        if (effectObj.has("amplifier"))      out.addProperty("amplifier", effectObj.get("amplifier").getAsInt());
        if (effectObj.has("ambient"))        out.addProperty("ambient", effectObj.get("ambient").getAsBoolean());
        if (effectObj.has("show_particles")) out.addProperty("show_particles", effectObj.get("show_particles").getAsBoolean());

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateEffectImmunity(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:effect_immunity");

        JsonArray effects = new JsonArray();
        if (src.has("effects")) {
            for (JsonElement el : src.getAsJsonArray("effects")) {
                if (el.isJsonPrimitive()) {
                    effects.add(el.getAsString());
                } else if (el.isJsonObject()) {
                    JsonObject obj = el.getAsJsonObject();
                    // Origins uses {"effect": "minecraft:poison"} in some formats
                    if (obj.has("effect")) effects.add(obj.get("effect").getAsString());
                    else if (obj.has("id")) effects.add(obj.get("id").getAsString());
                }
            }
        } else if (src.has("effect")) {
            effects.add(src.get("effect").getAsString());
        }

        if (effects.isEmpty()) throw new IllegalArgumentException("origins:effect_immunity: no effects specified");

        out.add("effects", effects);
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateModifyDamage(JsonObject src, String direction) {
        // Conditional damage modifiers cannot be safely translated to Route A because
        // native modify_damage has no condition support.  Skip to avoid creating
        // unconditional damage immunity from what should be a gated ability.
        if (src.has("condition")) return Optional.empty();

        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:modify_damage");
        out.addProperty("direction", direction);

        // Origins modifier is nested; extract value and operation to compute multiplier
        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            if (mod.has("value")) {
                double value = mod.get("value").getAsDouble();
                String op = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
                // [LOSSY] multiply_total / multiply_base and addition all collapse to (1 + value) multiplier.
                float multiplier = (float)(1.0 + value);
                out.addProperty("multiplier", multiplier);
            }
        }

        if (!out.has("multiplier")) out.addProperty("multiplier", 1.0f);

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

        // [LOSSY] block_condition is dropped — break speed is applied via the
        // vanilla player.block_break_speed attribute, which can't filter by block.
        // The power applies to all blocks.

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateEntityGroup(JsonObject src) {
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:entity_group");
        String group = src.has("group") ? src.get("group").getAsString() : "undefined";
        out.addProperty("group", group);
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateInvisibility() {
        // Map origins:invisibility to a permanent invisibility status effect (no particles).
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:status_effect");
        out.addProperty("effect", "minecraft:invisibility");
        out.addProperty("ambient", true);
        out.addProperty("show_particles", false);
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
}
