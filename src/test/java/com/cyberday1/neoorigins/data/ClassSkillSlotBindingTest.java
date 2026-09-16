package com.cyberday1.neoorigins.data;

import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.power.builtin.ActionOnEventPower;
import com.cyberday1.neoorigins.power.builtin.ActiveAbilityPower;
import com.cyberday1.neoorigins.power.builtin.AttributeModifierPower;
import com.cyberday1.neoorigins.power.builtin.ConditionPassivePower;
import com.cyberday1.neoorigins.power.builtin.ModifyDamagePower;
import com.cyberday1.neoorigins.power.builtin.PersistentEffectPower;
import com.cyberday1.neoorigins.power.builtin.PreventActionPower;
import com.cyberday1.neoorigins.power.builtin.SneakyPower;
import com.cyberday1.neoorigins.power.builtin.StartingEquipmentPower;
import com.cyberday1.neoorigins.power.builtin.StealthPower;
import com.cyberday1.neoorigins.power.builtin.TogglePower;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins which class power actually reaches the Class Skill key.
 *
 * <p>A class gets exactly one keybind slot: {@code NeoOriginsNetwork} reads
 * {@code classActives.get(0)} in both the activate handler and the HUD slot
 * builder, and {@code ActiveOriginService} fills that list in the origin's
 * declared {@code powers} order. The first slot-occupying power in the JSON
 * wins and every later one is silently unbound.
 *
 * <p>The Rogue shipped with {@code class_rogue_stealth} ahead of
 * {@code step_assist_switch}, which left Step Assist unreachable. Reordering
 * makes the switch reachable and orphans Stealth instead. That trade is
 * deliberate, and the first test below states it so a later reorder cannot
 * swap them back unnoticed.
 */
// hub: neoorigins/class-skill-slot.md
class ClassSkillSlotBindingTest {

    private static final String NS = "neoorigins:";

    /**
     * Real power-type instances for every {@code type} the class layer uses.
     * Activeness is answered by production code; this map only resolves a JSON
     * type id to the class that answers it. {@link #everyClassPowerTypeIsClassified()}
     * fails when a class JSON grows a type missing here, so it cannot go stale.
     */
    private static final Map<String, PowerType<?>> CLASS_LAYER_TYPES = Map.ofEntries(
        Map.entry(NS + "sneaky", new SneakyPower()),
        Map.entry(NS + "stealth", new StealthPower()),
        Map.entry(NS + "toggle", new TogglePower()),
        Map.entry(NS + "active_ability", new ActiveAbilityPower()),
        Map.entry(NS + "modify_damage", new ModifyDamagePower()),
        Map.entry(NS + "attribute_modifier", new AttributeModifierPower()),
        Map.entry(NS + "starting_equipment", new StartingEquipmentPower()),
        Map.entry(NS + "action_on_event", new ActionOnEventPower()),
        Map.entry(NS + "prevent_action", new PreventActionPower()),
        Map.entry(NS + "condition_passive", new ConditionPassivePower()),
        Map.entry(NS + "night_vision", new PersistentEffectPower()));

    /**
     * Types whose {@code isActivePower(Config)} reads {@code config.toggleable()},
     * so the no-arg form is the inherited {@code false} and answers nothing. For
     * these the JSON's own {@code toggleable} field is the production formula.
     *
     * <p>{@code neoorigins:night_vision} is rewritten to {@code persistent_effect}
     * by {@code LegacyPowerTypeAliases}, which forces {@code toggleable:false},
     * so an absent field is the real answer.
     */
    private static final List<String> TOGGLEABLE_DRIVEN =
        List.of(NS + "condition_passive", NS + "night_vision");

    /**
     * The classes this test reasons about: the three that carry Step Assist.
     * The other eighteen are out of scope, so {@link #CLASS_LAYER_TYPES} covers
     * only the types these three use and stays small.
     */
    private static final List<String> STEP_ASSIST_CLASSES =
        List.of("class_rogue.json", "class_explorer.json", "class_scout.json");

    // -- The reorder ----------------------------------------------------

    @Test
    void theRoguesOneSlotGoesToStepAssistAndOrphansStealth() {
        List<String> actives = slotOccupyingPowersOf("class_rogue.json");

        assertEquals(List.of(NS + "step_assist_switch", NS + "class_rogue_stealth"), actives,
            "the Rogue has two slot-occupying powers and only the first is bound; "
                + "step_assist_switch must lead or Step Assist is dead again");
        assertEquals(List.of(NS + "class_rogue_stealth"), actives.subList(1, actives.size()),
            "Stealth is the accepted cost of the reorder: unbound, not removed");
    }

    /** The other two Step Assist classes never had a competitor; keep it that way. */
    @Test
    void explorerAndScoutBindStepAssistWithNothingOrphaned() {
        for (String file : List.of("class_explorer.json", "class_scout.json")) {
            assertEquals(List.of(NS + "step_assist_switch"), slotOccupyingPowersOf(file),
                file + " must bind step_assist_switch and orphan nothing");
        }
    }

    // -- Why exactly those powers compete -------------------------------

    /**
     * The hidden {@code step_assist_toggle} sits ahead of the switch in all three
     * classes. It stays harmless only while {@link TogglePower} is inactive: were
     * it to occupy a slot it would swallow the Class Skill key and leave the
     * switch unbound, which is the original bug in a new place.
     */
    @Test
    void theHiddenStateTogglePowerNeverTakesTheSlot() {
        assertFalse(new TogglePower().isActivePower(),
            "step_assist_toggle precedes the switch in every class list; "
                + "if it ever occupies a slot it steals the Class Skill key");
        assertFalse(new SneakyPower().isActivePower(),
            "sneaky is passive and leads the Rogue list");
    }

    @Test
    void bothRogueCandidatesReallyOccupyTheSlot() {
        assertTrue(new ActiveAbilityPower().isActivePower(), "step_assist_switch");
        assertTrue(new StealthPower().isActivePower(), "class_rogue_stealth");
    }

    // -- Anti-drift guards ----------------------------------------------

    @Test
    void everyClassPowerTypeIsClassified() {
        Map<String, JsonObject> classes = loadClassOrigins();
        List<String> unknown = new ArrayList<>();
        for (String file : STEP_ASSIST_CLASSES) {
            JsonObject origin = classes.get(file);
            assertNotNull(origin, "no such class origin: " + file);
            for (String powerId : powerList(origin)) {
                String type = typeOf(powerId);
                if (!CLASS_LAYER_TYPES.containsKey(type)) {
                    unknown.add(file + " -> " + powerId + " (" + type + ")");
                }
            }
        }
        assertTrue(unknown.isEmpty(),
            "a Step Assist class grew a power type this test cannot classify, so the "
                + "slot it reports may be wrong. Add them to CLASS_LAYER_TYPES: " + unknown);
    }

    /**
     * A type outside {@link #TOGGLEABLE_DRIVEN} must not override
     * {@code isActivePower(Config)}, or the no-arg form used here is not the
     * production answer and the slot this test computes is fiction.
     */
    @Test
    void onlyTheToggleableDrivenTypesAreConfigDependent() {
        List<String> surprises = new ArrayList<>();
        for (Map.Entry<String, PowerType<?>> e : CLASS_LAYER_TYPES.entrySet()) {
            if (TOGGLEABLE_DRIVEN.contains(e.getKey())) continue;
            for (Method m : e.getValue().getClass().getDeclaredMethods()) {
                if (m.getName().equals("isActivePower") && m.getParameterCount() == 1) {
                    surprises.add(e.getKey());
                }
            }
        }
        assertTrue(surprises.isEmpty(),
            "these types decide activeness from their config, so the no-arg reading "
                + "used here is wrong for them: " + surprises);
    }

    // -- Model of the one-slot rule -------------------------------------

    /**
     * Mirrors {@code ActiveOriginService.getOrBuild}: declared order, keeping only
     * powers whose type occupies a hotkey slot. Built-in classes register no named
     * keybinds, so the {@code isNativeHotkeyPower} exclusion cannot fire here.
     */
    private static List<String> slotOccupyingPowersOf(String originFile) {
        JsonObject origin = loadClassOrigins().get(originFile);
        assertNotNull(origin, "no such class origin: " + originFile);
        List<String> out = new ArrayList<>();
        for (String powerId : powerList(origin)) {
            if (occupiesSlot(powerId)) out.add(powerId);
        }
        return out;
    }

    private static boolean occupiesSlot(String powerId) {
        String type = typeOf(powerId);
        PowerType<?> impl = CLASS_LAYER_TYPES.get(type);
        assertNotNull(impl, "unclassified power type " + type + " on " + powerId);
        if (TOGGLEABLE_DRIVEN.contains(type)) {
            JsonObject json = loadPowers().get(powerId);
            return json.has("toggleable") && json.get("toggleable").getAsBoolean();
        }
        return impl.isActivePower();
    }

    private static String typeOf(String powerId) {
        JsonObject json = loadPowers().get(powerId);
        assertNotNull(json, "class list references a missing power: " + powerId);
        return json.get("type").getAsString();
    }

    private static List<String> powerList(JsonObject origin) {
        List<String> out = new ArrayList<>();
        origin.getAsJsonArray("powers").forEach(e -> out.add(e.getAsString()));
        return out;
    }

    // -- Resource loading -----------------------------------------------

    private static Map<String, JsonObject> loadClassOrigins() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        forEachJson(dataRoot().resolve("origins"), (name, json) -> {
            if (name.startsWith("class_") && json.has("powers")) out.put(name, json);
        });
        assertFalse(out.isEmpty(), "no class_*.json origins found");
        return out;
    }

    private static Map<String, JsonObject> loadPowers() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        forEachJson(dataRoot().resolve("powers"),
            (name, json) -> out.put(NS + name.substring(0, name.length() - ".json".length()), json));
        assertFalse(out.isEmpty(), "no power JSONs found");
        return out;
    }

    private interface JsonSink { void accept(String fileName, JsonObject json); }

    private static void forEachJson(Path dir, JsonSink sink) {
        try (Stream<Path> files = Files.walk(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json"))
                 .sorted()
                 .forEach(p -> {
                     try {
                         JsonElement el = JsonParser.parseString(
                             Files.readString(p, StandardCharsets.UTF_8));
                         if (el.isJsonObject()) {
                             sink.accept(p.getFileName().toString(), el.getAsJsonObject());
                         }
                     } catch (IOException io) {
                         throw new UncheckedIOException(io);
                     }
                 });
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    private static Path dataRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources/data/neoorigins/origins");
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("could not locate the origins data root");
    }
}
