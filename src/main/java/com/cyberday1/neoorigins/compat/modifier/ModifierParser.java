package com.cyberday1.neoorigins.compat.modifier;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Apoli-style modifier JSON into {@link FloatModifier} instances.
 *
 * <p>Apoli modifier operations (simplified to the float-only subset used by
 * origins powers):
 * <ul>
 *   <li>{@code add_base_early} / {@code add_base} — additive to base, pre-multiply phase</li>
 *   <li>{@code multiply_base_additive} / {@code multiply_base_multiplicative} — scale base</li>
 *   <li>{@code add_total_early} — additive after base phase</li>
 *   <li>{@code multiply_total_additive} — +(base * value) after totalling</li>
 *   <li>{@code multiply_total_multiplicative} / {@code set_total} — final transforms</li>
 * </ul>
 *
 * <p>Parse returns a composed modifier that applies each stage in phase order.
 * For a single modifier (not a list), the phase collapses into a direct lambda.
 */
public final class ModifierParser {

    private ModifierParser() {}

    /** Parse a single modifier JSON object. */
    public static FloatModifier parse(JsonObject json, String contextId) {
        if (json == null) return FloatModifier.identity();
        String op = json.has("operation") ? json.get("operation").getAsString() : "";
        float value = json.has("value") ? json.get("value").getAsFloat() : 0f;
        try {
            return switch (op) {
                case "add_base_early", "add_base", "addition",
                     "add_total_early", "add_total"              -> base -> base + value;
                case "multiply_base_additive"                    -> base -> base + (base * value);
                case "multiply_base_multiplicative",
                     "multiply_total_multiplicative",
                     "multiplication"                            -> base -> base * value;
                case "multiply_total_additive"                   -> base -> base + (base * value);
                case "set_total", "set"                          -> base -> value;
                case "min_total"                                 -> base -> Math.min(base, value);
                case "max_total"                                 -> base -> Math.max(base, value);
                default -> {
                    NeoOrigins.LOGGER.warn("[CompatB] modifier '{}' in {} defaulted to identity",
                        op, contextId);
                    yield FloatModifier.identity();
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] modifier parse error in {}: {}", contextId, e.getMessage());
            return FloatModifier.identity();
        }
    }

    /**
     * Parse a list of modifier JSON objects, composing them in order. JSON shape
     * may be either a single object or an array. Returns identity if empty/null.
     */
    public static FloatModifier parseList(JsonElement element, String contextId) {
        if (element == null || element.isJsonNull()) return FloatModifier.identity();
        if (element.isJsonObject()) return parse(element.getAsJsonObject(), contextId);
        if (!element.isJsonArray()) return FloatModifier.identity();
        JsonArray arr = element.getAsJsonArray();
        if (arr.isEmpty()) return FloatModifier.identity();
        List<FloatModifier> parts = new ArrayList<>(arr.size());
        for (JsonElement el : arr) {
            if (el.isJsonObject()) parts.add(parse(el.getAsJsonObject(), contextId));
        }
        if (parts.isEmpty()) return FloatModifier.identity();
        if (parts.size() == 1) return parts.get(0);
        final FloatModifier[] chain = parts.toArray(new FloatModifier[0]);
        return base -> {
            float v = base;
            for (FloatModifier m : chain) v = m.apply(v);
            return v;
        };
    }
}
