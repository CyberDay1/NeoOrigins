package com.cyberday1.neoorigins.rig;

import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads the mod's own shipped origins and powers into the real data managers.
 *
 * <p>{@code OriginDataManager} and {@code PowerDataManager} are reload listeners
 * whose {@code apply} step is the whole translation, validation and holder-building
 * pipeline; only {@code prepare} touches a {@code ResourceManager}. Feeding {@code
 * apply} the same map {@code prepare} would have built from
 * {@code data/neoorigins/origins/**} therefore loads the real data through the real
 * code path, with no server and no resource stack.
 */
// hub: neoorigins/headless-world-rig.md
public final class RealDatapack {

    private static final String DATA_ROOT = "data/neoorigins/origins";
    private static boolean loaded;

    private RealDatapack() {}

    /** Idempotent: the managers are singletons, so a second load would only churn. */
    public static synchronized void load() {
        if (loaded) return;
        apply(PowerDataManager.INSTANCE, read(locate("powers")));
        apply(OriginDataManager.INSTANCE, read(locate("origins")));
        loaded = true;
    }

    /** A fresh layer manager holding the shipped layers; the shared instance is left alone. */
    public static com.cyberday1.neoorigins.data.LayerDataManager layers() {
        var manager = new com.cyberday1.neoorigins.data.LayerDataManager();
        Map<ResourceLocation, com.google.gson.JsonObject> json = new HashMap<>();
        read(locate("origin_layers")).forEach((id, el) -> json.put(id, el.getAsJsonObject()));
        apply(manager, json);
        return manager;
    }

    /**
     * Loads the shipped data plus one extra pack's {@code data/<ns>/origins/**}. The
     * managers hold the union until {@link #restoreShipped()}, which a caller must run
     * after, since other tests enumerate every loaded origin and power.
     */
    public static synchronized void loadWith(Path packRoot) {
        Map<ResourceLocation, JsonElement> powers = read(locate("powers"));
        Map<ResourceLocation, JsonElement> origins = read(locate("origins"));
        try (Stream<Path> namespaces = Files.list(packRoot.resolve("data"))) {
            for (Path ns : namespaces.toList()) {
                String name = ns.getFileName().toString();
                Path p = ns.resolve("origins/powers");
                Path o = ns.resolve("origins/origins");
                if (Files.isDirectory(p)) powers.putAll(read(p, name));
                if (Files.isDirectory(o)) origins.putAll(read(o, name));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        apply(PowerDataManager.INSTANCE, powers);
        apply(OriginDataManager.INSTANCE, origins);
        loaded = false;
    }

    /** Puts the managers back to the shipped data alone. */
    public static synchronized void restoreShipped() {
        loaded = false;
        load();
    }

    /** A path in the source checkout, found by walking up from the working directory. */
    public static Path projectPath(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve(relative);
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate " + relative + " above " + Path.of("").toAbsolutePath());
    }

    /**
     * The shipped {@code data/neoorigins/origins/<kind>} directory. Resolved off the
     * classpath rather than off the working directory, because the harness does not
     * run from the project root; the fallback covers a source checkout whose
     * resources have not been processed yet.
     */
    private static Path locate(String kind) {
        java.net.URL url = RealDatapack.class.getClassLoader().getResource(DATA_ROOT + "/" + kind);
        if (url != null && "file".equals(url.getProtocol())) {
            try {
                return Path.of(url.toURI());
            } catch (java.net.URISyntaxException ignored) {
                // fall through to the source-tree search
            }
        }
        Path dir = Path.of("").toAbsolutePath();
        for (int up = 0; up < 6 && dir != null; up++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources").resolve(DATA_ROOT).resolve(kind);
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate " + DATA_ROOT + "/" + kind
            + " on the classpath or above " + Path.of("").toAbsolutePath());
    }

    private static Map<ResourceLocation, JsonElement> read(Path dir) {
        return read(dir, "neoorigins");
    }

    private static Map<ResourceLocation, JsonElement> read(Path dir, String namespace) {
        Map<ResourceLocation, JsonElement> out = new HashMap<>();
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                String name = dir.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                name = name.substring(0, name.length() - ".json".length());
                try {
                    out.put(ResourceLocation.fromNamespaceAndPath(namespace, name),
                        JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (out.isEmpty()) throw new IllegalStateException("no json found under " + dir.toAbsolutePath());
        return out;
    }

    /** {@code apply} is protected on the vanilla listener, so reach it reflectively. */
    private static void apply(Object manager, Map<ResourceLocation, ?> json) {
        try {
            Method m = manager.getClass().getDeclaredMethod("apply", Map.class,
                net.minecraft.server.packs.resources.ResourceManager.class,
                net.minecraft.util.profiling.ProfilerFiller.class);
            m.setAccessible(true);
            m.invoke(manager, json, null, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not drive " + manager.getClass().getSimpleName() + ".apply", e);
        }
    }
}
