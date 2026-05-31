package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Locale;

/**
 * Pure (no Minecraft-server) JSON shaping for the 2.1 creator's on-disk
 * datapack. Kept separate from {@link CustomPackWriter} so the shape rules —
 * the parts that silently break — are headless-testable (see
 * {@code dev.CustomPackCheck}).
 *
 * <p>Shape rules, verified against the codebase:
 * <ul>
 *   <li>Origin JSON carries <b>no {@code id}</b> (OriginDataManager injects it
 *       from the resource path) and <b>no {@code type}</b> (origins aren't
 *       typed; only powers are).</li>
 *   <li>Power JSON <b>does</b> carry {@code type}.</li>
 *   <li>A layer file is merged <b>additively</b> by LayerDataManager
 *       ({@code mergeOrigins}: append + dedup unless {@code "replace": true}),
 *       so the creator's layer file omits {@code replace} and just lists its
 *       origin ids — accumulating across saves within our single pack file.</li>
 *   <li>Author-entered name/description are emitted as {@code {"text": …}} so
 *       they render literally rather than as translation keys.</li>
 * </ul>
 */
public final class CustomPackSerializer {

    private CustomPackSerializer() {}

    /** The origin definition body — no {@code id}, no {@code type}. */
    public static JsonObject originJson(OriginDraft d) {
        JsonObject o = new JsonObject();
        o.add("name", text(d.name));
        o.add("description", text(d.description));
        o.addProperty("icon", d.icon.toString());
        o.addProperty("impact", impactName(d.impact));
        o.addProperty("order", d.order);

        JsonArray powers = new JsonArray();
        for (OriginDraft.PowerDraft p : d.powers) {
            powers.add(p.powerId.toString());
        }
        o.add("powers", powers);
        o.add("upgrades", new JsonArray());
        return o;
    }

    /** A power definition body — {@code rawJson} with {@code type} ensured. */
    public static JsonObject powerJson(OriginDraft.PowerDraft p) {
        JsonObject body;
        try {
            var el = JsonParser.parseString(p.rawJson == null || p.rawJson.isBlank()
                ? "{}" : p.rawJson);
            body = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
        } catch (RuntimeException e) {
            body = new JsonObject();
        }
        pruneBlankArrayEntries(body);
        // type is authoritative from the draft, not the raw body.
        body.addProperty("type", p.typeId);
        return body;
    }

    /**
     * Drop blank ({@code ""}) entries from any array in {@code obj} (and remove
     * arrays left empty), recursing into nested objects/arrays. Scalar-string
     * list widgets ({@code ArrayStringRow}) keep blank entries in the working
     * JSON so a freshly-added input survives a form rebuild; this strips them on
     * export, mirroring the website serializer's {@code pruneForWire}.
     */
    private static void pruneBlankArrayEntries(JsonObject obj) {
        java.util.Map<String, JsonArray> replace = new java.util.LinkedHashMap<>();
        java.util.List<String> drop = new java.util.ArrayList<>();
        for (var e : obj.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonArray()) {
                JsonArray cleaned = pruneArray(v.getAsJsonArray());
                if (cleaned.isEmpty()) drop.add(e.getKey());
                else replace.put(e.getKey(), cleaned);
            } else if (v.isJsonObject()) {
                pruneBlankArrayEntries(v.getAsJsonObject());
            }
        }
        for (var r : replace.entrySet()) obj.add(r.getKey(), r.getValue());
        for (String k : drop) obj.remove(k);
    }

    private static JsonArray pruneArray(JsonArray arr) {
        JsonArray out = new JsonArray();
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()
                    && el.getAsString().trim().isEmpty()) {
                continue; // drop blank scalar entry
            }
            if (el.isJsonObject()) pruneBlankArrayEntries(el.getAsJsonObject());
            out.add(el);
        }
        return out;
    }

    /**
     * Additive layer patch: append {@code originId} into {@code existing}'s
     * {@code origins} array (deduplicated), mirroring
     * {@code LayerDataManager.mergeOrigins}. {@code existing} may be {@code null}
     * (first origin for this layer in our pack).
     */
    public static JsonObject layerPatch(JsonObject existing, String originId) {
        JsonObject layer = existing != null ? existing : new JsonObject();
        JsonArray origins = layer.has("origins") && layer.get("origins").isJsonArray()
            ? layer.getAsJsonArray("origins") : new JsonArray();
        for (var e : origins) {
            if (e.isJsonPrimitive() && e.getAsString().equals(originId)) {
                layer.add("origins", origins);
                return layer; // already present — no-op
            }
        }
        origins.add(originId);
        layer.add("origins", origins);
        return layer;
    }

    private static JsonObject text(String s) {
        JsonObject t = new JsonObject();
        t.addProperty("text", s == null ? "" : s);
        return t;
    }

    /** Impact dots 0–3 → the lowercase enum name Impact.CODEC accepts. */
    private static String impactName(int impact) {
        return switch (Math.max(0, Math.min(3, impact))) {
            case 1 -> "low";
            case 2 -> "medium";
            case 3 -> "high";
            default -> "none";
        };
    }
}
