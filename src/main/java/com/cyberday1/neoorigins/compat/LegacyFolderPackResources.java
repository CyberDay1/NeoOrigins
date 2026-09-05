package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Delegating {@link PackResources} that transparently serves 1.20-era plural
 * data folders under their modern 1.21 singular names, so legacy Origins/Apoli
 * datapacks run exactly as downloaded — zero pack edits.
 *
 * <p>1.20.x packs ship {@code data/<ns>/functions/}, {@code recipes/},
 * {@code predicates/}, {@code loot_tables/}, {@code advancements/}, plural tag
 * subfolders, etc. 1.21 renamed all of these to singular
 * ({@link net.minecraft.core.registries.Registries#elementsDirPath} /
 * {@code tagsDirPath}), so vanilla loaders simply never see the legacy content.
 * This wrapper covers BOTH access paths:
 * <ul>
 *   <li><b>lookup</b> — {@link #getResource}: modern path misses fall through
 *       to the remapped legacy path (modern content wins on duplicates);</li>
 *   <li><b>listing</b> — {@link #listResources}: legacy folders are listed
 *       under the requested modern prefix, with locations rewritten to their
 *       modern form and modern-emitted keys suppressing legacy duplicates.</li>
 * </ul>
 *
 * <p>It also rewrites the <em>contents</em> of every {@code .mcfunction} it
 * serves through {@link LegacyCommandRewriter}. This has to happen here, in the
 * resource layer, because a function whose lines use retired 1.20 syntax fails
 * to <em>compile</em> during {@code ServerFunctionLibrary} reload — it never
 * reaches the {@code CommandEvent} hook where the rewriter is otherwise applied.
 * Note that {@code ServerFunctionLibrary} takes its supplier from
 * {@link #listResources} (via {@code FileToIdConverter.listMatchingResources}),
 * not from {@link #getResource}, so both paths wrap.
 *
 * <p>The same argument applies to loot tables and item modifiers: one retired
 * function name makes the whole file fail to parse, so those JSONs are passed
 * through {@link LegacyLootFunctionRewriter} on read. That surface is gated on
 * a path prefix plus a substring test before anything is parsed — see
 * {@link #rewriteLootJson}.
 *
 * <p>Only {@link PackType#SERVER_DATA} is remapped; client resource layouts
 * did not change. Wrap points: {@link OriginsPackFinder} (originpacks/) and
 * the {@code FolderPackDetectorMixin} (world {@code datapacks/} + global pack
 * folders scanned by vanilla's FolderRepositorySource).
 */
public final class LegacyFolderPackResources implements PackResources {

    /**
     * Modern dir prefix → legacy dir prefix, both with trailing '/'.
     * Anchored at the start of the in-namespace path, so {@code function/}
     * cannot false-match {@code functions/} (the slash disambiguates) and the
     * {@code tags/*} entries never collide with the top-level ones.
     */
    private static final List<Map.Entry<String, String>> DIR_MAP = List.of(
        Map.entry("tags/entity_type/", "tags/entity_types/"),
        Map.entry("tags/game_event/",  "tags/game_events/"),
        Map.entry("tags/function/",    "tags/functions/"),
        Map.entry("tags/item/",        "tags/items/"),
        Map.entry("tags/block/",       "tags/blocks/"),
        Map.entry("tags/fluid/",       "tags/fluids/"),
        Map.entry("function/",         "functions/"),
        Map.entry("recipe/",           "recipes/"),
        Map.entry("predicate/",        "predicates/"),
        Map.entry("item_modifier/",    "item_modifiers/"),
        Map.entry("loot_table/",       "loot_tables/"),
        Map.entry("advancement/",      "advancements/"),
        Map.entry("structure/",        "structures/")
    );

    private static final String MCFUNCTION = ".mcfunction";
    private static final String JSON = ".json";

    /**
     * In-namespace prefixes whose JSON is scanned for retired loot functions.
     * Both the modern and legacy spellings appear, because {@link #getResource}
     * is asked for the modern path while a listing may hand back either.
     */
    private static final List<String> LOOT_JSON_DIRS = List.of(
        "loot_table/", "loot_tables/", "item_modifier/", "item_modifiers/");

    private final PackResources delegate;
    /** One INFO line per pack, emitted the first time a legacy remap actually serves content. */
    private final AtomicBoolean announced = new AtomicBoolean(false);
    /** Ditto for the mcfunction rewrite — packs ship hundreds of files, so never log per line. */
    private final AtomicBoolean functionsAnnounced = new AtomicBoolean(false);
    /** Ditto for the loot-function rewrite. */
    private final AtomicBoolean lootAnnounced = new AtomicBoolean(false);
    /** Ditto for the dead item→tag path repair, which is its own rewrite tier. */
    private final AtomicBoolean deadItemTagAnnounced = new AtomicBoolean(false);

    public LegacyFolderPackResources(PackResources delegate) {
        this.delegate = delegate;
    }

    /** Wrap a {@link Pack.ResourcesSupplier} so both open paths get the shim. */
    public static Pack.ResourcesSupplier wrap(Pack.ResourcesSupplier inner) {
        if (inner == null) return null;
        return new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo info) {
                return new LegacyFolderPackResources(inner.openPrimary(info));
            }

            @Override
            public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new LegacyFolderPackResources(inner.openFull(info, metadata));
            }
        };
    }

    private void announce() {
        if (announced.compareAndSet(false, true)) {
            NeoOrigins.LOGGER.info(
                "OriginsCompat: pack '{}' uses 1.20 plural data folders — legacy-folder shim engaged (serving them under their 1.21 names)",
                packId());
        }
    }

    // ── content rewriting ───────────────────────────────────────────────────

    /**
     * Single dispatch point for content rewriting: {@code .mcfunction} lines go
     * through {@link LegacyCommandRewriter#rewriteForCompile}, loot-table and
     * item-modifier JSON through {@link LegacyLootFunctionRewriter}. Everything
     * else is returned untouched, decided on the path alone so no file is read
     * that doesn't need to be.
     *
     * <p>Deliberately the compile-only tier, not the full rewrite: this runs
     * unconditionally over lines that may be perfectly valid already, and the
     * semantic rules assume the opposite. See that method for the packs that
     * proved it.
     *
     * <p>The wrapper re-reads the delegate on every {@code get()} — suppliers
     * are documented as re-runnable and vanilla does call them more than once
     * (listing, then metadata, then load), so nothing is consumed and cached.
     */
    private IoSupplier<InputStream> rewriteIfLegacy(PackType type, Identifier location,
                                                    @Nullable IoSupplier<InputStream> io) {
        if (io == null || type != PackType.SERVER_DATA) return io;
        String path = location.getPath();
        if (path.endsWith(MCFUNCTION)) return () -> rewriteFunction(io);
        if (path.endsWith(JSON) && isLootJson(path)) return () -> rewriteLootJson(io);
        return io;
    }

    private static boolean isLootJson(String path) {
        for (String dir : LOOT_JSON_DIRS) {
            if (path.startsWith(dir)) return true;
        }
        return false;
    }

    private InputStream rewriteFunction(IoSupplier<InputStream> io) throws IOException {
        byte[] raw;
        try (InputStream in = io.get()) {
            raw = in.readAllBytes();
        }
        String text = new String(raw, StandardCharsets.UTF_8);

        // Split on '\n' keeping empties, so CRLF/LF and any trailing newline all
        // survive byte-for-byte when nothing changes.
        String[] lines = text.split("\n", -1);
        boolean changed = false;
        boolean deadPaths = false;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            boolean cr = line.endsWith("\r");
            String body = cr ? line.substring(0, line.length() - 1) : line;
            String trimmed = body.trim();
            // Blank lines and mcfunction comments are not commands.
            if (trimmed.isEmpty() || trimmed.charAt(0) == '#') continue;
            String rewritten = LegacyCommandRewriter.rewriteForCompile(body);
            // Tier three, separately: a dead item→tag path compiles and runs
            // fine, so nothing downstream can catch it. Tracked apart from the
            // compile tier only so the two get their own announce line.
            String repaired = LegacyCommandRewriter.rewriteDeadItemTagPaths(rewritten);
            if (!repaired.equals(rewritten)) {
                deadPaths = true;
                rewritten = repaired;
            }
            if (rewritten.equals(body)) continue;
            lines[i] = cr ? rewritten + "\r" : rewritten;
            changed = true;
        }
        if (!changed) return new ByteArrayInputStream(raw);

        if (deadPaths) announceDeadItemTagRewrite();
        announceFunctionRewrite();
        return new ByteArrayInputStream(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    // ── loot-table JSON content rewriting ───────────────────────────────────

    /**
     * Serve a loot table / item modifier with the removed {@code set_nbt}
     * function retargeted at {@code minecraft:set_custom_data}. Same reason as
     * the mcfunction rewrite: the table fails to <em>parse</em> during reload
     * ("Unknown registry key"), long before any runtime hook could intervene,
     * and the failure cascades into every function that references it.
     *
     * <p>Gated cheaply on purpose — path prefix and suffix first, then a plain
     * substring test, so the overwhelming majority of a pack's JSONs are never
     * handed to a parser at all.
     */
    private InputStream rewriteLootJson(IoSupplier<InputStream> io) throws IOException {
        byte[] raw;
        try (InputStream in = io.get()) {
            raw = in.readAllBytes();
        }
        String text = new String(raw, StandardCharsets.UTF_8);
        if (!text.contains(LegacyLootFunctionRewriter.MARKER)) return new ByteArrayInputStream(raw);

        String rewritten = LegacyLootFunctionRewriter.rewrite(text, packId());
        if (rewritten == null) return new ByteArrayInputStream(raw);

        announceLootRewrite();
        return new ByteArrayInputStream(rewritten.getBytes(StandardCharsets.UTF_8));
    }

    private void announceLootRewrite() {
        if (lootAnnounced.compareAndSet(false, true)) {
            NeoOrigins.LOGGER.info(
                "OriginsCompat: pack '{}' uses the 'set_nbt' loot function removed in 1.20.5 — retargeting it to minecraft:set_custom_data on read",
                packId());
        }
    }

    private void announceDeadItemTagRewrite() {
        if (deadItemTagAnnounced.compareAndSet(false, true)) {
            NeoOrigins.LOGGER.info(
                "OriginsCompat: pack '{}' reads item NBT through the 'tag' field 1.20.5 deleted — repointing those paths at components.\"minecraft:custom_data\" on read",
                packId());
        }
    }

    private void announceFunctionRewrite() {
        if (functionsAnnounced.compareAndSet(false, true)) {
            NeoOrigins.LOGGER.info(
                "OriginsCompat: pack '{}' ships 1.20-era command syntax in its .mcfunction files — rewriting them to 1.21 syntax on read",
                packId());
        }
    }

    // ── lookup ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        IoSupplier<InputStream> modern = delegate.getResource(type, location);
        if (modern != null || type != PackType.SERVER_DATA) return rewriteIfLegacy(type, location, modern);
        String path = location.getPath();
        for (var e : DIR_MAP) {
            if (path.startsWith(e.getKey())) {
                Identifier legacy = location.withPath(e.getValue() + path.substring(e.getKey().length()));
                IoSupplier<InputStream> hit = delegate.getResource(type, legacy);
                if (hit != null) {
                    announce();
                    return rewriteIfLegacy(type, location, hit);
                }
                break; // prefixes are mutually exclusive — only one can match
            }
        }
        return null;
    }

    // ── listing ─────────────────────────────────────────────────────────────

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) {
            delegate.listResources(type, namespace, path, output);
            return;
        }
        // Forward the modern listing, remembering what it emitted so legacy
        // duplicates lose ("new folder wins").
        Set<Identifier> emitted = new HashSet<>();
        delegate.listResources(type, namespace, path, (loc, io) -> {
            emitted.add(loc);
            output.accept(loc, rewriteIfLegacy(type, loc, io));
        });

        for (var e : DIR_MAP) {
            String modernDir = e.getKey();   // with trailing '/'
            String legacyDir = e.getValue(); // with trailing '/'
            String legacyListPath;
            if (path.isEmpty()) {
                legacyListPath = stripSlash(legacyDir);
            } else if ((path + "/").startsWith(modernDir)) {
                // Requested at or below the modern dir (the common case:
                // ServerFunctionLibrary lists "function", TagLoader lists
                // "tags/item", ...). Splice the remainder onto the legacy dir.
                legacyListPath = stripSlash(legacyDir + (path + "/").substring(modernDir.length()));
            } else if (modernDir.startsWith(path + "/")) {
                // Requested a parent of the modern dir (e.g. "tags") — list
                // the whole legacy dir; remapping keeps results under `path`.
                legacyListPath = stripSlash(legacyDir);
            } else {
                continue;
            }
            delegate.listResources(type, namespace, legacyListPath, (loc, io) -> {
                String p = loc.getPath();
                if (!p.startsWith(legacyDir)) return; // defensive: unexpected shape
                Identifier modernLoc = loc.withPath(modernDir + p.substring(legacyDir.length()));
                if (emitted.add(modernLoc)) {
                    announce();
                    output.accept(modernLoc, rewriteIfLegacy(type, modernLoc, io));
                }
            });
        }
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    // ── passthrough ─────────────────────────────────────────────────────────

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        return delegate.getRootResource(elements);
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return delegate.getNamespaces(type);
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionType<T> serializer) throws IOException {
        return delegate.getMetadataSection(serializer);
    }

    @Override
    public PackLocationInfo location() {
        return delegate.location();
    }

    @Override
    public boolean isHidden() {
        return delegate.isHidden();
    }

    @Override
    public void close() {
        delegate.close();
    }
}
