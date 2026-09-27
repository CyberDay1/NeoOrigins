package com.cyberday1.neoorigins.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * Renames power ids inside JSON when the creator gives powers new ids, so a
 * resource cost, a power_active check or a tier_powers entry keeps pointing at
 * the renamed power. Shipped data writes these as full ids; a {@code type} value
 * is never a power reference and is left alone.
 */
public final class PowerIdRemap {

    private PowerIdRemap() {}

    /** A copy of {@code el} with every string equal to a key of {@code ids} replaced by its value. */
    public static JsonElement rewrite(JsonElement el, Map<String, String> ids) {
        if (el == null || ids.isEmpty()) return el == null ? null : el.deepCopy();
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String to = ids.get(el.getAsString());
            return to != null ? new JsonPrimitive(to) : el.deepCopy();
        }
        if (el.isJsonArray()) {
            JsonArray out = new JsonArray();
            for (JsonElement x : el.getAsJsonArray()) out.add(rewrite(x, ids));
            return out;
        }
        if (el.isJsonObject()) {
            JsonObject out = new JsonObject();
            for (var e : el.getAsJsonObject().entrySet()) {
                out.add(e.getKey(), "type".equals(e.getKey()) ? e.getValue().deepCopy() : rewrite(e.getValue(), ids));
            }
            return out;
        }
        return el.deepCopy();
    }
}
