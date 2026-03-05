package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Scans the originpacks/ folder and registers each entry as a server data pack.
 * Handles: .jar files, .zip files, and plain folders — with or without pack.mcmeta.
 * Vanilla's FolderRepositorySource rejects .jar files and folders missing pack.mcmeta,
 * so we synthesize Pack.Metadata for packs that don't include one.
 */
public class OriginsPackFinder implements RepositorySource {

    private final Path folder;

    public OriginsPackFinder(Path folder) {
        this.folder = folder;
    }

    @Override
    public void loadPacks(Consumer<Pack> onLoad) {
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("Failed to create originpacks/ folder at {}", folder, e);
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path entry : stream) {
                try {
                    Pack pack = tryLoadEntry(entry);
                    if (pack != null) {
                        onLoad.accept(pack);
                        NeoOrigins.LOGGER.info("OriginsCompat: loaded pack '{}'", entry.getFileName());
                    } else {
                        NeoOrigins.LOGGER.debug("OriginsCompat: skipping non-pack entry '{}'", entry.getFileName());
                    }
                } catch (Exception e) {
                    NeoOrigins.LOGGER.warn("OriginsCompat: failed to load pack '{}': {}", entry.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            NeoOrigins.LOGGER.error("Failed to scan originpacks/ folder at {}", folder, e);
        }
    }

    private Pack tryLoadEntry(Path path) {
        String name = path.getFileName().toString();
        Pack.ResourcesSupplier supplier = null;

        if (Files.isRegularFile(path)) {
            if (name.endsWith(".zip") || name.endsWith(".jar")) {
                supplier = new FilePackResources.FileResourcesSupplier(path);
            }
        } else if (Files.isDirectory(path)) {
            supplier = new PathPackResources.PathResourcesSupplier(path);
        }

        if (supplier == null) return null;

        PackLocationInfo info = new PackLocationInfo(
            "originpacks/" + name,
            Component.literal(name),
            PackSource.DEFAULT,
            Optional.empty()
        );
        PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, false);

        // Try standard metadata reading first (pack has a pack.mcmeta)
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA, selectionConfig);
        if (pack != null) return pack;

        // No pack.mcmeta — Origins mod JARs don't include one.
        // Synthesize compatible metadata and construct the Pack directly.
        Pack.Metadata synthetic = new Pack.Metadata(
            Component.literal(name),
            PackCompatibility.COMPATIBLE,
            FeatureFlagSet.of(),
            List.of(),
            false
        );
        return new Pack(info, supplier, synthetic, selectionConfig);
    }
}
