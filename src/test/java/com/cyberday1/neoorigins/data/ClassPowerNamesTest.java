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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two powers in one class must not share a display name: Warrior listed both its
 * knockback resistance and its armor bonus as "Battle Stance", and classic Rogue
 * showed its Stealth power beside a description card also called "Stealth".
 * Display-only cards count: the picker lists them like any other power.
 */
class ClassPowerNamesTest {

    private static final String DATA = "src/main/resources/data/neoorigins/origins/";

    @Test
    void noClassShowsTwoPowersUnderOneName() throws IOException {
        JsonObject lang = read(RealDatapack.projectPath(
            "src/main/resources/assets/neoorigins/lang/en_us.json"));
        List<String> clashes = new ArrayList<>();
        for (String dir : List.of("origins", "classic_origins")) {
            try (Stream<Path> files = Files.list(RealDatapack.projectPath(DATA + dir))) {
                for (Path file : files.filter(p -> p.getFileName().toString().startsWith("class_")).toList()) {
                    Map<String, String> byName = new HashMap<>();
                    for (JsonElement el : read(file).getAsJsonArray("powers")) {
                        String id = el.getAsString();
                        String name = displayName(id, lang);
                        if (name == null) continue; // hidden glue powers carry no name
                        String other = byName.putIfAbsent(name, id);
                        if (other != null) clashes.add(dir + "/" + file.getFileName() + ": \"" + name
                            + "\" is both " + other + " and " + id);
                    }
                }
            }
        }
        assertTrue(clashes.isEmpty(), "duplicate power names in a class:\n  " + String.join("\n  ", clashes));
    }

    private static String displayName(String powerId, JsonObject lang) throws IOException {
        String path = powerId.substring(powerId.indexOf(':') + 1);
        String key = "power.neoorigins." + path + ".name";
        Path file = RealDatapack.projectPath(DATA + "powers/" + path + ".json");
        JsonObject power = read(file);
        if (power.has("name") && power.get("name").isJsonPrimitive()) key = power.get("name").getAsString();
        return lang.has(key) ? lang.get(key).getAsString() : null;
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }
}
