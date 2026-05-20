package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * On-disk JSON shape for a mob origin (mirrors {@link CustomPackSerializer}
 * but for {@code MobOrigin.CODEC}: a {@code target} block instead of a layer,
 * no layer patch). {@code id} is omitted — {@code MobOriginDataManager}
 * injects it from the file path. Headless; round-trip-tested.
 */
public final class MobCustomPackSerializer {

    private MobCustomPackSerializer() {}

    public static JsonObject mobOriginJson(MobOriginDraft d) {
        JsonObject o = new JsonObject();
        o.add("name", text(d.name));
        o.add("description", text(d.description));
        o.addProperty("icon", d.icon.toString());
        o.add("target", targetJson(d));
        JsonArray powers = new JsonArray();
        for (OriginDraft.PowerDraft p : d.powers) {
            if (p.powerId != null) powers.add(p.powerId.toString());
        }
        o.add("powers", powers);
        if (d.spawnRulesEnabled) o.add("spawn_rules", spawnRulesJson(d));
        return o;
    }

    /** Reuses the player serializer's power body shaping (same PowerDraft). */
    public static JsonObject powerJson(OriginDraft.PowerDraft p) {
        return CustomPackSerializer.powerJson(p);
    }

    private static JsonObject targetJson(MobOriginDraft d) {
        JsonObject t = new JsonObject();
        if (!d.targetEntityTypes.isEmpty()) {
            JsonArray arr = new JsonArray();
            d.targetEntityTypes.forEach(arr::add);
            t.add("entity_types", arr);
        } else if (d.targetEntityTag != null && !d.targetEntityTag.isBlank()) {
            t.addProperty("entity_tag", d.targetEntityTag.trim());
        } else if (d.targetEntityType != null && !d.targetEntityType.isBlank()) {
            t.addProperty("entity_type", d.targetEntityType.trim());
        }
        return t;
    }

    private static JsonObject text(String s) {
        JsonObject o = new JsonObject();
        o.addProperty("text", s == null ? "" : s);
        return o;
    }

    private static JsonObject spawnRulesJson(MobOriginDraft d) {
        JsonObject s = new JsonObject();
        s.addProperty("weight", d.weight);
        if (!"any".equals(d.timeOfDay)) s.addProperty("time_of_day", d.timeOfDay);
        if (!d.spawnReasons.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String r : d.spawnReasons) arr.add(r);
            s.add("spawn_reasons", arr);
        }
        if (d.mutexGroup != null && !d.mutexGroup.isBlank()) {
            s.addProperty("mutex_group", d.mutexGroup.trim());
        }
        if (d.replace) s.addProperty("replace", true);
        if (d.yRangeEnabled) {
            JsonObject r = new JsonObject();
            r.addProperty("min", d.yRangeMin);
            r.addProperty("max", d.yRangeMax);
            s.add("y_range", r);
        }
        if (d.lightRangeEnabled) {
            JsonObject r = new JsonObject();
            r.addProperty("min", d.lightRangeMin);
            r.addProperty("max", d.lightRangeMax);
            s.add("light_range", r);
        }
        JsonObject loc = locationJson(d);
        if (loc != null) s.add("location", loc);
        return s;
    }

    /** Returns null when no location sub-field is set (so the codec defaults
     *  to {@code Optional.empty()} rather than receiving an empty object). */
    private static JsonObject locationJson(MobOriginDraft d) {
        JsonObject l = new JsonObject();
        boolean any = false;
        if (!d.locationDimension.isBlank())    { l.addProperty("dimension", d.locationDimension.trim()); any = true; }
        if (!d.locationBiome.isBlank())        { l.addProperty("biome", d.locationBiome.trim()); any = true; }
        if (!d.locationBiomeTag.isBlank())     { l.addProperty("biome_tag", d.locationBiomeTag.trim()); any = true; }
        if (!d.locationBiomes.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String b : d.locationBiomes) if (b != null && !b.isBlank()) arr.add(b.trim());
            if (arr.size() > 0) { l.add("biomes", arr); any = true; }
        }
        if (!d.locationStructure.isBlank())    { l.addProperty("structure", d.locationStructure.trim()); any = true; }
        if (!d.locationStructureTag.isBlank()) { l.addProperty("structure_tag", d.locationStructureTag.trim()); any = true; }
        if (d.locationAllowWaterSurface)       { l.addProperty("allow_water_surface", true); any = true; }
        if (d.locationAllowOceanFloor)         { l.addProperty("allow_ocean_floor", true); any = true; }
        if (d.locationMinYEnabled)             { l.addProperty("min_y", d.locationMinY); any = true; }
        if (d.locationMaxYEnabled)             { l.addProperty("max_y", d.locationMaxY); any = true; }
        if (!"any".equals(d.locationCanSeeSky)) {
            l.addProperty("can_see_sky", "true".equals(d.locationCanSeeSky));
            any = true;
        }
        return any ? l : null;
    }
}
