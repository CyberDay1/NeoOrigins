package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;

/**
 * Normalizes Origins-format origin JSONs to NeoOrigins format.
 * Safe to call on both Origins-format and already-valid NeoOrigins-format origins;
 * operations are no-ops when the field is already in the expected format.
 */
public final class OriginsOriginTranslator {

    private OriginsOriginTranslator() {}

    /**
     * Top-level keys that doNormalize actively translates or re-emits itself.
     * These must NOT also be copied raw, or the translated value would be
     * overwritten/duplicated. Everything outside this set is passed through
     * verbatim so native NeoOrigins fields (tier_powers, required_mods,
     * spawn_location, ...) and future additions survive translation of
     * hybrid or Origins-format packs. The native CODEC ignores keys it
     * doesn't know, so unknown Origins-only keys (e.g. loading_priority)
     * are harmless when passed through.
     */
    private static final Set<String> HANDLED_KEYS = Set.of(
        "name",        // re-emitted via extractLiteralOrDerive
        "description", // re-emitted via extractLiteralOrDerive
        "icon",        // re-emitted as-is when present
        "impact",      // int → string translation
        "hidden",      // Origins-only: renamed to "unchoosable" — must not leak raw
        "unchoosable", // re-emitted (native spelling)
        "order",       // re-emitted as-is
        "special",     // re-emitted as-is
        "upgrades",    // re-emitted as-is
        "powers",      // multiple-power IDs rewritten to synthetic sub-power IDs
        "tier_powers"  // evolution add/remove lists rewritten the same way
    );

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
        // Prefer explicit plain/text component; fall back to deriving a readable name from the ID
        // (avoids displaying raw translation keys when pack lang files are not loaded by NeoForge)
        out.addProperty("name", extractLiteralOrDerive(src.has("name") ? src.get("name") : null,
            deriveNameFromId(id)));

        // ---- description ----
        // Same approach: use explicit text if present, otherwise empty string
        out.addProperty("description", extractLiteralOrDerive(src.has("description") ? src.get("description") : null,
            ""));

        // ---- icon: pass through as-is (IconCodec handles all formats) ----
        if (src.has("icon")) {
            out.add("icon", src.get("icon"));
        }

        // ---- impact: integer → string ----
        if (src.has("impact")) {
            JsonElement impactEl = src.get("impact");
            Integer level = null;
            if (impactEl.isJsonPrimitive()) {
                var prim = impactEl.getAsJsonPrimitive();
                if (prim.isNumber()) {
                    level = prim.getAsInt();
                } else if (prim.isString()) {
                    // Real Apoli packs commonly quote the numeric impact
                    // ("impact": "2"); treat that as the integer form too.
                    try {
                        level = Integer.parseInt(prim.getAsString().trim());
                    } catch (NumberFormatException ignored) {
                        // genuine named string ("low"/"medium"/...) — pass through
                    }
                }
            }
            if (level != null) {
                String impactStr = switch (level) {
                    case 0  -> "none";
                    case 1  -> "low";
                    case 2  -> "medium";
                    case 3  -> "high";
                    default -> "none";
                };
                out.addProperty("impact", impactStr);
            } else {
                out.add("impact", impactEl); // already a named string — pass through
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
            out.add("powers", rewritePowerIds(src.getAsJsonArray("powers")));
        }

        // ---- tier_powers: rewrite each overlay's add/remove lists too ----
        // The base `powers` array is expanded above, so a multiple-type power
        // referenced in a tier's add/remove must be expanded to the same
        // synthetic sub-power IDs — otherwise the parent ID has no registered
        // holder and the add/remove silently no-ops (evolution HP/stat powers
        // never apply or never get cleared).
        if (src.has("tier_powers") && src.get("tier_powers").isJsonArray()) {
            JsonArray translatedTiers = new JsonArray();
            for (JsonElement tierEl : src.getAsJsonArray("tier_powers")) {
                if (!tierEl.isJsonObject()) { translatedTiers.add(tierEl); continue; }
                JsonObject overlay = tierEl.getAsJsonObject().deepCopy();
                if (overlay.has("add") && overlay.get("add").isJsonArray()) {
                    overlay.add("add", rewritePowerIds(overlay.getAsJsonArray("add")));
                }
                if (overlay.has("remove") && overlay.get("remove").isJsonArray()) {
                    overlay.add("remove", rewritePowerIds(overlay.getAsJsonArray("remove")));
                }
                translatedTiers.add(overlay);
            }
            out.add("tier_powers", translatedTiers);
        }

        // ---- pass through all remaining keys verbatim ----
        // Anything not actively translated above is copied as-is so native
        // NeoOrigins fields and future keys are not silently dropped.
        for (Map.Entry<String, JsonElement> entry : src.entrySet()) {
            if (!HANDLED_KEYS.contains(entry.getKey())) {
                out.add(entry.getKey(), entry.getValue());
            }
        }

        return out;
    }

    /**
     * Rewrite ONLY the multiple-power references on an origin JSON, in place.
     * Applies the same {@code powers} and {@code tier_powers} add/remove rewrite
     * that {@link #doNormalize} performs, but touches no other field — so it is
     * safe to run on native-format origins that must NOT be otherwise translated.
     *
     * Idempotent: {@link #rewritePowerIds} only rewrites IDs that are keys in
     * {@link OriginsMultipleExpander#MULTIPLE_EXPANSION_MAP} (parent multiple IDs);
     * synthetic sub-power IDs are not keys, so a second pass is a no-op. This makes
     * an unconditional call safe even after {@link #normalize} already ran.
     */
    public static JsonObject rewriteMultiplePowerRefs(JsonObject json) {
        // ---- powers: rewrite multiple IDs to synthetic sub-power IDs ----
        if (json.has("powers") && json.get("powers").isJsonArray()) {
            json.add("powers", rewritePowerIds(json.getAsJsonArray("powers")));
        }

        // ---- tier_powers: rewrite each overlay's add/remove lists too ----
        if (json.has("tier_powers") && json.get("tier_powers").isJsonArray()) {
            JsonArray translatedTiers = new JsonArray();
            for (JsonElement tierEl : json.getAsJsonArray("tier_powers")) {
                if (!tierEl.isJsonObject()) { translatedTiers.add(tierEl); continue; }
                JsonObject overlay = tierEl.getAsJsonObject().deepCopy();
                if (overlay.has("add") && overlay.get("add").isJsonArray()) {
                    overlay.add("add", rewritePowerIds(overlay.getAsJsonArray("add")));
                }
                if (overlay.has("remove") && overlay.get("remove").isJsonArray()) {
                    overlay.add("remove", rewritePowerIds(overlay.getAsJsonArray("remove")));
                }
                translatedTiers.add(overlay);
            }
            json.add("tier_powers", translatedTiers);
        }
        return json;
    }

    /**
     * Rewrites a list of power-ID strings, replacing any {@code multiple}-type
     * power ID with its expanded synthetic sub-power IDs (from
     * {@link OriginsMultipleExpander#MULTIPLE_EXPANSION_MAP}). Non-multiple IDs
     * and non-string entries pass through unchanged. Shared by the base
     * {@code powers} array and every {@code tier_powers} add/remove list so a
     * multiple power resolves to the same registered holders everywhere.
     */
    private static JsonArray rewritePowerIds(JsonArray ids) {
        JsonArray out = new JsonArray();
        for (JsonElement el : ids) {
            if (!el.isJsonPrimitive()) { out.add(el); continue; }
            String powerIdStr = el.getAsString();
            Identifier powerIdent = Identifier.tryParse(powerIdStr);
            if (powerIdent != null && OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.containsKey(powerIdent)) {
                for (Identifier synthId : OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.get(powerIdent)) {
                    out.add(synthId.toString());
                }
            } else {
                out.add(powerIdStr);
            }
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
     * Derive a human-readable display name from an origin Identifier.
     * Takes the last path segment (after the last '/'), replaces '_' with spaces,
     * and title-cases each word. E.g. "origins-plus-plus:voidling/voidling" → "Voidling".
     */
    private static String deriveNameFromId(Identifier id) {
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
