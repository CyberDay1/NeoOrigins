package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.api.origin.ConditionedOrigin;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.origin.OriginTierOverlay;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.data.ModGate;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Renders POWER_REFERENCE.txt from what the build loads: layers and origins
 * through their codecs, powers through PowerDataManager, fields from each
 * registered type's parser, text from en_us.json. Needs the FML-bootstrapped
 * JUnit harness, which is why it lives in the test tree.
 */
// hub: neoorigins/power-reference.md
public final class PowerReferenceGenerator {

    private static final String DATA = "data/neoorigins/origins/";
    private static final String RULE = "=".repeat(80);
    private static final String THIN = "-".repeat(80);
    private static final int WIDTH = 78;

    /** Keys PowerDataManager#parsePower strips (or injects) around the type's own decode. */
    private static final Set<String> NOT_TYPE_FIELDS = Set.of(
        "type", "name", "description", "power_condition", "power_condition_mode", "required_mods", "_power_id");

    private PowerReferenceGenerator() {}

    /** @param resources the project's src/main/resources, the tree the build packages */
    public static String render(Path resources) {
        RealDatapack.load();
        Map<String, String> lang = lang(resources);
        Map<Identifier, JsonObject> powerJson = readDir(resources, "powers");
        Map<Identifier, JsonObject> originJson = readDir(resources, "origins");
        Map<Identifier, OriginLayer> layers = new TreeMap<>();
        readDir(resources, "origin_layers").forEach((id, json) -> layers.put(id, decode(OriginLayer.CODEC, id, json)));
        Map<Identifier, Origin> origins = new TreeMap<>();
        originJson.forEach((id, json) -> origins.put(id, decode(Origin.CODEC, id, json)));
        Map<Class<?>, Identifier> typeIds = registeredTypes();

        StringBuilder out = new StringBuilder();
        header(out);
        Stats stats = new Stats();
        originsSection(out, lang, layers, origins, originJson, powerJson, typeIds, stats);
        typesSection(out, powerJson, typeIds, stats);
        statsSection(out, layers, origins, originJson, powerJson, stats);
        return out.toString();
    }

    // ── section 1 ────────────────────────────────────────────────────────

    private static void header(StringBuilder out) {
        line(out, RULE);
        line(out, "  NeoOrigins - Power Reference for Modpack Creators");
        line(out, RULE);
        line(out, "");
        line(out, "  Generated from what this build loads, not written by hand:");
        line(out, "    origins  the shipped origin JSON, decoded by Origin.CODEC");
        line(out, "    powers   the shipped power JSON, run through PowerDataManager");
        line(out, "    types    the power types PowerTypes registers, with the fields");
        line(out, "             each type's parser reads");
        line(out, "    text     names and descriptions from assets/neoorigins/lang/en_us.json");
        line(out, "");
        line(out, "  Regenerate with:");
        line(out, "    ./gradlew test --tests '*PowerReferenceTest' -PwritePowerReference");
        line(out, "  PowerReferenceTest fails the build when this file is stale.");
        line(out, "");
        line(out, "  Contents:");
        line(out, "    1. Origins and their powers, by layer");
        line(out, "    2. Power types");
        line(out, "    3. Quick stats");
        line(out, "");
    }

    private static void originsSection(StringBuilder out, Map<String, String> lang,
            Map<Identifier, OriginLayer> layers, Map<Identifier, Origin> origins,
            Map<Identifier, JsonObject> originJson, Map<Identifier, JsonObject> powerJson,
            Map<Class<?>, Identifier> typeIds, Stats stats) {
        line(out, "");
        line(out, RULE);
        line(out, "  1. ORIGINS AND THEIR POWERS");
        line(out, RULE);

        Map<Identifier, Identifier> layerOf = new LinkedHashMap<>();
        List<OriginLayer> ordered = layers.values().stream()
            .sorted(Comparator.comparingInt(OriginLayer::order).thenComparing(l -> l.id().toString()))
            .toList();
        for (OriginLayer layer : ordered) {
            for (ConditionedOrigin co : layer.origins()) layerOf.putIfAbsent(co.origin(), layer.id());
        }

        for (OriginLayer layer : ordered) {
            List<Origin> members = layer.origins().stream().map(ConditionedOrigin::origin)
                .filter(origins::containsKey).map(origins::get).toList();
            section(out, "LAYER " + layer.id() + " - " + text(layer.name(), lang, "") + " ("
                + members.size() + " origins, layer order " + layer.order() + ")");
            for (Origin origin : sortByOrder(members)) {
                origin(out, lang, origin, originJson.get(origin.id()), powerJson, typeIds, stats);
            }
        }
        List<Origin> loose = origins.values().stream().filter(o -> !layerOf.containsKey(o.id())).toList();
        if (!loose.isEmpty()) {
            section(out, "NOT IN A BUILT-IN LAYER (" + loose.size() + " origins)");
            for (Origin origin : sortByOrder(loose)) {
                origin(out, lang, origin, originJson.get(origin.id()), powerJson, typeIds, stats);
            }
        }
    }

    private static List<Origin> sortByOrder(List<Origin> origins) {
        return origins.stream()
            .sorted(Comparator.comparingInt(Origin::order).thenComparing(o -> o.id().toString()))
            .toList();
    }

    private static void origin(StringBuilder out, Map<String, String> lang, Origin origin, JsonObject json,
            Map<Identifier, JsonObject> powerJson, Map<Class<?>, Identifier> typeIds, Stats stats) {
        line(out, "");
        line(out, THIN);
        line(out, "  " + text(origin.name(), lang, origin.id().toString()).toUpperCase(java.util.Locale.ROOT));
        line(out, "  ID: " + origin.id() + " | Impact: " + origin.impact() + " | Order: " + origin.order()
            + " | Icon: " + iconId(json));
        String mods = requiredMods(json);
        if (mods != null) line(out, "  Loads only with mods: " + mods);
        line(out, THIN);
        wrap(out, "  ", text(origin.description(), lang, ""));

        if (origin.powers().isEmpty()) {
            line(out, "");
            line(out, "  No powers.");
        }
        for (Identifier id : origin.powers()) power(out, lang, id, powerJson, typeIds, stats);
        for (OriginTierOverlay tier : origin.tierPowers().stream()
                .sorted(Comparator.comparingInt(OriginTierOverlay::tier)).toList()) {
            line(out, "");
            line(out, "  Tier " + tier.tier() + " adds" + (tier.remove().isEmpty() ? ":"
                : " (and removes " + String.join(", ", tier.remove().stream().map(Object::toString).toList()) + "):"));
            for (Identifier id : tier.add()) power(out, lang, id, powerJson, typeIds, stats);
        }
    }

    private static void power(StringBuilder out, Map<String, String> lang, Identifier id,
            Map<Identifier, JsonObject> powerJson, Map<Class<?>, Identifier> typeIds, Stats stats) {
        line(out, "");
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id);
        JsonObject json = powerJson.get(id);
        String nameKey = "power." + id.getNamespace() + "." + id.getPath() + ".name";
        String descKey = "power." + id.getNamespace() + "." + id.getPath() + ".description";
        String name = holder != null ? text(holder.name(), lang, "") : "";
        if (name.isEmpty()) name = lang.getOrDefault(nameKey, "");
        String desc = holder != null ? text(holder.description(), lang, "") : "";
        if (desc.isEmpty()) desc = lang.getOrDefault(descKey, "");
        line(out, "  " + (name.isEmpty() ? id.toString() : name + " (" + id + ")"));

        String written = json != null && json.has("type") ? json.get("type").getAsString() : null;
        if (holder != null) {
            Identifier loaded = typeIds.get(holder.type().getClass());
            String type = loaded != null ? loaded.toString() : holder.type().getClass().getSimpleName();
            line(out, "    Type: " + type + (written != null && !written.equals(type) ? " (written as " + written + ")" : ""));
            if (holder.hidden()) line(out, "    Hidden: yes");
        } else if (json == null) {
            stats.missing.add(id);
            line(out, "    MISSING: no power file with this id is shipped");
        } else {
            String mods = requiredMods(json);
            Identifier target = written == null ? null
                : LegacyPowerTypeAliases.aliasTarget(Identifier.parse(written));
            line(out, "    Type: " + (target != null ? target + " (written as " + written + ")" : written));
            if (mods != null) {
                line(out, "    Loads only with mods: " + mods);
            } else {
                stats.notLoading.add(id);
                line(out, "    DOES NOT LOAD in this build");
            }
        }
        if (!desc.isEmpty()) wrap(out, "    ", desc);
    }

    // ── section 2 ────────────────────────────────────────────────────────

    private static void typesSection(StringBuilder out, Map<Identifier, JsonObject> powerJson,
            Map<Class<?>, Identifier> typeIds, Stats stats) {
        line(out, "");
        line(out, "");
        line(out, RULE);
        line(out, "  2. POWER TYPES");
        line(out, RULE);
        line(out, "");
        wrap(out, "  ", "Every power type this build registers. \"Fields\" are the keys the type's own "
            + "parser reads, beyond the ones every power accepts (name, description, hidden, "
            + "power_condition, power_condition_mode, required_mods). \"Shipped powers\" counts "
            + "the built-in powers that load as this type.");

        Map<Identifier, Integer> uses = new TreeMap<>();
        for (PowerHolder<?> h : PowerDataManager.INSTANCE.getPowers().values()) {
            Identifier t = typeIds.get(h.type().getClass());
            if (t != null && h.id().getNamespace().equals("neoorigins")) uses.merge(t, 1, Integer::sum);
        }

        Map<Identifier, Class<?>> byId = new TreeMap<>(Comparator.comparing(Identifier::toString));
        typeIds.forEach((cls, id) -> byId.put(id, cls));
        for (var e : byId.entrySet()) {
            PowerType<?> type = instantiate(e.getValue());
            line(out, "");
            line(out, "  " + e.getKey());
            line(out, "    " + (type.isActivePower() ? "Active" : "Passive") + " | Shipped powers: "
                + uses.getOrDefault(e.getKey(), 0) + " | " + e.getValue().getSimpleName());
            Codec<?> codec = type.codec();
            Set<String> fields;
            if (codec instanceof MapCodec.MapCodecCodec<?> map) {
                stats.codecTypes++;
                fields = new TreeSet<>();
                map.codec().keys(JsonOps.INSTANCE).forEach(k -> fields.add(k.getAsString()));
                fields.removeAll(NOT_TYPE_FIELDS);
                wrap(out, "    ", "Fields: " + (fields.isEmpty() ? "none" : String.join(", ", fields)));
            } else {
                stats.handWrittenTypes++;
                int[] samples = {0};
                fields = observedFields(codec, e.getKey(), powerJson, samples);
                wrap(out, "    ", "Fields: " + (fields.isEmpty() ? "none observed" : String.join(", ", fields)));
                wrap(out, "    ", "(hand-written parser: the keys it read from an empty object and from "
                    + samples[0] + " shipped power" + (samples[0] == 1 ? "" : "s") + "; a branch those never reach may read more)");
            }
        }

        Map<String, String> aliases = new TreeMap<>();
        for (Identifier from : LegacyPowerTypeAliases.aliasedTypeIds()) {
            aliases.put(from.toString(), String.valueOf(LegacyPowerTypeAliases.aliasTarget(from)));
        }
        line(out, "");
        line(out, "");
        line(out, "  Older type ids that still load, and the type each loads as:");
        line(out, "");
        aliases.forEach((from, to) -> line(out, "    " + from + " -> " + to));
        stats.aliases = aliases.size();
    }

    /**
     * The hand-written decoders open with {@code ops.convertTo(JsonOps.INSTANCE, input)},
     * which copies the object. Handing JSON through unchanged keeps the spy the thing read.
     */
    private static final JsonOps PASS_THROUGH = new JsonOps(false) {
        @Override
        @SuppressWarnings("unchecked")
        public <U> U convertTo(com.mojang.serialization.DynamicOps<U> outOps, JsonElement input) {
            return outOps instanceof JsonOps ? (U) input : super.convertTo(outOps, input);
        }
    };

    /** Keys a hand-written decoder reads, recorded off a spy of its input. */
    private static Set<String> observedFields(Codec<?> codec, Identifier typeId,
            Map<Identifier, JsonObject> powerJson, int[] samples) {
        Set<String> seen = new TreeSet<>();
        List<JsonObject> inputs = new ArrayList<>();
        inputs.add(new JsonObject());
        powerJson.forEach((id, json) -> {
            if (json.has("type") && json.get("type").getAsString().equals(typeId.toString())) {
                JsonObject config = json.deepCopy();
                NOT_TYPE_FIELDS.forEach(config::remove);
                config.addProperty("_power_id", id.toString());
                inputs.add(config);
                samples[0]++;
            }
        });
        for (JsonObject input : inputs) {
            JsonObject spy = Mockito.spy(input);
            try {
                codec.parse(PASS_THROUGH, spy);
            } catch (RuntimeException ignored) {
                // a throw part-way still leaves the reads made before it
            }
            for (Invocation call : Mockito.mockingDetails(spy).getInvocations()) {
                String method = call.getMethod().getName();
                if (call.getArguments().length == 1 && call.getArguments()[0] instanceof String key
                        && (method.equals("has") || method.equals("get") || method.equals("remove")
                            || method.startsWith("getAsJson"))) {
                    seen.add(key);
                }
            }
        }
        seen.removeAll(NOT_TYPE_FIELDS);
        return seen;
    }

    // ── section 3 ────────────────────────────────────────────────────────

    private static void statsSection(StringBuilder out, Map<Identifier, OriginLayer> layers,
            Map<Identifier, Origin> origins, Map<Identifier, JsonObject> originJson,
            Map<Identifier, JsonObject> powerJson, Stats stats) {
        line(out, "");
        line(out, "");
        line(out, RULE);
        line(out, "  3. QUICK STATS");
        line(out, RULE);
        line(out, "");
        line(out, "  Origins shipped:         " + origins.size());
        for (OriginLayer layer : layers.values()) {
            line(out, "    in " + layer.id() + ": " + layer.origins().size());
        }
        long gatedOrigins = originJson.values().stream().filter(j -> requiredMods(j) != null).count();
        line(out, "    needing another mod:   " + gatedOrigins);
        Map<String, Integer> impact = new TreeMap<>();
        origins.values().forEach(o -> impact.merge(o.impact().toString(), 1, Integer::sum));
        line(out, "    by impact:             " + impact);
        long loaded = powerJson.keySet().stream().filter(id -> PowerDataManager.INSTANCE.getPower(id) != null).count();
        long gatedPowers = powerJson.values().stream().filter(j -> requiredMods(j) != null).count();
        line(out, "  Power files shipped:     " + powerJson.size());
        line(out, "    load with no other mod: " + loaded);
        line(out, "    needing another mod:   " + gatedPowers);
        line(out, "    do not load:           " + stats.notLoading.size());
        line(out, "    referenced, not shipped: " + stats.missing.size());
        line(out, "  Power types registered:  " + (stats.codecTypes + stats.handWrittenTypes));
        line(out, "    codec parsers:         " + stats.codecTypes);
        line(out, "    hand-written parsers:  " + stats.handWrittenTypes);
        line(out, "  Older type ids aliased:  " + stats.aliases);
        line(out, "");
        line(out, RULE);
        line(out, "  End of Power Reference");
        line(out, RULE);
    }

    // ── plumbing ─────────────────────────────────────────────────────────

    private static final class Stats {
        final Set<Identifier> notLoading = new TreeSet<>();
        final Set<Identifier> missing = new TreeSet<>();
        int codecTypes;
        int handWrittenTypes;
        int aliases;
    }

    /** Registered type id per power class, read off PowerTypes' DeferredHolder fields. */
    private static Map<Class<?>, Identifier> registeredTypes() {
        Map<Class<?>, Identifier> out = new LinkedHashMap<>();
        for (var field : PowerTypes.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())
                    || !(field.getGenericType() instanceof ParameterizedType pt)
                    || pt.getRawType() != DeferredHolder.class) continue;
            try {
                out.put((Class<?>) pt.getActualTypeArguments()[1], ((DeferredHolder<?, ?>) field.get(null)).getId());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        if (out.isEmpty()) throw new IllegalStateException("read no power types off PowerTypes");
        return out;
    }

    private static PowerType<?> instantiate(Class<?> cls) {
        try {
            return (PowerType<?>) cls.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + cls, e);
        }
    }

    private static <T> T decode(Codec<T> codec, Identifier id, JsonObject json) {
        JsonObject copy = json.deepCopy();
        copy.addProperty("id", id.toString());
        return codec.parse(JsonOps.INSTANCE, copy).getOrThrow(err -> new IllegalStateException(id + ": " + err));
    }

    /** The icon id as IconCodec reads it; its ItemStack comes back empty under the harness. */
    private static String iconId(JsonObject json) {
        JsonElement icon = json == null ? null : json.get("icon");
        if (icon == null) return "none";
        if (icon.isJsonPrimitive()) return icon.getAsString();
        if (icon.isJsonObject()) {
            JsonObject o = icon.getAsJsonObject();
            if (o.has("item")) return o.get("item").getAsString();
            if (o.has("id")) return o.get("id").getAsString();
        }
        return "unreadable";
    }

    /** The required_mods list when ModGate would drop this file without them, else null. */
    private static String requiredMods(JsonObject json) {
        if (json == null || ModGate.satisfied(json.get("required_mods"))) return null;
        List<String> mods = new ArrayList<>();
        json.getAsJsonArray("required_mods").forEach(e -> mods.add(e.getAsString()));
        return String.join(", ", mods);
    }

    private static String text(Component component, Map<String, String> lang, String fallback) {
        if (component == null) return fallback;
        if (component.getContents() instanceof TranslatableContents tc) {
            return lang.getOrDefault(tc.getKey(), tc.getFallback() != null ? tc.getFallback() : fallback);
        }
        String s = component.getString();
        return s.isEmpty() ? fallback : s;
    }

    private static Map<String, String> lang(Path resources) {
        try {
            Map<String, String> out = new TreeMap<>();
            JsonParser.parseString(Files.readString(resources.resolve("assets/neoorigins/lang/en_us.json"),
                    StandardCharsets.UTF_8)).getAsJsonObject()
                .entrySet().forEach(e -> out.put(e.getKey(), e.getValue().getAsString()));
            return out;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Map<Identifier, JsonObject> readDir(Path resources, String kind) {
        Path dir = resources.resolve(DATA + kind);
        if (!Files.isDirectory(dir)) throw new IllegalStateException("no directory " + dir);
        Map<Identifier, JsonObject> out = new TreeMap<>(Comparator.comparing(Identifier::toString));
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String name = dir.relativize(p).toString().replace(java.io.File.separatorChar, '/');
                name = name.substring(0, name.length() - ".json".length());
                JsonElement el = JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8));
                out.put(Identifier.fromNamespaceAndPath("neoorigins", name), el.getAsJsonObject());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    private static void section(StringBuilder out, String title) {
        line(out, "");
        line(out, "");
        line(out, "  " + title);
        line(out, "  " + "~".repeat(Math.min(title.length(), WIDTH - 2)));
    }

    private static void wrap(StringBuilder out, String indent, String text) {
        if (text == null || text.isBlank()) return;
        StringBuilder current = new StringBuilder(indent);
        for (String word : text.trim().split("\\s+")) {
            if (current.length() > indent.length() && current.length() + 1 + word.length() > WIDTH) {
                line(out, current.toString());
                current = new StringBuilder(indent);
            }
            if (current.length() > indent.length()) current.append(' ');
            current.append(word);
        }
        line(out, current.toString());
    }

    private static void line(StringBuilder out, String s) {
        out.append(s.stripTrailing()).append('\n');
    }
}
