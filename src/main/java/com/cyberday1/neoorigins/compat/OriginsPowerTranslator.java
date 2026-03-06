package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

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
     * These produce [SKIP] lines rather than [FAIL] lines.
     */
    private static final Set<String> SKIP_TYPES = Set.of(
        "origins:resource",
        "origins:toggle",
        "origins:action_on_hit",
        "origins:action_on_being_hit",
        "origins:action_on_wake_up",
        "origins:action_on_land",
        "origins:action_on_item_use",
        "origins:action_on_callback",
        "origins:action_on_kill",
        "origins:conditioned_attribute",
        "origins:conditioned_status_effect",
        "origins:overlay",
        "origins:shader",
        "origins:particle",
        "origins:lava_vision",
        "origins:model_color",
        "origins:swim_speed",
        "origins:air_acceleration",
        "origins:tooltip",
        "origins:burn",
        "origins:fire_projectile",
        "origins:exhaust",
        "origins:phasing",
        "origins:keep_inventory",
        "origins:ignore_water",
        "origins:climbing",  // handled separately via wall_climbing
        "apace:resource",
        "apace:toggle",
        "apace:conditioned_attribute",
        "apace:conditioned_status_effect"
    );

    private OriginsPowerTranslator() {}

    /**
     * Translate an Origins-format power JSON to NeoOrigins format.
     * Returns Optional.empty() for skip/fail cases — these are already logged.
     * Does NOT handle origins:multiple — that is handled by OriginsMultipleExpander before this is called.
     */
    public static Optional<JsonObject> translate(Identifier id, JsonObject json) {
        String type = OriginsFormatDetector.getType(json);

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
            NeoOrigins.LOGGER.warn("OriginsCompat: Failed to translate power {} ({}): {}", id, type, reason);
            CompatTranslationLog.fail(id, type + ": " + reason);
            return Optional.empty();
        }
    }

    private static Optional<JsonObject> doTranslate(Identifier id, String type, JsonObject src) {
        return switch (type) {
            case "origins:attribute"               -> translateAttribute(src);
            case "origins:elytra_flight"           -> translateSimple("neoorigins:flight");
            case "origins:creative_flight"         -> translateSimple("neoorigins:flight");
            case "origins:night_vision"            -> translateSimple("neoorigins:night_vision");
            case "origins:water_breathing"         -> translateSimple("neoorigins:water_breathing");
            case "origins:stacking_status_effect"  -> translateStackingStatusEffect(src);
            case "origins:status_effect"           -> translateStatusEffect(src);
            case "origins:effect_immunity"         -> translateEffectImmunity(src);
            case "origins:modify_damage_taken"     -> translateModifyDamage(src, "in");
            case "origins:modify_damage_dealt"     -> translateModifyDamage(src, "out");
            case "origins:invulnerability"         -> translateInvulnerability(src);
            case "origins:disable_regen"           -> translateSimplePrevent("SPRINT_FOOD");
            case "origins:slow_falling"            -> translateSimplePrevent("FALL_DAMAGE");
            case "origins:active_self"             -> translateActiveSelf(id, src);
            case "origins:action_over_time"        -> translateActionOverTime(id, src);
            case "origins:walk_speed"              -> translateWalkSpeed(src);
            case "apace:attribute"                 -> translateAttribute(src);
            case "apace:stacking_status_effect"    -> translateStackingStatusEffect(src);
            case "apace:effect_immunity"           -> translateEffectImmunity(src);
            case "apace:elytra_flight"             -> translateSimple("neoorigins:flight");
            case "apace:creative_flight"           -> translateSimple("neoorigins:flight");
            case "apace:night_vision"              -> translateSimple("neoorigins:night_vision");
            case "apace:water_breathing"           -> translateSimple("neoorigins:water_breathing");
            case "origins:entity_size"             -> translateEntitySize(src);
            case "apace:entity_size"               -> translateEntitySize(src);
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
        out.addProperty("attribute", "minecraft:generic.movement_speed");

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
        // Origins stacking_status_effect — apply first effect only, stacking ramp is lost.
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
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:modify_damage");
        out.addProperty("direction", direction);

        // Origins modifier is nested; extract value and operation to compute multiplier
        if (src.has("modifier")) {
            JsonObject mod = src.getAsJsonObject("modifier");
            if (mod.has("value")) {
                double value = mod.get("value").getAsDouble();
                String op = mod.has("operation") ? mod.get("operation").getAsString() : "addition";
                // Approximate: multiply_total / multiply_base both become (1 + value) multiplier
                float multiplier = (float) switch (op) {
                    case "multiply_total", "multiply_base" -> 1.0 + value;
                    default -> 1.0 + value; // "addition" — approximate
                };
                out.addProperty("multiplier", multiplier);
            }
        }

        if (!out.has("multiplier")) out.addProperty("multiplier", 1.0f);

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateInvulnerability(JsonObject src) {
        // Approximate: origins:invulnerability blocks all damage sources.
        // Route A can only map to a single prevent_action; approximate as FIRE immunity.
        JsonObject out = new JsonObject();
        out.addProperty("type", "neoorigins:prevent_action");
        out.addProperty("action", "FIRE");
        return Optional.of(out);
    }

    private static Optional<JsonObject> translateActiveSelf(Identifier id, JsonObject src) {
        // Only handle active_self whose action is origins:apply_effect
        if (!src.has("action")) {
            CompatTranslationLog.skip(id, "origins:active_self", "no 'action' field");
            return Optional.empty();
        }
        JsonObject action = src.getAsJsonObject("action");
        String actionType = OriginsFormatDetector.getType(action);
        if (!"origins:apply_effect".equals(actionType) && !"apace:apply_effect".equals(actionType)) {
            CompatTranslationLog.skip(id, "origins:active_self", "unsupported action type: " + actionType);
            return Optional.empty();
        }
        return translateStackingStatusEffect(action);
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
            // Approximate: normalise against vanilla player height of 1.8
            out.addProperty("scale", height / 1.8f);
        } else if (src.has("width")) {
            float width = src.get("width").getAsFloat();
            // Approximate: normalise against vanilla player width of 0.6
            out.addProperty("scale", width / 0.6f);
        } else {
            throw new IllegalArgumentException("origins:entity_size missing 'scale', 'height', or 'width' field");
        }

        return Optional.of(out);
    }

    private static Optional<JsonObject> translateActionOverTime(Identifier id, JsonObject src) {
        // Only handle action_over_time whose entity_action is execute_command
        if (!src.has("entity_action")) {
            CompatTranslationLog.skip(id, "origins:action_over_time", "no 'entity_action' field");
            return Optional.empty();
        }
        JsonObject action = src.getAsJsonObject("entity_action");
        String actionType = OriginsFormatDetector.getType(action);
        if (!"origins:execute_command".equals(actionType) && !"apace:execute_command".equals(actionType)) {
            CompatTranslationLog.skip(id, "origins:action_over_time", "unsupported entity_action type: " + actionType);
            return Optional.empty();
        }
        // TickActionPower only supports enum action types; command support is not present in Route A.
        CompatTranslationLog.skip(id, "origins:action_over_time",
            "execute_command action requires Route B (TickActionPower doesn't support arbitrary commands)");
        return Optional.empty();
    }
}
