package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

/**
 * Round-trips an {@link OriginDraft} through a single JSON string so the whole
 * draft travels in one network field ({@code SaveCustomOriginPayload}) instead
 * of a bespoke per-field {@code StreamCodec}. The server re-parses and (Phase 4)
 * validates before handing to {@link CustomPackWriter}.
 *
 * <p>This is the wire/transport shape of the <em>draft</em> — distinct from
 * {@link CustomPackSerializer}, which produces the final on-disk datapack JSON.
 * Pure (no server); round-trip is headless-tested.
 */
public final class OriginDraftJson {

    private static final Gson GSON = new Gson();

    private OriginDraftJson() {}

    public static String toJson(OriginDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("namespace", d.namespace);
        o.addProperty("idPath", d.idPath);
        o.addProperty("name", d.name);
        o.addProperty("description", d.description);
        o.addProperty("icon", d.icon.toString());
        o.addProperty("impact", d.impact);
        o.addProperty("order", d.order);
        o.addProperty("layerId", d.layerId.toString());

        JsonArray powers = new JsonArray();
        for (OriginDraft.PowerDraft p : d.powers) {
            JsonObject pj = new JsonObject();
            pj.addProperty("powerId", p.powerId.toString());
            pj.addProperty("typeId", p.typeId);
            pj.addProperty("rawJson", p.rawJson);
            powers.add(pj);
        }
        o.add("powers", powers);
        return GSON.toJson(o);
    }

    /** @throws IllegalArgumentException if the JSON is malformed/incomplete. */
    public static OriginDraft fromJson(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            OriginDraft d = new OriginDraft();
            d.namespace = str(o, "namespace", d.namespace);
            d.idPath = str(o, "idPath", d.idPath);
            d.name = str(o, "name", "");
            d.description = str(o, "description", "");
            d.icon = id(o, "icon", d.icon);
            d.impact = o.has("impact") ? o.get("impact").getAsInt() : d.impact;
            d.order = o.has("order") ? o.get("order").getAsInt() : d.order;
            d.layerId = id(o, "layerId", d.layerId);

            if (o.has("powers") && o.get("powers").isJsonArray()) {
                for (var e : o.getAsJsonArray("powers")) {
                    JsonObject pj = e.getAsJsonObject();
                    var pd = new OriginDraft.PowerDraft(
                        Identifier.parse(pj.get("powerId").getAsString()),
                        pj.has("typeId") ? pj.get("typeId").getAsString() : "");
                    pd.rawJson = pj.has("rawJson") ? pj.get("rawJson").getAsString() : "{}";
                    d.powers.add(pd);
                }
            }
            return d;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed OriginDraft JSON: " + e.getMessage(), e);
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static Identifier id(JsonObject o, String k, Identifier def) {
        return o.has(k) ? Identifier.parse(o.get(k).getAsString()) : def;
    }
}
