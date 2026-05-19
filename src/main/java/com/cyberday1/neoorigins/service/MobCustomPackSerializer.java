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
}
