package com.cyberday1.neoorigins.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;

/**
 * Item ids that vanilla renamed after 1.20, mapped to their 1.21.1 names. Sibling
 * of {@link LegacyEntityIds}; only ids that no longer resolve are listed.
 */
public final class LegacyItemIds {

    private static final Map<String, String> REMAP = Map.of(
        // 1.20.5 renamed the turtle scute when the armadillo scute arrived.
        "minecraft:scute", "minecraft:turtle_scute"
    );

    private LegacyItemIds() {}

    /** Remaps a legacy item id string; returns the input unchanged if unmapped. */
    public static String remap(String itemId) {
        if (itemId == null) return null;
        return REMAP.getOrDefault(itemId, itemId);
    }

    /** An origin icon (an id string, or an object with item/id) with a renamed item id remapped; else the input. */
    public static JsonElement remapIcon(JsonElement icon) {
        if (icon instanceof JsonPrimitive p && p.isString()) {
            String id = remap(p.getAsString());
            return id.equals(p.getAsString()) ? icon : new JsonPrimitive(id);
        }
        if (icon instanceof JsonObject obj) {
            for (String key : new String[] {"item", "id"}) {
                if (obj.get(key) instanceof JsonPrimitive p && p.isString()) {
                    String id = remap(p.getAsString());
                    if (id.equals(p.getAsString())) return icon;
                    JsonObject copy = obj.deepCopy();
                    copy.addProperty(key, id);
                    return copy;
                }
            }
        }
        return icon;
    }
}
