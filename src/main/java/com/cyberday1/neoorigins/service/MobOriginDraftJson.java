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

        // Spawn rules (Phase 4) — flat fields on the wire, gathered into
        // spawn_rules / location at on-disk serialization time.
        o.addProperty("spawnRulesEnabled", d.spawnRulesEnabled);
        o.addProperty("weight", d.weight);
        o.addProperty("timeOfDay", d.timeOfDay);
        JsonArray reasons = new JsonArray();
        d.spawnReasons.forEach(reasons::add);
        o.add("spawnReasons", reasons);
        o.addProperty("mutexGroup", d.mutexGroup);
        o.addProperty("replace", d.replace);
        o.addProperty("yRangeEnabled", d.yRangeEnabled);
        o.addProperty("yRangeMin", d.yRangeMin);
        o.addProperty("yRangeMax", d.yRangeMax);
        o.addProperty("lightRangeEnabled", d.lightRangeEnabled);
        o.addProperty("lightRangeMin", d.lightRangeMin);
        o.addProperty("lightRangeMax", d.lightRangeMax);
        o.addProperty("locationDimension", d.locationDimension);
        o.addProperty("locationBiome", d.locationBiome);
        o.addProperty("locationBiomeTag", d.locationBiomeTag);
        JsonArray locBiomes = new JsonArray();
        d.locationBiomes.forEach(locBiomes::add);
        o.add("locationBiomes", locBiomes);
        o.addProperty("locationStructure", d.locationStructure);
        o.addProperty("locationStructureTag", d.locationStructureTag);
        o.addProperty("locationAllowWaterSurface", d.locationAllowWaterSurface);
        o.addProperty("locationAllowOceanFloor", d.locationAllowOceanFloor);
        o.addProperty("locationMinYEnabled", d.locationMinYEnabled);
        o.addProperty("locationMinY", d.locationMinY);
        o.addProperty("locationMaxYEnabled", d.locationMaxYEnabled);
        o.addProperty("locationMaxY", d.locationMaxY);
        o.addProperty("locationCanSeeSky", d.locationCanSeeSky);
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

            // Spawn rules (Phase 4) — flat-field wire shape.
            d.spawnRulesEnabled = bool(o, "spawnRulesEnabled", false);
            d.weight = dbl(o, "weight", d.weight);
            d.timeOfDay = str(o, "timeOfDay", d.timeOfDay);
            d.spawnReasons.clear();
            if (o.has("spawnReasons") && o.get("spawnReasons").isJsonArray()) {
                for (var e : o.getAsJsonArray("spawnReasons")) d.spawnReasons.add(e.getAsString());
            }
            d.mutexGroup = str(o, "mutexGroup", "");
            d.replace = bool(o, "replace", false);
            d.yRangeEnabled = bool(o, "yRangeEnabled", false);
            d.yRangeMin = i(o, "yRangeMin", d.yRangeMin);
            d.yRangeMax = i(o, "yRangeMax", d.yRangeMax);
            d.lightRangeEnabled = bool(o, "lightRangeEnabled", false);
            d.lightRangeMin = i(o, "lightRangeMin", d.lightRangeMin);
            d.lightRangeMax = i(o, "lightRangeMax", d.lightRangeMax);
            d.locationDimension = str(o, "locationDimension", "");
            d.locationBiome = str(o, "locationBiome", "");
            d.locationBiomeTag = str(o, "locationBiomeTag", "");
            d.locationBiomes.clear();
            if (o.has("locationBiomes") && o.get("locationBiomes").isJsonArray()) {
                for (var e : o.getAsJsonArray("locationBiomes")) d.locationBiomes.add(e.getAsString());
            }
            d.locationStructure = str(o, "locationStructure", "");
            d.locationStructureTag = str(o, "locationStructureTag", "");
            d.locationAllowWaterSurface = bool(o, "locationAllowWaterSurface", false);
            d.locationAllowOceanFloor = bool(o, "locationAllowOceanFloor", false);
            d.locationMinYEnabled = bool(o, "locationMinYEnabled", false);
            d.locationMinY = i(o, "locationMinY", d.locationMinY);
            d.locationMaxYEnabled = bool(o, "locationMaxYEnabled", false);
            d.locationMaxY = i(o, "locationMaxY", d.locationMaxY);
            d.locationCanSeeSky = str(o, "locationCanSeeSky", "any");
            return d;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed MobOriginDraft JSON: " + e.getMessage(), e);
        }
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }
    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsBoolean() : def;
    }
    private static int i(JsonObject o, String k, int def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : def;
    }
    private static double dbl(JsonObject o, String k, double def) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }
}
