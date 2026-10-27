package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.registry.BuiltinPowers;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code power_overrides.toml} key, on every built-in power, must land on a
 * field the power actually reads, and its default must be the value the power ships
 * with. The loader writes a key into the power JSON before the codec runs, so a key
 * whose field the type never reads is silently dead, and a default that differs
 * from the shipped JSON would retune the power for every server that has the file.
 *
 * <p>Each key is overridden with a sentinel and the power is resolved the way the
 * loader resolves it (overrides, then the legacy alias remap). The result must
 * differ from the unmodified power. When the only difference is the key itself at
 * the JSON root, the resolved type must read that field (its {@link BuiltinPowers}
 * field specs or its codec's keys), and its default must match the config's.
 * Nested targets must already exist in the shipped JSON.
 */
class PowerOverridesReachTest {

    private static final double SENTINEL = 4242.0;

    private final Map<String, ModConfigSpec.ConfigValue<?>> swapped = new HashMap<>();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restore() {
        swapped.forEach((key, cv) -> {
            String[] k = key.split("#", 2);
            PowerOverridesConfig.POWER_OVERRIDES.get(k[0]).put(k[1], cv);
        });
        swapped.clear();
    }

    @Test
    void everyOverrideKeyReachesAFieldThePowerReads() throws IOException {
        List<String> problems = new ArrayList<>();
        for (var power : PowerOverridesConfig.POWER_OVERRIDES.entrySet()) {
            String path = power.getKey().substring("neoorigins:".length());
            Path file = powerFile(path);
            if (!Files.exists(file)) {
                problems.add(path + ": no shipped power file, so the section configures nothing");
                continue;
            }
            for (var field : List.copyOf(power.getValue().entrySet())) {
                String label = path + "." + field.getKey();
                String problem = check(power.getKey(), field.getKey(), field.getValue(), file);
                if (problem != null) problems.add(label + ": " + problem);
            }
        }
        assertTrue(problems.isEmpty(), "power_overrides keys that never reach the power:\n  "
            + String.join("\n  ", problems));
    }

    @Test
    void aKeyWithSeveralTargetsWritesEachOne() throws IOException {
        for (String[] c : new String[][] {
                {"neoorigins:cinderborn_fireball", "speed"},
                {"neoorigins:frostborn_freeze_aura", "radius"},
                {"neoorigins:sylvan_active_root", "duration_ticks"}}) {
            var shipped = PowerOverridesConfig.POWER_OVERRIDES.get(c[0]).get(c[1]);
            List<String> targets = PowerOverridesConfig.getOverrideTargets(c[0], c[1]);
            assertTrue(targets.size() > 1, c[0] + "." + c[1] + " should have several targets");
            Object sentinel = shipped.getDefault() instanceof Integer ? (Object) (int) SENTINEL : (Object) SENTINEL;
            swap(c[0], c[1], shipped, sentinel);
            PowerDataManager.Resolved resolved;
            try {
                resolved = PowerDataManager.resolvePowerType(ResourceLocation.parse(c[0]),
                    read(powerFile(c[0].substring("neoorigins:".length()))));
            } finally {
                PowerOverridesConfig.POWER_OVERRIDES.get(c[0]).put(c[1], shipped);
                swapped.remove(c[0] + "#" + c[1]);
            }
            for (String target : targets) {
                assertTrue(sameValue(sentinel, at(resolved.json(), target)),
                    c[0] + "." + c[1] + " did not reach " + target);
            }
        }
    }

    private String check(String powerId, String field, ModConfigSpec.ConfigValue<?> shipped, Path file)
            throws IOException {
        JsonObject original = read(file);
        Object def = shipped.getDefault();
        JsonElement authored = null;
        for (String target : PowerOverridesConfig.getOverrideTargets(powerId, field)) {
            JsonElement value = at(original, target);
            if (target.contains(".") && value == null) {
                return "nested target " + target + " is not in the shipped JSON";
            }
            if (value != null && !sameValue(def, value)) {
                return "config default " + def + " but the power ships " + value + " at " + target;
            }
            if (value != null) authored = value;
        }

        // Resolve without and with a sentinel, exactly as the loader does, and diff.
        ResourceLocation id = ResourceLocation.parse(powerId);
        PowerDataManager.Resolved plain = PowerDataManager.resolvePowerType(id, read(file));
        Object sentinel = def instanceof Boolean b ? (Object) !b
            : def instanceof Integer ? (Object) (int) SENTINEL : (Object) SENTINEL;
        swap(powerId, field, shipped, sentinel);
        PowerDataManager.Resolved resolved;
        try {
            resolved = PowerDataManager.resolvePowerType(id, read(file));
        } finally {
            PowerOverridesConfig.POWER_OVERRIDES.get(powerId).put(field, shipped);
            swapped.remove(powerId + "#" + field);
        }
        if (plain == null || resolved == null) return "power does not resolve";

        Set<String> changed = new HashSet<>();
        diff(plain.json(), resolved.json(), "", changed);
        if (!plain.typeId().equals(resolved.typeId())) changed.add("type");
        if (changed.isEmpty()) return "writing it changes nothing in the resolved " + resolved.typeId();
        // Anything beyond the key itself landing at the root means a nested target
        // or a legacy remap consumed the value; both reach the power.
        if (!changed.equals(Set.of(field))) return null;

        BuiltinPowers.PowerSpec spec = BuiltinPowers.get(resolved.typeId());
        if (spec == null || !readsRoot(spec, field)) {
            return "lands at the root, which " + resolved.typeId() + " does not read";
        }
        if (authored == null) {
            Object specDefault = specDefault(spec, field);
            if (specDefault != null && !sameValue(def, specDefault)) {
                return "config default " + def + " but " + resolved.typeId() + " defaults to " + specDefault;
            }
        }
        return null;
    }

    private static void diff(JsonElement a, JsonElement b, String path, Set<String> out) {
        if (a != null && b != null && a.isJsonObject() && b.isJsonObject()) {
            Set<String> keys = new HashSet<>(a.getAsJsonObject().keySet());
            keys.addAll(b.getAsJsonObject().keySet());
            for (String k : keys) {
                diff(a.getAsJsonObject().get(k), b.getAsJsonObject().get(k),
                    path.isEmpty() ? k : path + "." + k, out);
            }
        } else if (a == null ? b != null : !a.equals(b)) {
            out.add(path);
        }
    }

    private static boolean readsRoot(BuiltinPowers.PowerSpec spec, String field) {
        for (FieldSpec f : spec.fields()) if (f.name().equals(field)) return true;
        try {
            var type = (com.cyberday1.neoorigins.api.power.PowerType<?>)
                spec.powerClass().getDeclaredConstructor().newInstance();
            if (type.codec() instanceof MapCodec.MapCodecCodec<?> mc) {
                return mc.codec().keys(JsonOps.INSTANCE)
                    .anyMatch(k -> k.isJsonPrimitive() && k.getAsString().equals(field));
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // No public no-arg constructor: the field specs are the whole answer.
        }
        return false;
    }

    private static Object specDefault(BuiltinPowers.PowerSpec spec, String field) {
        for (FieldSpec f : spec.fields()) if (f.name().equals(field)) return f.defaultValue();
        return null;
    }

    private void swap(String powerId, String field, ModConfigSpec.ConfigValue<?> shipped, Object value) {
        swapped.putIfAbsent(powerId + "#" + field, shipped);
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Object> cv = Mockito.mock(ModConfigSpec.ConfigValue.class);
        Mockito.when(cv.get()).thenReturn(value);
        Mockito.when(cv.getDefault()).thenReturn(shipped.getDefault());
        PowerOverridesConfig.POWER_OVERRIDES.get(powerId).put(field, cv);
    }

    private static boolean sameValue(Object def, Object value) {
        if (value instanceof JsonElement el) {
            if (!el.isJsonPrimitive()) return false;
            if (def instanceof Number n) {
                return el.getAsJsonPrimitive().isNumber() && Math.abs(n.doubleValue() - el.getAsDouble()) < 1e-9;
            }
            return def.toString().equals(el.getAsString());
        }
        if (def instanceof Number a && value instanceof Number b) return Math.abs(a.doubleValue() - b.doubleValue()) < 1e-9;
        return def.toString().equals(value.toString());
    }

    private static final Path POWERS = RealDatapack.projectPath("src/main/resources/data/neoorigins/origins/powers");

    private static Path powerFile(String power) {
        return POWERS.resolve(power + ".json");
    }

    private static JsonObject read(Path file) throws IOException {
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static JsonElement at(JsonObject json, String path) {
        JsonElement cur = json;
        for (String part : path.split("\\.")) {
            if (cur == null) return null;
            if (cur.isJsonObject()) {
                cur = cur.getAsJsonObject().get(part);
            } else if (cur.isJsonArray() && part.chars().allMatch(Character::isDigit)) {
                int i = Integer.parseInt(part);
                cur = i < cur.getAsJsonArray().size() ? cur.getAsJsonArray().get(i) : null;
            } else {
                return null;
            }
        }
        return cur;
    }
}
