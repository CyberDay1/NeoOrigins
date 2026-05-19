package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

/**
 * Wire (re)serialization of a {@link MobOriginDraft} for the Save payload —
 * the mob-side analogue of {@link OriginDraftJson}. Lossless round-trip;
 * exercised headlessly by {@code customPackCheck}.
 */
public final class MobOriginDraftJson {

    private static final Gson GSON = new Gson();

    private MobOriginDraftJson() {}

    public static String toJson(MobOriginDraft d) {
        JsonObject o = new JsonObject();
        o.addProperty("idPath", d.idPath);
        o.addProperty("name", d.name);
        o.addProperty("description", d.description);
        o.addProperty("icon", d.icon.toString());
        o.addProperty("targetEntityType", d.targetEntityType);
        o.addProperty("targetEntityTag", d.targetEntityTag);
        JsonArray types = new JsonArray();
        d.targetEntityTypes.forEach(types::add);
        o.add("targetEntityTypes", types);
        JsonArray powers = new JsonArray();
        for (OriginDraft.PowerDraft p : d.powers) {
            JsonObject pj = new JsonObject();
            pj.addProperty("powerId", p.powerId == null ? "" : p.powerId.toString());
            pj.addProperty("typeId", p.typeId);
            pj.addProperty("rawJson", p.rawJson);
            powers.add(pj);
        }
        o.add("powers", powers);
        return GSON.toJson(o);
    }

    public static MobOriginDraft fromJson(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            MobOriginDraft d = new MobOriginDraft();
            d.idPath = str(o, "idPath", d.idPath);
            d.name = str(o, "name", "");
            d.description = str(o, "description", "");
            if (o.has("icon")) {
                ResourceLocation ic = ResourceLocation.tryParse(o.get("icon").getAsString());
                if (ic != null) d.icon = ic;
            }
            d.targetEntityType = str(o, "targetEntityType", "");
            d.targetEntityTag = str(o, "targetEntityTag", "");
            d.targetEntityTypes.clear();
            if (o.has("targetEntityTypes") && o.get("targetEntityTypes").isJsonArray()) {
                for (var e : o.getAsJsonArray("targetEntityTypes")) d.targetEntityTypes.add(e.getAsString());
            }
            if (o.has("powers") && o.get("powers").isJsonArray()) {
                for (var e : o.getAsJsonArray("powers")) {
                    JsonObject pj = e.getAsJsonObject();
                    String pid = pj.has("powerId") ? pj.get("powerId").getAsString() : "";
                    var pd = new OriginDraft.PowerDraft(
                        pid.isBlank() ? null : ResourceLocation.parse(pid),
                        pj.has("typeId") ? pj.get("typeId").getAsString() : "");
                    pd.rawJson = pj.has("rawJson") ? pj.get("rawJson").getAsString() : "{}";
                    d.powers.add(pd);
                }
            }
            return d;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed MobOriginDraft JSON: " + e.getMessage(), e);
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }
}
