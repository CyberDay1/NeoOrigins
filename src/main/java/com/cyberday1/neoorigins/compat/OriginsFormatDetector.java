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
                && json.get("impact").getAsJsonPrimitive().isNumber()) {
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

    /** Returns the type string, or empty string if not present. */
    public static String getType(JsonObject json) {
        return json.has("type") ? json.get("type").getAsString() : "";
    }
}
