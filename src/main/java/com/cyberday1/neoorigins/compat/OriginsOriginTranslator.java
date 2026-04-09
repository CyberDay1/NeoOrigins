package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

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
    public static JsonObject normalize(ResourceLocation id, JsonObject src) {
        try {
            return doNormalize(id, src);
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            NeoOrigins.LOGGER.warn("OriginsCompat: Failed to normalize origin {} ({}): {}", id, reason, e);
            // Return a shallow copy so the CODEC can at least attempt to parse the original
            return src.deepCopy();
        }
    }

    private static JsonObject doNormalize(ResourceLocation id, JsonObject src) {
        JsonObject out = new JsonObject();

        // ---- name ----
        // Prefer explicit plain/text component; fall back to deriving a readable name from the ID
        // (avoids displaying raw translation keys when pack lang files are not loaded by NeoForge)
        out.addProperty("name", extractLiteralOrDerive(src.has("name") ? src.get("name") : null,
            deriveNameFromId(id)));

        // ---- description ----
        // Same approach: use explicit text if present, otherwise empty string
        out.addProperty("description", extractLiteralOrDerive(src.has("description") ? src.get("description") : null,
            ""));

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
                ResourceLocation powerIdent = ResourceLocation.tryParse(powerIdStr);
                if (powerIdent != null && OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.containsKey(powerIdent)) {
                    // Replace the multiple power ID with all its synthetic sub-power IDs
                    for (ResourceLocation synthId : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.get(powerIdent)) {
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
     * Extract a literal display string from a component element, using {@code fallback} when the
     * value is a translation key ({"translate": "..."}) or missing. This prevents raw translation
     * keys from being shown in the UI when the external pack's lang files aren't loaded by NeoForge.
     *
     * Handles: plain string → returned as-is; {"text": "..."} → text value;
     *          null or {"translate": "..."} → {@code fallback}.
     */
    private static String extractLiteralOrDerive(JsonElement el, String fallback) {
        if (el == null) return fallback;
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("text")) return obj.get("text").getAsString();
            // {"translate": "..."} — we cannot resolve this without the pack's lang file,
            // so return fallback (derived name or empty string) for a readable display.
        }
        return fallback;
    }

    /**
     * Derive a human-readable display name from an origin ResourceLocation.
     * Takes the last path segment (after the last '/'), replaces '_' with spaces,
     * and title-cases each word. E.g. "origins-plus-plus:voidling/voidling" → "Voidling".
     */
    private static String deriveNameFromId(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0) path = path.substring(slash + 1);
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0)));
            sb.append(part.substring(1).toLowerCase());
        }
        return sb.isEmpty() ? id.getPath() : sb.toString();
    }
}
