package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft.PowerDraft;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.Map;

/**
 * Pure inverse of {@link CustomPackSerializer}: an origin definition body plus
 * its referenced power bodies → an editable {@link OriginDraft}. This is the
 * keystone for re-opening, cloning and overriding existing origins in the 2.1
 * creator (the network layer reads the live origin/power JSON, this turns it
 * back into a draft). Kept Minecraft-server-free so the shape rules stay
 * headless round-trip-testable against the serializer (see {@code CustomPackCheck}).
 *
 * @param idPath  the origin's path segment (the serializer omits {@code id};
 *                the data manager injects it from the file path, so it is
 *                supplied here by the caller).
 * @param layerId the layer the origin belongs to (membership lives in the
 *                layer file, not the origin body — supplied by the caller).
 */
public final class OriginDraftReader {

    private OriginDraftReader() {}

    public static OriginDraft fromJson(String idPath, Identifier layerId,
                                       JsonObject originJson,
                                       Map<String, JsonObject> powerBodies) {
        OriginDraft d = new OriginDraft();
        d.idPath = idPath;
        if (layerId != null) d.layerId = layerId;
        d.name = plainText(originJson.get("name"));
        d.description = plainText(originJson.get("description"));
        if (originJson.has("icon")) {
            try { d.icon = Identifier.parse(originJson.get("icon").getAsString()); }
            catch (RuntimeException ignored) { /* keep default */ }
        }
        d.impact = impactValue(originJson.get("impact"));
        if (originJson.has("order")) {
            try { d.order = originJson.get("order").getAsInt(); }
            catch (RuntimeException ignored) { /* keep 0 */ }
        }

        if (originJson.has("powers") && originJson.get("powers").isJsonArray()) {
            for (JsonElement pe : originJson.getAsJsonArray("powers")) {
                if (!pe.isJsonPrimitive()) continue;
                String powerIdStr = pe.getAsString();
                JsonObject body = powerBodies.get(powerIdStr);
                if (body == null) continue;
                String typeId = body.has("type") ? body.get("type").getAsString() : "";
                JsonObject raw = body.deepCopy();
                raw.remove("type");
                Identifier pid;
                try { pid = Identifier.parse(powerIdStr); }
                catch (RuntimeException e) { continue; }
                PowerDraft pd = new PowerDraft(pid, typeId);
                pd.rawJson = raw.toString();
                d.powers.add(pd);
            }
        }
        return d;
    }

    /** Inverse of {@code CustomPackSerializer.text}: {"text":s} | "s" → s. */
    private static String plainText(JsonElement el) {
        if (el == null || el.isJsonNull()) return "";
        if (el.isJsonObject() && el.getAsJsonObject().has("text")) {
            return el.getAsJsonObject().get("text").getAsString();
        }
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            return el.getAsString();
        }
        return el.toString();
    }

    /** Inverse of {@code CustomPackSerializer.impactName}; accepts ints too. */
    private static int impactValue(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0;
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return Math.max(0, Math.min(3, el.getAsInt()));
        }
        return switch (el.getAsString().toLowerCase(java.util.Locale.ROOT)) {
            case "low" -> 1;
            case "medium" -> 2;
            case "high" -> 3;
            default -> 0;
        };
    }
}
