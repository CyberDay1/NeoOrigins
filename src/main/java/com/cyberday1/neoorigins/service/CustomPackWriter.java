package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.screen.creator.model.OriginDraft;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes the 2.1 creator's draft to a real on-disk world datapack at
 * {@code <world>/datapacks/neoorigins_custom/}, the vanilla world-datapack
 * folder (auto-discovered on reload — no pack finder needed).
 *
 * <p>Server-authoritative side only; the open/save gate + the Apply trigger
 * live in later phases. JSON shape is delegated to {@link CustomPackSerializer}
 * (headless-tested). Writes are atomic (temp + {@code ATOMIC_MOVE}) so a
 * concurrent reload never reads a half-written file. {@code pack.mcmeta}'s
 * {@code pack_format} is read from the mod's own packaged {@code /pack.mcmeta}
 * so it tracks the MC version with zero drift.
 */
public final class CustomPackWriter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int FALLBACK_PACK_FORMAT = 84;

    private CustomPackWriter() {}

    /** Outcome of a write; {@code paths} are pack-relative for logging. */
    public record WriteResult(boolean ok, List<String> paths, String error) {
        public static WriteResult fail(String msg) { return new WriteResult(false, List.of(), msg); }
    }

    public static Path packDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR)
            .resolve(OriginDraft.CUSTOM_NAMESPACE);
    }

    public static WriteResult write(MinecraftServer server, OriginDraft draft) {
        Path root = packDir(server);
        List<String> written = new ArrayList<>();
        try {
            Files.createDirectories(root);
            writePackMeta(root, written);

            // Origin body — no id, no type (OriginDataManager injects id from path).
            ResourceLocation originId = draft.originId();
            Path originFile = dataFile(root, originId.getNamespace(),
                "origins/origins", originId.getPath());
            atomicWriteJson(originFile, CustomPackSerializer.originJson(draft));
            written.add(rel(root, originFile));

            // Powers — each carries its type. Namespace is pinned to our own
            // (never trust a client-supplied powerId namespace); the path is
            // still containment-checked in dataFile().
            for (OriginDraft.PowerDraft p : draft.powers) {
                Path pf = dataFile(root, OriginDraft.CUSTOM_NAMESPACE,
                    "origins/powers", p.powerId.getPath());
                atomicWriteJson(pf, CustomPackSerializer.powerJson(p));
                written.add(rel(root, pf));
            }

            // Layer — additive merge. Written under our own namespace (keeping
            // the target layer's path) so every file the creator emits lives
            // under data/<CUSTOM_NAMESPACE>/. LayerDataManager#mergeForeignSamePathLayers
            // then folds neoorigins_custom:origin → origins:origin (and
            // neoorigins_custom:class → neoorigins:class) so the origin still
            // appears in the canonical picker.
            ResourceLocation layerId = draft.layerId;
            Path layerFile = dataFile(root, OriginDraft.CUSTOM_NAMESPACE,
                "origins/origin_layers", layerId.getPath());
            JsonObject existing = readJsonIfPresent(layerFile);
            JsonObject merged = CustomPackSerializer.layerPatch(existing, originId.toString());
            atomicWriteJson(layerFile, merged);
            written.add(rel(root, layerFile));

            NeoOrigins.LOGGER.info("[creator] wrote custom origin '{}' ({} files)",
                originId, written.size());
            return new WriteResult(true, written, null);
        } catch (IOException | RuntimeException e) {
            NeoOrigins.LOGGER.error("[creator] failed writing custom origin to {}", root, e);
            return WriteResult.fail(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /**
     * Resolve a pack-relative data file and hard-guarantee it stays inside the
     * pack root. {@link CreatorValidator} already rejects unsafe id paths, but
     * this is the last line of defense (and protects any future caller that
     * doesn't run the validator): {@code Path.resolve} does NOT collapse
     * {@code ..}, so a traversal id would otherwise escape {@code root}.
     */
    private static Path dataFile(Path root, String namespace, String category, String name) {
        Path normalizedRoot = root.normalize();
        Path target = normalizedRoot.resolve("data").resolve(namespace)
            .resolve(category).resolve(name + ".json").normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException(
                "refusing to write outside the pack directory: " + name);
        }
        return target;
    }

    private static void writePackMeta(Path root, List<String> written) throws IOException {
        Path meta = root.resolve("pack.mcmeta");
        if (Files.exists(meta)) return; // keep an existing one (idempotent)
        int fmt = packFormat();
        JsonObject pack = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("description", "NeoOrigins custom origins (in-game creator)");
        inner.addProperty("pack_format", fmt);
        inner.addProperty("min_format", fmt);
        inner.addProperty("max_format", fmt);
        pack.add("pack", inner);
        atomicWriteJson(meta, pack);
        written.add("pack.mcmeta");
    }

    /** Read pack_format from the mod's own packaged pack.mcmeta (zero drift). */
    private static int packFormat() {
        try (InputStream in = CustomPackWriter.class.getResourceAsStream("/pack.mcmeta")) {
            if (in != null) {
                JsonObject root = JsonParser.parseString(
                    new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                return root.getAsJsonObject("pack").get("pack_format").getAsInt();
            }
        } catch (IOException | RuntimeException e) {
            NeoOrigins.LOGGER.warn("[creator] could not read mod pack_format, using {}",
                FALLBACK_PACK_FORMAT, e);
        }
        return FALLBACK_PACK_FORMAT;
    }

    private static JsonObject readJsonIfPresent(Path file) throws IOException {
        if (!Files.isRegularFile(file)) return null;
        var el = JsonParser.parseString(Files.readString(file));
        return el.isJsonObject() ? el.getAsJsonObject() : null;
    }

    private static void atomicWriteJson(Path file, JsonObject json) throws IOException {
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String rel(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
