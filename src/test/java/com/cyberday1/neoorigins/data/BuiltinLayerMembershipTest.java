package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The picker lists a layer's own {@code origins} array and nothing else, and the
 * server refuses a choice outside it. The Dragon Survival origins shipped in no
 * layer, so they could never be picked. Layers are decoded by a fresh
 * {@link LayerDataManager} so the shared instance other tests read stays empty.
 */
class BuiltinLayerMembershipTest {

    private static final String DATA_ROOT = "data/neoorigins/origins";
    private static final List<String> DRAGONS = List.of("cave_dragon", "forest_dragon", "sea_dragon");

    private static LayerDataManager layers;

    @BeforeAll
    static void load() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Map<Identifier, JsonObject> json = new HashMap<>();
        read("origin_layers").forEach((id, obj) -> json.put(id, obj));
        layers = new LayerDataManager();
        layers.apply(json, null, InactiveProfiler.INSTANCE);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("neoorigins", path);
    }

    @Test
    void theDragonsAreOfferedByTheOriginLayer() {
        OriginLayer origin = layers.getLayer(id("origin"));
        List<Identifier> offered = origin.getAvailableOriginIds(Map.of());
        for (String dragon : DRAGONS) {
            assertTrue(offered.contains(id(dragon)), dragon + " is not offered by neoorigins:origin");
        }
    }

    /** The general form, so the next origin shipped without a layer entry fails here. */
    @Test
    void everyShippedOriginIsInABuiltInLayer() {
        List<Identifier> inALayer = new ArrayList<>();
        layers.getLayers().values().forEach(l -> inALayer.addAll(l.getAvailableOriginIds()));
        List<Identifier> loose = read("origins").keySet().stream()
            .filter(o -> !inALayer.contains(o)).sorted().toList();
        assertEquals(List.of(), loose, "shipped origins no built-in layer lists");
    }

    /** Without Dragon Survival the gate keeps them unloaded, which the picker filters out. */
    @Test
    void withoutDragonSurvivalTheyDoNotLoad() {
        RealDatapack.load();
        assertTrue(OriginDataManager.INSTANCE.hasOrigin(id("human")), "the real datapack did not load");
        for (String dragon : DRAGONS) {
            assertFalse(OriginDataManager.INSTANCE.hasOrigin(id(dragon)), dragon + " loaded without its mod");
        }
    }

    private static Map<Identifier, JsonObject> read(String kind) {
        Path dir = locate(kind);
        Map<Identifier, JsonObject> out = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString();
                try {
                    out.put(id(name.substring(0, name.length() - ".json".length())),
                        JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertFalse(out.isEmpty(), "no json under " + dir);
        return out;
    }

    private static Path locate(String kind) {
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources").resolve(DATA_ROOT).resolve(kind);
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate " + DATA_ROOT + "/" + kind);
    }
}
