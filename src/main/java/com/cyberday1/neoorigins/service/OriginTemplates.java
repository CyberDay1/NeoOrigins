package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.api.origin.ConditionedOrigin;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.screen.creator.model.OriginTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-side snapshot of every currently-loaded origin in a shape the in-game
 * creator can hand to {@link OriginTemplateLoader} to fill a draft. Powers the
 * "Load template" picker in {@code OriginCreatorScreen}.
 *
 * <p>Each entry pairs the raw origin JSON (post-translation, as the data
 * manager parsed it) with the raw bodies of every power it references — so
 * cloning a template into a draft is byte-identical to what the source pack
 * shipped, not a re-serialized round-trip.
 *
 * <p>Origins whose power bodies aren't on disk (Route-B compat injections,
 * registry-synced powers without source JSON) are still listed but with a
 * stub {@code {}} body so the user sees them in the picker and can hand-edit
 * if they really want to template a synthetic power. Build-time decision: a
 * "complete only" filter would silently hide most Origins-mod compat origins
 * from the picker and confuse authors who can see them in-world.
 */
public final class OriginTemplates {

    private static final Gson GSON = new Gson();

    private OriginTemplates() {}

    /** Build a template list from the currently-loaded data managers.
     *  Sorted by origin id for deterministic picker order. */
    public static List<OriginTemplate> collect() {
        Map<ResourceLocation, Origin> origins = OriginDataManager.INSTANCE.getOrigins();
        if (origins.isEmpty()) return List.of();

        Map<ResourceLocation, ResourceLocation> originToLayer = buildOriginLayerIndex();
        List<OriginTemplate> out = new ArrayList<>(origins.size());

        for (Origin origin : origins.values()) {
            ResourceLocation originId = origin.id();
            JsonObject body = OriginDataManager.INSTANCE.getRawOriginJson(originId);
            if (body == null) continue; // client-synced origin without source JSON

            ResourceLocation layerId = originToLayer.getOrDefault(originId,
                ResourceLocation.fromNamespaceAndPath("neoorigins", "origin"));

            LinkedHashMap<ResourceLocation, String> powerBodies = new LinkedHashMap<>();
            for (ResourceLocation powerId : origin.powers()) {
                JsonObject powerBody = PowerDataManager.INSTANCE.getRawPowerJson(powerId);
                powerBodies.put(powerId, powerBody != null ? GSON.toJson(powerBody) : "{}");
            }

            ResourceLocation icon = origin.icon().isEmpty()
                ? ResourceLocation.withDefaultNamespace("player_head")
                : BuiltInRegistries.ITEM.getKey(origin.icon().getItem());

            out.add(new OriginTemplate(
                originId, layerId,
                origin.name().getString(),
                icon != null ? icon : ResourceLocation.withDefaultNamespace("player_head"),
                origin.impact() != null ? origin.impact().ordinal() : 0,
                GSON.toJson(body),
                powerBodies));
        }

        out.sort((a, b) -> a.originId().toString().compareTo(b.originId().toString()));
        return out;
    }

    private static Map<ResourceLocation, ResourceLocation> buildOriginLayerIndex() {
        Map<ResourceLocation, ResourceLocation> index = new java.util.HashMap<>();
        for (OriginLayer layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            for (ConditionedOrigin co : layer.origins()) {
                // First layer wins — picker only uses this for the
                // Origins-vs-Classes split, and an origin appearing in
                // multiple layers is already a pack-author edge case.
                index.putIfAbsent(co.origin(), layer.id());
            }
        }
        return index;
    }

    /** Serialize a template list to a single JSON string for transport. */
    public static String toJson(List<OriginTemplate> templates) {
        JsonArray arr = new JsonArray();
        for (OriginTemplate t : templates) {
            JsonObject o = new JsonObject();
            o.addProperty("originId", t.originId().toString());
            o.addProperty("layerId", t.layerId().toString());
            o.addProperty("displayName", t.displayName());
            o.addProperty("icon", t.icon().toString());
            o.addProperty("impact", t.impact());
            o.addProperty("originBody", t.originBody());
            JsonArray powers = new JsonArray();
            for (var e : t.powers().entrySet()) {
                JsonObject p = new JsonObject();
                p.addProperty("id", e.getKey().toString());
                p.addProperty("body", e.getValue());
                powers.add(p);
            }
            o.add("powers", powers);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("items", arr);
        return GSON.toJson(root);
    }

    /** Inverse of {@link #toJson}. Malformed entries are dropped silently —
     *  the picker still works with whatever survives. */
    public static List<OriginTemplate> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("items") || !root.get("items").isJsonArray()) return List.of();
            List<OriginTemplate> out = new ArrayList<>();
            for (var el : root.getAsJsonArray("items")) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                try {
                    ResourceLocation originId = ResourceLocation.parse(o.get("originId").getAsString());
                    ResourceLocation layerId = ResourceLocation.parse(o.get("layerId").getAsString());
                    String displayName = o.has("displayName") ? o.get("displayName").getAsString() : originId.getPath();
                    ResourceLocation icon = ResourceLocation.parse(o.get("icon").getAsString());
                    int impact = o.has("impact") ? o.get("impact").getAsInt() : 0;
                    String originBody = o.get("originBody").getAsString();
                    LinkedHashMap<ResourceLocation, String> powers = new LinkedHashMap<>();
                    if (o.has("powers") && o.get("powers").isJsonArray()) {
                        for (var pe : o.getAsJsonArray("powers")) {
                            JsonObject p = pe.getAsJsonObject();
                            powers.put(
                                ResourceLocation.parse(p.get("id").getAsString()),
                                p.get("body").getAsString());
                        }
                    }
                    out.add(new OriginTemplate(originId, layerId, displayName, icon,
                        impact, originBody, powers));
                } catch (RuntimeException ignored) { /* drop one bad entry */ }
            }
            return out;
        } catch (RuntimeException e) {
            return List.of();
        }
    }
}
