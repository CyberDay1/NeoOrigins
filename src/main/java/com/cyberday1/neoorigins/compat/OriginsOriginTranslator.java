package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/**
 * Normalizes Origins-format origin JSONs to NeoOrigins format.
 * Safe to call on both Origins-format and already-valid NeoOrigins-format origins;
 * operations are no-ops when the field is already in the expected format.
 */
public final class OriginsOriginTranslator {

    private OriginsOriginTranslator() {}

    /**
     * Normalize an origin JSON (Origins or NeoOrigins format) to NeoOrigins format.
     * Returns a new JsonObject; the input is not modified.
     * Never throws — all errors are caught and logged, returning a best-effort result.
     *
     * Note: the "id" field is added by OriginDataManager AFTER this call, not here.
     */
    public static JsonObject normalize(Identifier id, JsonObject src) {
        try {
            return doNormalize(id, src);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            NeoOrigins.LOGGER.warn("OriginsCompat: Failed to normalize origin {} ({}): {}", id, reason, e);
            // Return a shallow copy so the CODEC can at least attempt to parse the original
            return src.deepCopy();
        }
    }

    private static JsonObject doNormalize(Identifier id, JsonObject src) {
        JsonObject out = new JsonObject();

        // ---- name ----
        out.addProperty("name", extractComponent(src.has("name") ? src.get("name") : null,
            id.getNamespace() + ".origin." + id.getPath().replace('/', '.') + ".name"));

        // ---- description ----
        out.addProperty("description", extractComponent(src.has("description") ? src.get("description") : null,
            id.getNamespace() + ".origin." + id.getPath().replace('/', '.') + ".description"));

        // ---- icon: unwrap {"item": "..."} or {"id": "..."} object to string ----
        if (src.has("icon")) {
            JsonElement iconEl = src.get("icon");
            if (iconEl.isJsonObject()) {
                JsonObject iconObj = iconEl.getAsJsonObject();
                if (iconObj.has("item")) {
                    out.addProperty("icon", iconObj.get("item").getAsString());
                } else if (iconObj.has("id")) {
                    out.addProperty("icon", iconObj.get("id").getAsString());
                }
                // else: unknown icon object format — omit (will use CODEC default)
            } else if (iconEl.isJsonPrimitive()) {
                out.add("icon", iconEl);
            }
        }

        // ---- impact: integer → string ----
        if (src.has("impact")) {
            JsonElement impactEl = src.get("impact");
            if (impactEl.isJsonPrimitive() && impactEl.getAsJsonPrimitive().isNumber()) {
                int level = impactEl.getAsInt();
                String impactStr = switch (level) {
                    case 0  -> "none";
                    case 1  -> "low";
                    case 2  -> "medium";
                    case 3  -> "high";
                    default -> "none";
                };
                out.addProperty("impact", impactStr);
            } else {
                out.add("impact", impactEl); // already a string — pass through
            }
        }

        // ---- hidden → unchoosable ----
        if (src.has("hidden")) {
            out.add("unchoosable", src.get("hidden"));
        } else if (src.has("unchoosable")) {
            out.add("unchoosable", src.get("unchoosable"));
        }

        // ---- pass-through fields ----
        if (src.has("order"))   out.add("order", src.get("order"));
        if (src.has("special")) out.add("special", src.get("special"));
        if (src.has("upgrades")) out.add("upgrades", src.get("upgrades"));

        // ---- powers: rewrite multiple IDs to synthetic sub-power IDs ----
        if (src.has("powers")) {
            JsonArray translatedPowers = new JsonArray();
            for (JsonElement el : src.getAsJsonArray("powers")) {
                if (!el.isJsonPrimitive()) continue;
                String powerIdStr = el.getAsString();
                Identifier powerIdent = Identifier.tryParse(powerIdStr);
                if (powerIdent != null && OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.containsKey(powerIdent)) {
                    // Replace the multiple power ID with all its synthetic sub-power IDs
                    for (Identifier synthId : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.get(powerIdent)) {
                        translatedPowers.add(synthId.toString());
                    }
                } else {
                    translatedPowers.add(powerIdStr);
                }
            }
            out.add("powers", translatedPowers);
        }

        return out;
    }

    /**
     * Extract a string from a potentially-complex component element.
     * Handles: plain string, {"translate": "..."}, {"text": "..."}.
     * Falls back to {@code fallback} if null or unrecognized format.
     */
    private static String extractComponent(JsonElement el, String fallback) {
        if (el == null) return fallback;
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("translate")) return obj.get("translate").getAsString();
            if (obj.has("text"))      return obj.get("text").getAsString();
        }
        return fallback;
    }
}
