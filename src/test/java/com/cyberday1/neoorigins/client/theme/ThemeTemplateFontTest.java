package com.cyberday1.neoorigins.client.theme;

import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * On 1.21.1 two fonts sharing one ttf provider load glyphs on one FreeType face from two
 * threads, which crashes the client. The theme template must never ship a reference to one.
 */
// hub: neoorigins/theme-template-boot.md
class ThemeTemplateFontTest {

    @Test
    void templateFontsNeverReferenceATtfFont() throws IOException {
        Set<String> ttfFonts = new HashSet<>();
        List<Path> fontFiles = new ArrayList<>();
        for (Path root : List.of(RealDatapack.projectPath("src/main/resources/assets"),
                                 RealDatapack.projectPath("docs/theme-template/assets"))) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(p -> p.getParent().getFileName().toString().equals("font")
                        && p.toString().endsWith(".json"))
                    .forEach(p -> {
                        String ns = root.relativize(p).getName(0).toString();
                        String id = ns + ":" + p.getFileName().toString().replace(".json", "");
                        if (providers(p).stream().anyMatch(o -> "ttf".equals(type(o)))) ttfFonts.add(id);
                        if (p.startsWith(RealDatapack.projectPath("docs/theme-template"))) fontFiles.add(p);
                    });
            }
        }
        assertEquals(true, ttfFonts.contains("neoorigins:parchment"), "the scan must see the bundled ttf font");

        List<String> bad = new ArrayList<>();
        for (Path p : fontFiles) {
            for (JsonObject o : providers(p)) {
                if ("reference".equals(type(o)) && ttfFonts.contains(o.get("id").getAsString())) {
                    bad.add(p.getFileName() + " -> " + o.get("id").getAsString());
                }
            }
        }
        assertEquals(List.of(), bad);
    }

    private static String type(JsonObject o) {
        return o.has("type") ? o.get("type").getAsString() : "";
    }

    private static List<JsonObject> providers(Path p) {
        try {
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            List<JsonObject> out = new ArrayList<>();
            if (root.has("providers")) {
                for (JsonElement e : root.getAsJsonArray("providers")) out.add(e.getAsJsonObject());
            }
            return out;
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
