package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vanilla flickers Night Vision once its remaining duration is 200 ticks or less
 * ({@code GameRenderer#getNightVisionScale}). A power that re-applies it every
 * {@code interval} ticks must therefore refresh it while more than 200 ticks remain,
 * or the screen pulses: Murky Sight's 220-tick effect on a 40-tick refresh fell to
 * 180 and flickered for half of every cycle.
 */
class PeriodicNightVisionTest {

    private static final int FLICKER_TICKS = 200;

    @Test
    void periodicNightVisionNeverDropsIntoTheFlicker() throws IOException {
        Path dir = RealDatapack.projectPath("src/main/resources/data/neoorigins/origins/powers");
        List<String> problems = new ArrayList<>();
        int checked = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject json = read(file);
                if (!json.has("type") || !"neoorigins:condition_passive".equals(json.get("type").getAsString())) continue;
                int interval = json.has("interval") ? json.get("interval").getAsInt() : 20;
                List<JsonObject> effects = new ArrayList<>();
                collectNightVision(json.get("entity_action"), effects);
                for (JsonObject effect : effects) {
                    checked++;
                    int duration = effect.get("duration").getAsInt();
                    if (duration - interval <= FLICKER_TICKS) {
                        problems.add(file.getFileName() + ": duration " + duration + " refreshed every "
                            + interval + " falls to " + (duration - interval));
                    }
                }
            }
        }
        assertTrue(checked > 0, "no periodic night vision found; the scan is broken");
        assertTrue(problems.isEmpty(), "night vision that flickers between refreshes:\n  "
            + String.join("\n  ", problems));
    }

    private static void collectNightVision(JsonElement el, List<JsonObject> out) {
        if (el == null) return;
        if (el.isJsonArray()) {
            for (JsonElement c : el.getAsJsonArray()) collectNightVision(c, out);
        } else if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("effect") && "minecraft:night_vision".equals(o.get("effect").getAsString())
                    && o.has("duration")) {
                out.add(o);
            }
            for (var e : o.entrySet()) collectNightVision(e.getValue(), out);
        }
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}
