package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.*;

public class LayerDataManager extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {

    public static final LayerDataManager INSTANCE = new LayerDataManager();
    // NeoOrigins format: data/<ns>/origins/origin_layers/<name>.json
    private static final FileToIdConverter FILE_CONVERTER = FileToIdConverter.json("origins/origin_layers");
    // Origins mod format: data/<ns>/origin_layers/<name>.json
    private static final FileToIdConverter COMPAT_CONVERTER = FileToIdConverter.json("origin_layers");

    private static final ResourceLocation ORIGINS_ORIGIN = ResourceLocation.fromNamespaceAndPath("origins", "origin");
    private static final ResourceLocation NEO_ORIGIN = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "origin");
    private static final ResourceLocation NEO_CLASS  = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, "class");

    /**
     * Built-in layer paths NeoOrigins recognises. Any foreign layer whose
     * path matches one of these auto-merges into the canonical destination
     * (see {@link #mergeForeignSamePathLayers}). Pack authors who genuinely
     * want a separate picker screen with one of these names can opt out by
     * setting {@code "standalone": true} on the layer JSON.
     */
    private static final Set<String> AUTO_MERGE_PATHS = Set.of("origin", "class");

    private Map<ResourceLocation, OriginLayer> layers = new HashMap<>();
    private List<OriginLayer> sortedLayers = new ArrayList<>();

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> map = new HashMap<>();
        // Native format first — these are authoritative
        scanConverterStacks(FILE_CONVERTER, resourceManager, map, false);
        // Compat format second — merges into existing layers
        scanConverterStacks(COMPAT_CONVERTER, resourceManager, map, true);
        // Fold origins:origin (Origins-mod compat packs) into our canonical
        // neoorigins:origin so all origins appear in one layer.
        mergeCompatOriginIntoNeo(map);
        // Fold any other <ns>:origin / <ns>:class layer into the canonical
        // destination, so a datapack that ships its own e.g. erin:origin layer
        // shows up in the main origin picker rather than as a third screen.
        // Authors who actually want a separate picker tab opt out via
        // "standalone": true on their layer JSON.
        mergeForeignSamePathLayers(map);
        return map;
    }

    /**
     * Scans all resource stacks for a converter, merging layer files that share the same ID.
     * Uses listMatchingResourceStacks() to read ALL packs, not just the top-priority one.
     */
    private void scanConverterStacks(FileToIdConverter converter, ResourceManager resourceManager,
                                     Map<ResourceLocation, JsonObject> map, boolean isCompat) {
        for (var entry : converter.listMatchingResourceStacks(resourceManager).entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation id = converter.fileToId(fileId);
            // Skip neoorigins namespace in compat converter — FILE_CONVERTER already handles it
            if (isCompat && NeoOrigins.MOD_ID.equals(id.getNamespace())) continue;

            List<Resource> resources = entry.getValue();
            for (Resource resource : resources) {
                try (Reader reader = resource.openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) continue;
                    JsonObject obj = parsed.getAsJsonObject();

                    if (map.containsKey(id)) {
                        mergeOrigins(map.get(id), obj);
                    } else {
                        map.put(id, obj);
                    }
                } catch (Exception e) {
                    NeoOrigins.LOGGER.error("Error reading layer file {} from pack", fileId, e);
                }
            }
        }
    }

    /**
     * Merges origins from {@code source} into {@code target}.
     * If source has "replace": true, it replaces target entirely.
     * Otherwise, appends source's origins array into target's.
     */
    private static void mergeOrigins(JsonObject target, JsonObject source) {
        if (source.has("replace") && source.get("replace").getAsBoolean()) {
            for (var e : source.entrySet()) {
                target.add(e.getKey(), e.getValue());
            }
            return;
        }
        // Additive merge — append origins, deduplicating by ID string
        JsonArray targetOrigins = target.has("origins") && target.get("origins").isJsonArray()
            ? target.getAsJsonArray("origins") : new JsonArray();
        Set<String> existing = new HashSet<>();
        for (JsonElement el : targetOrigins) {
            existing.add(originKey(el));
        }
        JsonArray sourceOrigins = source.has("origins") && source.get("origins").isJsonArray()
            ? source.getAsJsonArray("origins") : new JsonArray();
        for (JsonElement el : sourceOrigins) {
            if (existing.add(originKey(el))) {
                targetOrigins.add(el);
            }
        }
        target.add("origins", targetOrigins);
    }

    /** Returns a stable string key for an origin entry (plain string or object with "origin" field). */
    private static String originKey(JsonElement el) {
        if (el.isJsonPrimitive()) return el.getAsString();
        if (el.isJsonObject() && el.getAsJsonObject().has("origin"))
            return el.getAsJsonObject().get("origin").getAsString();
        return el.toString();
    }

    /**
     * Auto-merge pass for foreign layers sharing a built-in path. Any layer
     * whose path is in {@link #AUTO_MERGE_PATHS} but whose namespace is not
     * the canonical one folds into the canonical destination's origins list.
     * The source layer is dropped from the map so the picker doesn't show
     * a second screen for it. Pack authors set {@code "standalone": true}
     * on their layer JSON to opt out of this fold.
     *
     * <p>Runs after {@link #mergeCompatOriginIntoNeo}, so by this point any
     * {@code origins:origin} compat-pack layer has already been folded into
     * {@code neoorigins:origin} (the canonical destination for "origin").
     * The class layer stays at {@code neoorigins:class}.
     */
    private static void mergeForeignSamePathLayers(Map<ResourceLocation, JsonObject> map) {
        java.util.List<ResourceLocation> toRemove = new java.util.ArrayList<>();
        for (var entry : map.entrySet()) {
            ResourceLocation id = entry.getKey();
            // Don't try to fold a canonical destination into itself
            if (id.equals(NEO_ORIGIN) || id.equals(NEO_CLASS)) continue;
            if (!AUTO_MERGE_PATHS.contains(id.getPath())) continue;

            JsonObject sourceObj = entry.getValue();
            if (sourceObj.has("standalone") && sourceObj.get("standalone").getAsBoolean()) {
                NeoOrigins.LOGGER.debug("Layer {} opted out of auto-merge (standalone=true)", id);
                continue;
            }

            ResourceLocation dest = "origin".equals(id.getPath()) ? NEO_ORIGIN : NEO_CLASS;
            JsonObject destObj = map.get(dest);
            if (destObj == null) {
                // No canonical destination — leave the foreign layer as-is so
                // its origins still show somewhere rather than vanishing.
                NeoOrigins.LOGGER.debug("No canonical {} layer; leaving foreign layer {} standalone", dest, id);
                continue;
            }

            // A layer file from our own in-game-creator pack must never use
            // "replace": true to wipe the canonical origin/class list when it
            // folds in — creator output is only ever additive
            // (CustomPackSerializer never emits "replace"). This defends
            // against a hand-placed or hostile file dropped into
            // world/datapacks/neoorigins_custom. Scoped to that namespace so
            // every other pack's fold behavior is unchanged.
            if ("neoorigins_custom".equals(id.getNamespace()) // OriginDraft.CUSTOM_NAMESPACE
                    && sourceObj.has("replace")) {
                NeoOrigins.LOGGER.warn("Ignoring \"replace\" on creator-pack layer {} "
                    + "— folded layers from this pack are additive only", id);
                sourceObj.remove("replace");
            }
            int added = countOriginEntries(sourceObj);
            mergeOrigins(destObj, sourceObj);
            NeoOrigins.LOGGER.info("Folded foreign layer {} ({} origins) into {}", id, added, dest);
            toRemove.add(id);
        }
        for (ResourceLocation id : toRemove) map.remove(id);
    }

    private static int countOriginEntries(JsonObject obj) {
        return obj.has("origins") && obj.get("origins").isJsonArray()
            ? obj.getAsJsonArray("origins").size()
            : 0;
    }

    /**
     * Canonical origin layer is {@code neoorigins:origin}; {@code origins:origin}
     * is the legacy Origins-mod-format compat path. Fold any compat-pack
     * {@code origins:origin} layer into our canonical {@code neoorigins:origin}
     * so the merged content lives under one runtime layer id and the picker
     * shows a single tab. If only the compat layer exists (e.g. a pack-only
     * world with our own layer file missing), rename it to the canonical id
     * so commands and tab-complete still surface a {@code neoorigins:origin}.
     */
    private static void mergeCompatOriginIntoNeo(Map<ResourceLocation, JsonObject> map) {
        JsonObject compat = map.get(ORIGINS_ORIGIN);
        if (compat == null) return;

        if (map.containsKey(NEO_ORIGIN)) {
            mergeOrigins(map.get(NEO_ORIGIN), compat);
        } else {
            map.put(NEO_ORIGIN, compat);
        }
        map.remove(ORIGINS_ORIGIN);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> pObject, ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<ResourceLocation, OriginLayer> loaded = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonObject> entry : pObject.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                JsonObject obj = entry.getValue();
                obj.addProperty("id", id.toString());
                normalizeLayerJson(id, obj);
                OriginLayer.CODEC.parse(JsonOps.INSTANCE, obj)
                    .resultOrPartial(err -> NeoOrigins.LOGGER.warn("Failed to parse layer {}: {}", id, err))
                    .ifPresent(layer -> loaded.put(id, layer));
            } catch (Exception e) {
                NeoOrigins.LOGGER.error("Error loading layer {}", id, e);
            }
        }
        this.layers = Collections.unmodifiableMap(loaded);
        this.sortedLayers = loaded.values().stream()
            .filter(OriginLayer::enabled)
            .sorted(Comparator.comparingInt(OriginLayer::order))
            .toList();
        NeoOrigins.LOGGER.info("Loaded {} origin layers", loaded.size());
    }

    /**
     * Normalizes Origins-format layer JSON fields to NeoOrigins format in-place.
     */
    private static void normalizeLayerJson(ResourceLocation id, JsonObject obj) {
        if (!obj.has("name")) {
            obj.addProperty("name", id.getPath());
        } else if (obj.get("name").isJsonObject()) {
            JsonObject nameObj = obj.get("name").getAsJsonObject();
            String nameStr;
            if (nameObj.has("translate")) {
                nameStr = nameObj.get("translate").getAsString();
            } else if (nameObj.has("text")) {
                nameStr = nameObj.get("text").getAsString();
            } else {
                nameStr = id.getPath();
            }
            obj.addProperty("name", nameStr);
        }

        // Expand conditioned-batch format: entries with {"condition": {...}, "origins": [...]}
        // (plural array under a shared condition) into individual entries with singular "origin".
        // Also preserves conditions on individual entries for ConditionedOrigin.CODEC to parse.
        if (obj.has("origins") && obj.get("origins").isJsonArray()) {
            JsonArray origins = obj.getAsJsonArray("origins");
            JsonArray expanded = new JsonArray();
            for (JsonElement el : origins) {
                if (el.isJsonObject()) {
                    JsonObject entry = el.getAsJsonObject();
                    // Batch format: {"condition": {...}, "origins": ["id1", "id2", ...]}
                    if (entry.has("origins") && entry.get("origins").isJsonArray()) {
                        JsonElement condEl = entry.get("condition");
                        JsonArray batchOrigins = entry.getAsJsonArray("origins");
                        for (JsonElement originEl : batchOrigins) {
                            if (originEl.isJsonPrimitive()) {
                                JsonObject single = new JsonObject();
                                single.addProperty("origin", originEl.getAsString());
                                if (condEl != null && condEl.isJsonObject()) {
                                    single.add("condition", condEl.deepCopy());
                                }
                                expanded.add(single);
                            } else {
                                // Already an object — pass through
                                expanded.add(originEl);
                            }
                        }
                        continue;
                    }
                    // Single entry with "origin" (singular) — preserve as-is (condition intact)
                }
                expanded.add(el);
            }
            obj.add("origins", expanded);
        }
    }

    /** Replace the layer registry with data received from the server (client-side only). */
    public void setClientData(Map<ResourceLocation, OriginLayer> clientLayers, List<OriginLayer> clientSortedLayers) {
        this.layers = Collections.unmodifiableMap(new HashMap<>(clientLayers));
        this.sortedLayers = List.copyOf(clientSortedLayers);
    }

    public Map<ResourceLocation, OriginLayer> getLayers() { return layers; }
    public List<OriginLayer> getSortedLayers() { return sortedLayers; }
    public OriginLayer getLayer(ResourceLocation id) { return layers.get(id); }
    public boolean hasLayer(ResourceLocation id) { return layers.containsKey(id); }
}
