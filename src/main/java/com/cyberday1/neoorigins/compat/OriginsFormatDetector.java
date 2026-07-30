package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonObject;

/** Detects whether a JSON object is in Origins/Apace mod format. */
public final class OriginsFormatDetector {

    private OriginsFormatDetector() {}

    /**
     * Returns true if the JSON's "type" field is in the origins: or apace: namespace.
     * Used for power JSONs.
     */
    public static boolean isOriginsFormat(JsonObject json) {
        if (!json.has("type")) return false;
        String type = json.get("type").getAsString();
        return type.startsWith("origins:") || type.startsWith("apace:");
    }

    /**
     * Returns true if the JSON looks like an Origins-format origin (not a power).
     * Detects by structural markers: integer impact, object icon, or object name/description.
     */
    public static boolean isOriginsOriginFormat(JsonObject json) {
        if (json.has("impact") && json.get("impact").isJsonPrimitive()
                && isNumericImpact(json.get("impact").getAsJsonPrimitive())) {
            return true;
        }
        if (json.has("icon") && json.get("icon").isJsonObject()) {
            return true;
        }
        if (json.has("name") && json.get("name").isJsonObject()) {
            return true;
        }
        if (json.has("description") && json.get("description").isJsonObject()) {
            return true;
        }
        if (json.has("hidden")) {
            return true;
        }
        return false;
    }

    /**
     * True if an impact primitive is the Origins numeric form — either a real
     * JSON number ({@code "impact": 2}) or a quoted number ({@code "impact": "2"},
     * common in real Apoli packs). The quoted-numeric case is the integer form
     * with quotes around it, so it should be treated as Origins format, not as a
     * native string impact ("low"/"medium"/...).
     */
    private static boolean isNumericImpact(com.google.gson.JsonPrimitive prim) {
        if (prim.isNumber()) return true;
        if (prim.isString()) {
            try {
                Integer.parseInt(prim.getAsString().trim());
                return true;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }

    /** Returns the type string, or empty string if not present. */
    public static String getType(JsonObject json) {
        return json.has("type") ? json.get("type").getAsString() : "";
    }

    /**
     * Apoli-family namespaces that are dispatch-aliases of {@code origins:}.
     * Apoli is the power library Origins re-exports, so a power declared as
     * {@code apoli:resource} is the same type as {@code origins:resource};
     * Apugli is the common addon that follows the same vocabulary.
     */
    private static final java.util.Set<String> APOLI_FAMILY_NS = java.util.Set.of("apoli", "apugli");

    /** True for a namespace {@link #canonicalizePowerType} rewrites to {@code origins:}. */
    public static boolean isApoliFamily(String namespace) {
        return APOLI_FAMILY_NS.contains(namespace);
    }

    /**
     * Rewrites an Apoli-family power {@code type} to the canonical {@code origins:}
     * namespace, in place, and returns the resulting type string. This lets the
     * compat dispatch — which keys on {@code origins:}/{@code apace:} — recognize
     * real-world packs (e.g. CrystalWeaver) that declare power types in the
     * {@code apoli:} namespace, instead of letting them fall through to the native
     * codec and log "Unknown power type". Mirrors the namespace stripping the
     * action/condition parsers already perform (see {@code ActionParser}).
     *
     * <p>No-op (returns the unchanged type) for namespaces outside the family,
     * including native {@code neoorigins:} and already-canonical {@code origins:}/
     * {@code apace:} powers.
     */
    public static String canonicalizePowerType(JsonObject json) {
        if (!json.has("type")) return "";
        String type = json.get("type").getAsString();
        int colon = type.indexOf(':');
        if (colon > 0 && APOLI_FAMILY_NS.contains(type.substring(0, colon))) {
            String canonical = "origins:" + type.substring(colon + 1);
            json.addProperty("type", canonical);
            return canonical;
        }
        return type;
    }
}
