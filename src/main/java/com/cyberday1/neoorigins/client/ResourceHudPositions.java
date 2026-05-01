package com.cyberday1.neoorigins.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.cyberday1.neoorigins.NeoOrigins;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side persistent storage for resource bar HUD positions.
 * Positions are stored as screen-percentage (0.0–1.0) so they
 * survive resolution changes. Saved to {@code config/neoorigins-hud.json}.
 */
public final class ResourceHudPositions {

    public record Pos(float xPct, float yPct) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FMLPaths.CONFIGDIR.get().resolve("neoorigins-hud.json");
    private static final Type MAP_TYPE = new TypeToken<Map<String, Pos>>() {}.getType();

    private static Map<String, Pos> positions = new HashMap<>();
    private static boolean loaded = false;

    public static Pos get(String resourceId) {
        ensureLoaded();
        return positions.get(resourceId);
    }

    public static void set(String resourceId, float xPct, float yPct) {
        ensureLoaded();
        positions.put(resourceId, new Pos(xPct, yPct));
    }

    public static void remove(String resourceId) {
        ensureLoaded();
        positions.remove(resourceId);
    }

    public static void save() {
        try (Writer w = Files.newBufferedWriter(CONFIG_PATH)) {
            GSON.toJson(positions, MAP_TYPE, w);
        } catch (IOException e) {
            NeoOrigins.LOGGER.warn("Failed to save HUD positions: {}", e.getMessage());
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(CONFIG_PATH)) return;
        try (Reader r = Files.newBufferedReader(CONFIG_PATH)) {
            Map<String, Pos> read = GSON.fromJson(r, MAP_TYPE);
            if (read != null) positions = new HashMap<>(read);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("Failed to load HUD positions: {}", e.getMessage());
        }
    }

    private ResourceHudPositions() {}
}
