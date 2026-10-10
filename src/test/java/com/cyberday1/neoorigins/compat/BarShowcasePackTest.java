package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ResourcePower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code datapacks/bar_showcase}, the test origin that shows every bar style: it loads
 * through the real managers, every power is a resource bar, and between them they use
 * every style shipped under {@code assets/neoorigins/bar_fx}.
 */
class BarShowcasePackTest {

    private static final Path PACK = RealDatapack.projectPath("datapacks/bar_showcase");
    private static final Path POWERS = PACK.resolve("data/bar_showcase/origins/powers");

    @BeforeAll
    static void load() {
        RealDatapack.loadWith(PACK);
    }

    @AfterAll
    static void restore() {
        RealDatapack.restoreShipped();
    }

    @Test
    void theOriginLoadsWithEveryPower() throws IOException {
        Origin origin = OriginDataManager.INSTANCE.getOrigin(ResourceLocation.fromNamespaceAndPath("bar_showcase", "bar_showcase"));
        assertNotNull(origin, "bar_showcase:bar_showcase did not load");
        assertEquals(powerFiles().size(), origin.powers().size());
        List<ResourceLocation> dangling = origin.powers().stream()
            .filter(id -> PowerDataManager.INSTANCE.getPower(id) == null).toList();
        assertEquals(List.of(), dangling, "powers the origin lists but nothing loaded");
    }

    @Test
    void everyShippedStyleIsShown() throws IOException {
        Set<String> used = new TreeSet<>();
        for (Path file : powerFiles()) {
            String stem = file.getFileName().toString().replace(".json", "");
            var holder = PowerDataManager.INSTANCE.getPower(ResourceLocation.fromNamespaceAndPath("bar_showcase", stem));
            assertNotNull(holder, stem);
            assertTrue(holder.config() instanceof ResourcePower.Config, stem + " is not a resource bar");
            used.add(((ResourcePower.Config) holder.config()).animated());
        }
        Set<String> shipped = new TreeSet<>();
        try (Stream<Path> s = Files.list(RealDatapack.projectPath("src/main/resources/assets/neoorigins/bar_fx"))) {
            s.forEach(p -> shipped.add("neoorigins:" + p.getFileName().toString().replace(".json", "")));
        }
        assertEquals(shipped, used);
    }

    @Test
    void theLayerOffersTheOrigin() throws IOException {
        var layer = JsonParser.parseString(Files.readString(PACK.resolve("data/neoorigins/origins/origin_layers/origin.json")))
            .getAsJsonObject();
        assertEquals("bar_showcase:bar_showcase", layer.getAsJsonArray("origins").get(0).getAsString());
    }

    private static List<Path> powerFiles() throws IOException {
        try (Stream<Path> s = Files.list(POWERS)) {
            return new ArrayList<>(s.filter(p -> p.toString().endsWith(".json")).sorted().toList());
        }
    }
}
