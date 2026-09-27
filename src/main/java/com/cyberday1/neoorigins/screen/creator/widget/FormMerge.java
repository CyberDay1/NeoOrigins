package com.cyberday1.neoorigins.screen.creator.widget;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Applies a form edit to the JSON the form was opened on, keeping whatever the
 * form could not read. {@code orig} is the stored value, {@code before} the row's
 * reading of it at build time, {@code after} the row's value now; only the
 * difference between {@code before} and {@code after} is written.
 */
// hub: neoorigins/form-round-trip.md
public final class FormMerge {

    private FormMerge() {}

    public static JsonElement merge(JsonElement orig, JsonElement before, JsonElement after) {
        if (Objects.equals(before, after)) return orig == null ? null : orig.deepCopy();
        if (after == null) return null;
        if (orig != null && before != null) {
            if (orig.isJsonObject() && before.isJsonObject() && after.isJsonObject()) {
                return mergeObject(orig.getAsJsonObject(), before.getAsJsonObject(), after.getAsJsonObject());
            }
            if (orig.isJsonArray() && before.isJsonArray() && after.isJsonArray()) {
                return mergeArray(orig.getAsJsonArray(), before.getAsJsonArray(), after.getAsJsonArray());
            }
        }
        return after.deepCopy();
    }

    private static JsonObject mergeObject(JsonObject orig, JsonObject before, JsonObject after) {
        JsonObject out = orig.deepCopy();
        Set<String> keys = new LinkedHashSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String k : keys) {
            JsonElement v = merge(orig.get(k), before.get(k), after.get(k));
            if (Objects.equals(before.get(k), after.get(k))) continue;
            if (v == null) out.remove(k);
            else out.add(k, v);
        }
        return out;
    }

    // Unchanged entries keep their original (and whatever the form missed in
    // them); edited ones merge by position when the form read the array whole.
    private static JsonArray mergeArray(JsonArray orig, JsonArray before, JsonArray after) {
        boolean aligned = orig.size() == before.size();
        boolean[] used = new boolean[before.size()];
        JsonArray out = new JsonArray();
        for (int i = 0; i < after.size(); i++) {
            JsonElement a = after.get(i);
            int match = -1;
            if (aligned && i < before.size() && !used[i] && Objects.equals(before.get(i), a)) match = i;
            for (int j = 0; match < 0 && aligned && j < before.size(); j++) {
                if (!used[j] && Objects.equals(before.get(j), a)) match = j;
            }
            if (match >= 0) {
                used[match] = true;
                out.add(orig.get(match).deepCopy());
            } else if (aligned && i < before.size() && !used[i]) {
                used[i] = true;
                out.add(merge(orig.get(i), before.get(i), a));
            } else {
                out.add(a.deepCopy());
            }
        }
        return out;
    }
}
