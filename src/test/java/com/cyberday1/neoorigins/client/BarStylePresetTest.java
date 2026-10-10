package com.cyberday1.neoorigins.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped bar styles: each built-in resource bar names a shipped style, each
 * style's textures exist at the sizes it declares, and a loop one texel short of
 * the texture is used exactly where the last column repeats the first.
 */
class BarStylePresetTest {

    private static final Set<String> STYLES =
        Set.of("fire", "water", "wind", "earth", "soul", "void", "sculk", "tinted");

    @Test
    void everyStyleShipsAndMatchesItsTextures() throws IOException {
        Path assets = resources().resolve("assets/neoorigins");
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(assets.resolve("bar_fx"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String name = p.getFileName().toString().replace(".json", "");
                found.add(name);
                JsonObject o = JsonParser.parseString(Files.readString(p)).getAsJsonObject();

                BufferedImage tex = texture(o.get("texture").getAsString());
                int tileW = o.get("tile_width").getAsInt();
                int tileH = o.get("tile_height").getAsInt();
                assertEquals(tileW, tex.getWidth(), name + " tile_width");
                assertEquals(tileH, tex.getHeight(), name + " tile_height");

                assertTrue(o.has("frame"), name + " has no frame");
                BufferedImage frame = texture(o.get("frame").getAsString());
                int fw = o.get("frame_width").getAsInt();
                int fh = o.get("frame_height").getAsInt();
                assertEquals(fw, frame.getWidth(), name + " frame_width");
                assertEquals(fh, frame.getHeight(), name + " frame_height");
                assertEquals(fh - 2, tileH, name + ": the fill must be the frame inset 1px");

                String mode = o.has("mode") ? o.get("mode").getAsString() : "scroll";
                if ("static".equals(mode)) {
                    assertEquals(fw - 2, tileW, name + ": a static fill must fill the frame window");
                    continue;
                }
                int loopW = o.has("loop_width") ? o.get("loop_width").getAsInt() : tileW;
                boolean edgeRepeats = sameColumn(tex, tileW - 1, 0);
                if (loopW == tileW - 1) {
                    assertTrue(edgeRepeats, name + " loops at " + loopW + " but its last column differs from its first");
                } else {
                    assertEquals(tileW, loopW, name + " loop_width");
                    assertFalse(edgeRepeats, name + " repeats its first column at the end: loop it at " + (tileW - 1));
                }
                assertTrue(loopW >= fw - 2, name + ": the loop is narrower than the fill window");
            }
        }
        assertEquals(new TreeSet<>(STYLES), found);
        // The creator's style picker offers this list; it must name exactly what ships.
        Set<String> offered = new TreeSet<>();
        for (String id : BarFxManager.BUILTIN_STYLES) offered.add(id.substring("neoorigins:".length()));
        assertEquals(found, offered, "BarFxManager.BUILTIN_STYLES");
    }

    @Test
    void builtinBarsNameShippedStyles() throws IOException {
        Path powers = resources().resolve("data/neoorigins/origins/powers");
        List<String> bad = new ArrayList<>();
        int bars = 0;
        try (Stream<Path> files = Files.list(powers)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                JsonElement el = JsonParser.parseString(Files.readString(p));
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("type") || !o.get("type").getAsString().endsWith(":resource")) continue;
                if (!o.has("hud_render")) continue;
                bars++;
                JsonObject hud = o.getAsJsonObject("hud_render");
                String id = hud.has("animated") ? hud.get("animated").getAsString() : "";
                boolean shipped = id.startsWith("neoorigins:")
                    && STYLES.contains(id.substring("neoorigins:".length()));
                // color is the flat fill a client without the style falls back to.
                if (!shipped || !hud.has("color")) bad.add(p.getFileName() + " -> '" + id + "'");
            }
        }
        assertTrue(bars > 0, "no built-in resource bars found");
        assertTrue(bad.isEmpty(), "built-in bars without a shipped style or a color: " + bad);
    }

    private static boolean sameColumn(BufferedImage img, int a, int b) {
        for (int y = 0; y < img.getHeight(); y++) {
            if (img.getRGB(a, y) != img.getRGB(b, y)) return false;
        }
        return true;
    }

    private static BufferedImage texture(String id) throws IOException {
        String[] parts = id.split(":", 2);
        Path p = resources().resolve("assets").resolve(parts[0]).resolve(parts[1]);
        assertTrue(Files.isRegularFile(p), "missing texture " + id);
        return ImageIO.read(p.toFile());
    }

    private static Path resources() {
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources");
            if (Files.isDirectory(candidate.resolve("assets/neoorigins/bar_fx"))) return candidate;
        }
        throw new IllegalStateException("could not locate src/main/resources");
    }
}
