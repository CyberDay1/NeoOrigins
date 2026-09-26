package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One unparseable origin id in a layer costs that entry, not the layer. A web
 * export with a path of {@code Wizard} used to empty the whole origin picker.
 */
class LayerInvalidMemberTest {

    private static final List<String> BAD = List.of(
        "neoorigins:Wizard", "neoorigins:wizard ", "neoorigins:naïve");

    private static Map<ResourceLocation, JsonObject> shipped;

    @BeforeAll
    static void boot() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        shipped = new HashMap<>();
        Path dir = RealDatapack.projectPath("src/main/resources/data/neoorigins/origins/origin_layers");
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = p.getFileName().toString().replace(".json", "");
                shipped.put(ResourceLocation.fromNamespaceAndPath("neoorigins", name),
                    JsonParser.parseString(Files.readString(p)).getAsJsonObject());
            }
        }
    }

    private static LayerDataManager load(Map<ResourceLocation, JsonObject> json) {
        Map<ResourceLocation, JsonObject> copy = new HashMap<>();
        json.forEach((k, v) -> copy.put(k, v.deepCopy()));
        LayerDataManager m = new LayerDataManager();
        m.apply(copy, null, InactiveProfiler.INSTANCE);
        return m;
    }

    @Test
    void aBadEntryFromAnAddonCostsOnlyThatEntry() {
        for (String layerPath : List.of("origin", "class")) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath("neoorigins", layerPath);
            List<ResourceLocation> before = load(shipped).getLayer(id).getAvailableOriginIds();
            assertTrue(before.size() > 10, layerPath + " baseline too small: " + before.size());

            Map<ResourceLocation, JsonObject> withAddon = new HashMap<>(shipped);
            JsonObject merged = shipped.get(id).deepCopy();
            for (String bad : BAD) {
                // the web exporter's layer-extension shape, merged as prepare() merges it
                LayerDataManager.mergeOrigins(merged, JsonParser.parseString(
                    "{\"replace\":false,\"origins\":[\"" + bad + "\"]}").getAsJsonObject());
            }
            LayerDataManager.mergeOrigins(merged, JsonParser.parseString(
                "{\"origins\":[{\"origin\":\"Bad Id\",\"condition\":{}},\"neoorigins:fine_addon\"]}")
                .getAsJsonObject());
            withAddon.put(id, merged);

            OriginLayer layer = load(withAddon).getLayer(id);
            assertNotNull(layer, "layer " + id + " was dropped");
            List<ResourceLocation> after = layer.getAvailableOriginIds();
            assertTrue(after.containsAll(before), "lost shipped members of " + id);
            assertTrue(after.contains(ResourceLocation.fromNamespaceAndPath("neoorigins", "fine_addon")));
            assertEquals(before.size() + 1, after.size(), "only the valid addon entry should join");
        }
    }

    @Test
    void theShippedLayersStillLoadUnchanged() {
        LayerDataManager m = load(shipped);
        assertEquals(shipped.size(), m.getLayers().size());
        for (ResourceLocation id : shipped.keySet()) {
            assertFalse(m.getLayer(id).getAvailableOriginIds().isEmpty(), id + " empty");
        }
    }
}
