package com.cyberday1.neoorigins;

import com.cyberday1.neoorigins.client.NeoOriginsClientConfig;
import com.cyberday1.neoorigins.config.AdminConfig;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.config.PowerOverridesConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every translation key the mod itself asks for must have an en_us entry.
 * Keys are enumerated from the code (config specs, registries, source
 * literals, bundled data), never from the lang file.
 */
// hub: neoorigins/lang-coverage.md
class EnglishLangCoverageTest {

    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[a-z0-9_\\-]+\\.)+[a-z0-9_\\-]+)\"");
    private static final Set<String> OWN_NAMESPACES = Set.of("neoorigins", "origins");

    private static Path root;
    private static Set<String> lang;

    @BeforeAll
    static void load() throws IOException {
        String projectDir = System.getProperty("neoorigins.projectDir");
        assertNotNull(projectDir, "build.gradle must pass neoorigins.projectDir to the test task");
        root = Path.of(projectDir);
        try (Reader r = Files.newBufferedReader(
                root.resolve("src/main/resources/assets/neoorigins/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(r).getAsJsonObject().keySet();
        }
    }

    @Test
    void configScreenLabels() {
        Map<String, String> missing = new TreeMap<>();
        walkConfig(GameplayConfig.SPEC, "gameplay.toml", missing);
        walkConfig(AdminConfig.SPEC, "admin.toml", missing);
        walkConfig(PowerOverridesConfig.SPEC, "power_overrides.toml", missing);
        walkConfig(ContentTogglesConfig.SPEC, "content.toml", missing);
        walkConfig(NeoOriginsClientConfig.SPEC, "client.toml", missing);
        report("config screen labels", missing);
    }

    @Test
    void registeredContentNames() {
        Map<String, String> missing = new TreeMap<>();
        checkRegistry(BuiltInRegistries.ITEM, i -> i.getDescriptionId(), "item", missing);
        checkRegistry(BuiltInRegistries.BLOCK, b -> b.getDescriptionId(), "block", missing);
        checkRegistry(BuiltInRegistries.ENTITY_TYPE, e -> e.getDescriptionId(), "entity type", missing);
        checkRegistry(BuiltInRegistries.MOB_EFFECT, e -> e.getDescriptionId(), "mob effect", missing);
        report("registered content names", missing);
    }

    @Test
    void keysNamedInJavaSource() throws IOException {
        Map<String, String> missing = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root.resolve("src/main/java"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String src = Files.readString(p, StandardCharsets.UTF_8);
                Matcher m = STRING_LITERAL.matcher(src);
                while (m.find()) {
                    if (isOwnKey(m.group(1)) && !lang.contains(m.group(1))) {
                        int line = (int) src.substring(0, m.start()).chars().filter(c -> c == '\n').count() + 1;
                        missing.putIfAbsent(m.group(1), root.relativize(p) + ":" + line);
                    }
                }
            }
        }
        // Built as "key.neoorigins.hotkey." + n; the lang file promises names up to 64.
        for (int n = 1; n <= 64; n++) {
            if (!lang.contains("key.neoorigins.hotkey." + n)) missing.put("key.neoorigins.hotkey." + n, "NeoOriginsKeybindings");
        }
        report("keys named in Java source", missing);
    }

    @Test
    void keysNamedInBundledData() throws IOException {
        Map<String, String> missing = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root.resolve("src/main/resources/data"))) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    collectDataKeys(JsonParser.parseReader(r), root.relativize(p).toString(), missing);
                }
            }
        }
        report("keys named in bundled data", missing);
    }

    // Mirrors ConfigurationScreen#getTranslationKey: explicit key, else <modid>.configuration.<leaf>.
    private static void walkConfig(ModConfigSpec spec, String where, Map<String, String> missing) {
        walkLevel(spec, spec.getSpec(), new ArrayList<>(), where, missing);
    }

    private static void walkLevel(ModConfigSpec spec, UnmodifiableConfig level, List<String> path,
                                  String where, Map<String, String> missing) {
        for (Map.Entry<String, Object> e : level.valueMap().entrySet()) {
            List<String> sub = new ArrayList<>(path);
            sub.add(e.getKey());
            String key;
            if (e.getValue() instanceof ModConfigSpec.ValueSpec vs) {
                key = vs.getTranslationKey();
            } else {
                key = spec.getLevelTranslationKey(sub);
                if (e.getValue() instanceof UnmodifiableConfig child) walkLevel(spec, child, sub, where, missing);
            }
            if (key == null) key = NeoOrigins.MOD_ID + ".configuration." + e.getKey();
            if (!lang.contains(key)) missing.putIfAbsent(key, where + " [" + String.join(".", sub) + "]");
        }
    }

    private static <T> void checkRegistry(Registry<T> registry, Function<T, String> descriptionId,
                                          String kind, Map<String, String> missing) {
        int seen = 0;
        for (var id : registry.keySet()) {
            if (!OWN_NAMESPACES.contains(id.getNamespace())) continue;
            seen++;
            String key = descriptionId.apply(registry.get(id));
            if (!lang.contains(key)) missing.put(key, kind + " " + id);
        }
        assertFalse(seen == 0 && kind.equals("item"), "no neoorigins items registered; the harness did not load the mod");
    }

    private static void collectDataKeys(JsonElement el, String where, Map<String, String> missing) {
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            for (var e : o.entrySet()) collectDataKeys(e.getValue(), where, missing);
        } else if (el.isJsonArray()) {
            for (JsonElement c : el.getAsJsonArray()) collectDataKeys(c, where, missing);
        } else if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String s = el.getAsString();
            if (STRING_LITERAL.matcher("\"" + s + "\"").matches() && isOwnKey(s) && !lang.contains(s)) {
                missing.putIfAbsent(s, where);
            }
        }
    }

    // A dotted literal is ours if any segment is our mod id (item.neoorigins.x, key.category.neoorigins.x).
    private static boolean isOwnKey(String s) {
        return Arrays.asList(s.split("\\.")).contains(NeoOrigins.MOD_ID);
    }

    private static void report(String what, Map<String, String> missing) {
        if (missing.isEmpty()) return;
        StringBuilder sb = new StringBuilder(missing.size() + " " + what + " have no en_us.json entry:");
        missing.forEach((k, where) -> sb.append("\n  ").append(k).append("   <- ").append(where));
        fail(sb.toString());
    }
}
