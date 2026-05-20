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
        Path root = packDir(server).normalize();
        try {
            Files.createDirectories(root);

            // Phase 1 — build the full file plan (final path → JSON). No
            // filesystem writes yet, so a serialization/containment failure
            // here leaves the pack completely untouched.
            ResourceLocation originId = draft.originId();
            java.util.LinkedHashMap<Path, JsonObject> plan = new java.util.LinkedHashMap<>();

            // Re-validate pack.mcmeta rather than blindly trusting any
            // existing file: rewrite it when missing, unreadable, or its
            // pack_format no longer matches what we'd emit (a stale or
            // hand-edited mcmeta would otherwise silently break loading).
            Path meta = root.resolve("pack.mcmeta");
            if (needsPackMeta(meta)) plan.put(meta, packMetaJson());

            Path originFile = dataFile(root, originId.getNamespace(),
                "origins/origins", originId.getPath());
            if (Files.exists(originFile)) {
                NeoOrigins.LOGGER.warn("[creator] overwriting existing custom origin '{}' "
                    + "— there is no author/ownership tracking, so on a shared "
                    + "server this replaces whoever saved it last", originId);
            }
            plan.put(originFile, CustomPackSerializer.originJson(draft));

            // Powers — type carried in the body. Namespace pinned to our own
            // (never trust a client-supplied powerId namespace); the path is
            // still containment-checked in dataFile().
            for (OriginDraft.PowerDraft p : draft.powers) {
                plan.put(dataFile(root, OriginDraft.CUSTOM_NAMESPACE, "origins/powers",
                    p.powerId.getPath()), CustomPackSerializer.powerJson(p));
            }

            // Layer — additive merge, written under our own namespace so every
            // emitted file lives under data/<CUSTOM_NAMESPACE>/;
            // LayerDataManager folds it into the canonical picker.
            Path layerFile = dataFile(root, OriginDraft.CUSTOM_NAMESPACE,
                "origins/origin_layers", draft.layerId.getPath());
            plan.put(layerFile, CustomPackSerializer.layerPatch(
                readJsonIfPresent(layerFile), originId.toString()));

            List<String> written = stageAndCommit(root, plan);
            NeoOrigins.LOGGER.info("[creator] wrote custom origin '{}' ({} files)",
                originId, written.size());
            return new WriteResult(true, written, null);
        } catch (IOException | RuntimeException e) {
            NeoOrigins.LOGGER.error("[creator] failed writing custom origin to {}", root, e);
            String m = e.getMessage();
            return WriteResult.fail(e.getClass().getSimpleName() + ": "
                + (m != null ? m : "(no detail)"));
        }
    }

    /**
     * Write a mob origin to {@code origins/mob_origins/<id>.json} (+ its power
     * files). Same staged/atomic machinery and the same C1 path-containment
     * hardening as the player {@link #write(MinecraftServer, OriginDraft)};
     * no layer patch (mobs aren't layered).
     */
    public static WriteResult write(MinecraftServer server,
            com.cyberday1.neoorigins.screen.mobcreator.model.MobOriginDraft draft) {
        Path root = packDir(server).normalize();
        try {
            Files.createDirectories(root);
            ResourceLocation originId = draft.originId();
            java.util.LinkedHashMap<Path, JsonObject> plan = new java.util.LinkedHashMap<>();

            Path meta = root.resolve("pack.mcmeta");
            if (needsPackMeta(meta)) plan.put(meta, packMetaJson());

            Path mobFile = dataFile(root, originId.getNamespace(),
                "origins/mob_origins", originId.getPath());
            if (Files.exists(mobFile)) {
                NeoOrigins.LOGGER.warn("[creator] overwriting existing custom mob origin '{}' "
                    + "— no author/ownership tracking, this replaces whoever saved it last",
                    originId);
            }
            plan.put(mobFile, com.cyberday1.neoorigins.service.MobCustomPackSerializer.mobOriginJson(draft));

            for (OriginDraft.PowerDraft p : draft.powers) {
                plan.put(dataFile(root, OriginDraft.CUSTOM_NAMESPACE, "origins/powers",
                    p.powerId.getPath()), CustomPackSerializer.powerJson(p));
            }

            List<String> written = stageAndCommit(root, plan);
            // Drops carrier files (Phase 5c). Written unconditionally — other
            // mob origins in the pack may already have drops, and the modifier
            // is a no-op when no mob has a resolvable origin with drops.
            MobLootModifierGenerator.ensureCarriers(server);
            NeoOrigins.LOGGER.info("[creator] wrote custom mob origin '{}' ({} files)",
                originId, written.size());
            return new WriteResult(true, written, null);
        } catch (IOException | RuntimeException e) {
            NeoOrigins.LOGGER.error("[creator] failed writing custom mob origin to {}", root, e);
            String m = e.getMessage();
            return WriteResult.fail(e.getClass().getSimpleName() + ": "
                + (m != null ? m : "(no detail)"));
        }
    }

    /**
     * Phase 2+3 shared by both writers: stage every planned file to a .tmp
     * sibling (rolling back all temps on any failure — nothing moved), then
     * commit with same-directory atomic renames. Returns pack-relative paths.
     */
    private static List<String> stageAndCommit(Path root,
            java.util.LinkedHashMap<Path, JsonObject> plan) throws IOException {
        java.util.LinkedHashMap<Path, Path> staged = new java.util.LinkedHashMap<>();
        try {
            for (var e : plan.entrySet()) {
                Path f = e.getKey();
                Files.createDirectories(f.getParent());
                Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
                Files.writeString(tmp, GSON.toJson(e.getValue()), StandardCharsets.UTF_8);
                staged.put(f, tmp);
            }
        } catch (IOException | RuntimeException e) {
            for (Path tmp : staged.values()) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* best effort */ }
            }
            throw e;
        }
        List<String> written = new ArrayList<>();
        for (var e : staged.entrySet()) {
            commitMove(e.getValue(), e.getKey());
            written.add(rel(root, e.getKey()));
        }
        return written;
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

    /** True when pack.mcmeta is missing, unreadable, or its pack_format no
     *  longer matches what we would write (so we replace it with a correct
     *  one rather than trusting a stale/hostile file). */
    private static boolean needsPackMeta(Path meta) {
        if (!Files.isRegularFile(meta)) return true;
        try {
            JsonObject o = readJsonIfPresent(meta);
            if (o == null || !o.has("pack")) return true;
            return o.getAsJsonObject("pack").get("pack_format").getAsInt() != packFormat();
        } catch (IOException | RuntimeException e) {
            return true; // garbled — overwrite with a valid one
        }
    }

    private static JsonObject packMetaJson() {
        int fmt = packFormat();
        JsonObject pack = new JsonObject();
        JsonObject inner = new JsonObject();
        inner.addProperty("description", "NeoOrigins custom origins (in-game creator)");
        inner.addProperty("pack_format", fmt);
        inner.addProperty("min_format", fmt);
        inner.addProperty("max_format", fmt);
        pack.add("pack", inner);
        return pack;
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

    /** Move a staged temp into place atomically (falling back to a plain
     *  replace where the filesystem can't do an atomic rename). */
    private static void commitMove(Path tmp, Path file) throws IOException {
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
