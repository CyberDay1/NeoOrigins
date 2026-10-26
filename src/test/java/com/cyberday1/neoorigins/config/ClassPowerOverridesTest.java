package com.cyberday1.neoorigins.config;

import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.rig.RealDatapack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code power_overrides.toml} key on a built-in class power must reach the
 * value the power actually reads, and its default must be what the power ships with.
 * A key written at the JSON root of an {@code action_on_event} power, whose value
 * lives in {@code modifier.value}, used to be silently ignored.
 */
class ClassPowerOverridesTest {

    /** Root fields the shipped JSON omits because the type's codec default is used. */
    private static final Set<String> CODEC_DEFAULTED = Set.of(
        "class_beastmaster_diffusal.radius",
        "class_titan_size.modify_reach",
        "class_titan_size.reach_bonus",
        "class_rogue_sneaky.detection_multiplier",
        "class_rogue_stealth.activation_ticks",
        "class_lumberjack_tree_felling.max_blocks",
        "class_merchant_trades.scan_interval",
        "class_merchant_trades.radius",
        "class_cook_smoker_xp.multiplier");

    private final Map<String, ModConfigSpec.ConfigValue<?>> swapped = new HashMap<>();

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void restore() {
        boolean changed = !swapped.isEmpty();
        swapped.forEach((key, cv) -> {
            String[] k = key.split("#", 2);
            PowerOverridesConfig.POWER_OVERRIDES.get(k[0]).put(k[1], cv);
        });
        swapped.clear();
        if (changed) RealDatapack.restoreShipped();
    }

    @Test
    void everyClassOverrideTargetsAShippedValueWithAMatchingDefault() throws IOException {
        List<String> problems = new ArrayList<>();
        for (var power : PowerOverridesConfig.POWER_OVERRIDES.entrySet()) {
            String path = power.getKey().substring("neoorigins:".length());
            if (!path.startsWith("class_") && !path.startsWith("classic_")) continue;
            JsonObject json = shipped(path);
            for (var field : power.getValue().entrySet()) {
                String target = PowerOverridesConfig.getOverrideTarget(power.getKey(), field.getKey());
                JsonElement value = at(json, target);
                String label = path + "." + field.getKey() + " -> " + target;
                if (value == null) {
                    if (target.contains(".") || !CODEC_DEFAULTED.contains(path + "." + field.getKey())) {
                        problems.add(label + ": not in the shipped JSON, so the power never reads it");
                    }
                    continue;
                }
                Object def = field.getValue().getDefault();
                boolean same = def instanceof Number n
                    ? Math.abs(n.doubleValue() - value.getAsDouble()) < 1e-9
                    : def.toString().equals(value.getAsString());
                if (!same) problems.add(label + ": config default " + def + " but the power ships " + value);
            }
        }
        assertTrue(problems.isEmpty(), "class power overrides out of step with the JSON:\n  "
            + String.join("\n  ", problems));
    }

    @ParameterizedTest(name = "{0}.{1} = {2}")
    @CsvSource({
        // power,                      field,           value, base, expected
        "class_beastmaster_potions,    multiplier,      3.0,   10,   30",
        "class_berserker_hunger,       multiplier,      2.0,   10,   20",
        "class_explorer_stamina,       multiplier,      0.25,  10,   2.5",
        "class_cleric_potions,         multiplier,      4.0,   10,   40",
        "class_miner_efficiency,       multiplier,      0.5,   10,   5",
        "class_blacksmith_repairs,     cost_multiplier, 0.25,  20,   5",
        "class_cleric_enchanting,      bonus_levels,    9,     10,   19",
        "class_cook_food,              saturation_bonus,1.5,   0,    1.5",
    })
    void modifierOverrideReachesTheLoadedPower(String power, String field, double value,
                                               float base, float expected) {
        override(power, field, value);
        PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(id(power));
        assertNotNull(holder, power + " did not load");
        var config = (ActionOnEventPower.Config) holder.config();
        assertEquals(expected, config.modifier().apply(base), 1e-4,
            power + "." + field + " did not reach the power's modifier");
    }

    @ParameterizedTest(name = "{0}.{1} -> {2}")
    @CsvSource({
        "class_sentinel_thorns,      return_ratio, entity_action.amount_ratio, 0.75",
        "abyssal_thorns_aura,        return_ratio, entity_action.amount_ratio, 0.75",
        "stoneguard_thorns_aura,     return_ratio, entity_action.amount_ratio, 0.75",
        "class_paladin_beacon_regen, radius,       condition.radius,           12.0",
    })
    void nestedOverrideLandsInTheNestedObject(String power, String field, String path, double value)
            throws IOException {
        override(power, field, value);
        JsonObject json = shipped(power);
        PowerDataManager.Resolved resolved = PowerDataManager.resolvePowerType(id(power), json);
        assertEquals(value, at(resolved.json(), path).getAsDouble(), 1e-9,
            power + "." + field + " was not written to " + path);
        assertTrue(!resolved.json().has(field) || path.equals(field),
            power + "." + field + " was also written at the JSON root");
    }

    private void override(String power, String field, double value) {
        String key = "neoorigins:" + power;
        Map<String, ModConfigSpec.ConfigValue<?>> fields = PowerOverridesConfig.POWER_OVERRIDES.get(key);
        ModConfigSpec.ConfigValue<?> shipped = fields.get(field);
        assertNotNull(shipped, key + " has no override field " + field);
        swapped.putIfAbsent(key + "#" + field, shipped);
        @SuppressWarnings("unchecked")
        ModConfigSpec.ConfigValue<Object> cv = Mockito.mock(ModConfigSpec.ConfigValue.class);
        Object def = shipped.getDefault();
        Mockito.when(cv.get()).thenReturn(def instanceof Integer ? (Object) (int) value : (Object) value);
        Mockito.when(cv.getDefault()).thenReturn(def);
        fields.put(field, cv);
        RealDatapack.restoreShipped();
    }

    private static Identifier id(String power) {
        return Identifier.fromNamespaceAndPath("neoorigins", power);
    }

    private static JsonObject shipped(String power) throws IOException {
        Path file = RealDatapack.projectPath(
            "src/main/resources/data/neoorigins/origins/powers/" + power + ".json");
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(r).getAsJsonObject();
        }
    }

    private static JsonElement at(JsonObject json, String path) {
        JsonElement cur = json;
        for (String part : path.split("\\.")) {
            if (cur == null || !cur.isJsonObject()) return null;
            cur = cur.getAsJsonObject().get(part);
        }
        return cur;
    }
}
