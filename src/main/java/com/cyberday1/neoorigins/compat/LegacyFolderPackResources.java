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
import java.io.IOException;
import java.io.InputStream;
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

    private final PackResources delegate;
    /** One INFO line per pack, emitted the first time a legacy remap actually serves content. */
    private final AtomicBoolean announced = new AtomicBoolean(false);

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

    // ── lookup ──────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, Identifier location) {
        IoSupplier<InputStream> modern = delegate.getResource(type, location);
        if (modern != null || type != PackType.SERVER_DATA) return modern;
        String path = location.getPath();
        for (var e : DIR_MAP) {
            if (path.startsWith(e.getKey())) {
                Identifier legacy = location.withPath(e.getValue() + path.substring(e.getKey().length()));
                IoSupplier<InputStream> hit = delegate.getResource(type, legacy);
                if (hit != null) {
                    announce();
                    return hit;
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
            output.accept(loc, io);
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
                    output.accept(modernLoc, io);
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
