package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

/**
 * Scans all packs in originpacks/ during RegisterEvent and auto-registers a simple
 * Item for any assets/<ns>/models/item/<name>.json whose item ID is not already in
 * the registry. This makes custom items from Fabric-sourced origin packs (e.g.
 * origins-plus-plus:petrified_heart) exist as real items on NeoForge so they can
 * be held, dropped, used in recipes, and referenced in power configs.
 *
 * Registration happens on the mod event bus before the registry freezes.
 */
public final class PackItemAutoRegistrar {

    private PackItemAutoRegistrar() {}

    public static void onRegisterItems(RegisterEvent event) {
        // Only act when the ITEM registry fires
        if (!event.getRegistryKey().equals(Registries.ITEM)) return;

        Path originpacks = FMLPaths.GAMEDIR.get().resolve("originpacks");
        if (!Files.exists(originpacks)) return;

        int[] count = {0};

        try (DirectoryStream<Path> entries = Files.newDirectoryStream(originpacks)) {
            for (Path entry : entries) {
                try {
                    String fname = entry.getFileName().toString().toLowerCase();
                    if (Files.isRegularFile(entry) && (fname.endsWith(".jar") || fname.endsWith(".zip"))) {
                        scanZip(entry, event, count);
                    } else if (Files.isDirectory(entry)) {
                        scanDirectory(entry, event, count);
                    }
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("PackItemAutoRegistrar: error scanning '{}': {}",
                        entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            NeoOrigins.LOGGER.warn("PackItemAutoRegistrar: error opening originpacks/: {}", e.getMessage());
        }

        if (count[0] > 0)
            NeoOrigins.LOGGER.info("PackItemAutoRegistrar: registered {} item(s) from originpacks/", count[0]);
    }

    // ── ZIP / JAR ──────────────────────────────────────────────────────────────

    private static void scanZip(Path zipPath, RegisterEvent event, int[] count) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            zip.entries().asIterator().forEachRemaining(entry -> {
                Identifier id = itemIdFromModelPath(entry.getName());
                if (id != null) tryRegister(id, event, count);
            });
        }
    }

    // ── Directory ──────────────────────────────────────────────────────────────

    private static void scanDirectory(Path packDir, RegisterEvent event, int[] count) throws IOException {
        Path assets = packDir.resolve("assets");
        if (!Files.isDirectory(assets)) return;

        try (DirectoryStream<Path> nsDirs = Files.newDirectoryStream(assets)) {
            for (Path nsDir : nsDirs) {
                if (!Files.isDirectory(nsDir)) continue;
                String ns = nsDir.getFileName().toString();
                if (shouldSkipNamespace(ns)) continue;

                Path modelsItem = nsDir.resolve("models").resolve("item");
                if (!Files.isDirectory(modelsItem)) continue;

                try (DirectoryStream<Path> models = Files.newDirectoryStream(modelsItem, "*.json")) {
                    for (Path model : models) {
                        String name = model.getFileName().toString();
                        name = name.substring(0, name.length() - 5); // strip .json
                        tryRegister(Identifier.fromNamespaceAndPath(ns, name), event, count);
                    }
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Parses "assets/<ns>/models/item/<name>.json" → Identifier(<ns>, <name>).
     * Returns null for paths that don't match or should be skipped.
     */
    private static Identifier itemIdFromModelPath(String path) {
        // Expected: assets/<ns>/models/item/<name>.json  (exactly 5 segments)
        if (!path.startsWith("assets/") || !path.contains("/models/item/") || !path.endsWith(".json"))
            return null;
        String[] parts = path.split("/");
        if (parts.length != 5) return null; // no sub-directories under models/item/
        String ns   = parts[1];
        String name = parts[4].substring(0, parts[4].length() - 5); // strip .json
        if (shouldSkipNamespace(ns)) return null;
        return Identifier.fromNamespaceAndPath(ns, name);
    }

    /** Namespaces whose items are already handled and must not be re-registered. */
    private static boolean shouldSkipNamespace(String ns) {
        return "minecraft".equals(ns) || "neoorigins".equals(ns);
    }

    private static void tryRegister(Identifier id, RegisterEvent event, int[] count) {
        if (BuiltInRegistries.ITEM.containsKey(id)) return;
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        event.register(Registries.ITEM, id, () -> new Item(new Item.Properties().setId(key)));
        count[0]++;
        NeoOrigins.LOGGER.debug("PackItemAutoRegistrar: registered {}", id);
    }
}
