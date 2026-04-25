package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Pre-release validator for the 2.0 alias layer.
 *
 * <p>Scans every {@code data/*&#47;powers/*&#47;*.json} file under a root
 * directory, applies {@link LegacyPowerTypeAliases#simulateApply} to any file
 * whose declared {@code type} matches a registered alias, and verifies the
 * remapped JSON has the structural fields the target generic type expects.
 * Reports failures with path, old type, new type, and the failing invariant.
 *
 * <p>Does NOT round-trip through the real codecs — those require a registry
 * environment the smoke test can't cheaply stand up. It exists to catch
 * remap-lambda bugs (missing field writes, wrong field names) so a runtime
 * Phase 8 playtest gets the easy parts pre-validated.
 *
 * <p>Invoke via {@code ./gradlew smokeTestAliases}.
 */
public final class PowerAliasSmokeTest {

    private static final JsonParser PARSER = new JsonParser();

    private PowerAliasSmokeTest() {}

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: PowerAliasSmokeTest <root-dir>  (e.g. src/main/resources/data)");
            System.exit(2);
        }
        LegacyPowerTypeAliases.bootstrap();

        Path root = Path.of(args[0]);
        if (!Files.isDirectory(root)) {
            System.err.println("not a directory: " + root);
            System.exit(2);
        }

        int scanned = 0, aliased = 0;
        List<String> failures = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.toString().endsWith(".json")) continue;
                if (!p.toString().replace('\\', '/').contains("/powers/")) continue;
                scanned++;

                JsonObject json;
                try {
                    JsonElement el = PARSER.parse(Files.readString(p));
                    if (!el.isJsonObject()) continue;
                    json = el.getAsJsonObject();
                } catch (Exception e) {
                    failures.add(p + " :: cannot parse JSON: " + e.getMessage());
                    continue;
                }
                if (!json.has("type")) continue;
                Identifier typeId;
                try {
                    typeId = Identifier.parse(json.get("type").getAsString());
                } catch (Exception e) {
                    continue;
                }

                Identifier powerId = Identifier.fromNamespaceAndPath(
                    "neoorigins-test", p.getFileName().toString().replace(".json", ""));
                Identifier remapped;
                try {
                    remapped = LegacyPowerTypeAliases.simulateApply(typeId, json, powerId);
                } catch (Exception e) {
                    failures.add(p + " :: " + typeId + " remap threw: " + e);
                    continue;
                }
                if (remapped.equals(typeId)) continue;     // not an aliased type
                aliased++;

                String reason = validate(remapped, json);
                if (reason != null) {
                    failures.add(p + " :: " + typeId + " -> " + remapped + " :: " + reason);
                }
            }
        }

        System.out.println("[alias-smoke] scanned " + scanned + " files, "
                         + aliased + " matched an alias, "
                         + failures.size() + " failures");
        for (String f : failures) System.out.println("[alias-smoke] FAIL  " + f);
        if (!failures.isEmpty()) System.exit(1);
    }

    /**
     * Structural check for the remapped JSON. Returns null on success, a
     * human-readable reason string on failure.
     */
    private static String validate(Identifier targetType, JsonObject json) {
        String t = targetType.toString();
        return switch (t) {
            case "neoorigins:action_on_event" -> validateActionOnEvent(json);
            case "neoorigins:persistent_effect" -> validatePersistentEffect(json);
            case "neoorigins:condition_passive" -> validateConditionPassive(json);
            case "neoorigins:active_ability" -> validateActiveAbility(json);
            case "neoorigins:attribute_modifier" -> validateAttributeModifier(json);
            default -> null;   // unknown target — skip rather than false-positive
        };
    }

    private static String validateActionOnEvent(JsonObject json) {
        if (!json.has("event")) return "missing 'event' field";
        boolean hasAction = json.has("entity_action") && json.get("entity_action").isJsonObject();
        boolean hasModifier = json.has("modifier") && json.get("modifier").isJsonObject();
        if (!hasAction && !hasModifier) return "missing both 'entity_action' and 'modifier'";
        return null;
    }

    private static String validatePersistentEffect(JsonObject json) {
        boolean hasArray = json.has("effects") && json.get("effects").isJsonArray();
        boolean hasTopLevel = json.has("effect") || json.has("id");
        if (!hasArray && !hasTopLevel) return "missing both 'effects' array and top-level 'effect'";
        return null;
    }

    private static String validateConditionPassive(JsonObject json) {
        if (!json.has("condition") || !json.get("condition").isJsonObject())
            return "missing 'condition' object";
        if (!json.has("entity_action") || !json.get("entity_action").isJsonObject())
            return "missing 'entity_action' object";
        return null;
    }

    private static String validateActiveAbility(JsonObject json) {
        if (!json.has("entity_action") || !json.get("entity_action").isJsonObject())
            return "missing 'entity_action' object";
        return null;
    }

    private static String validateAttributeModifier(JsonObject json) {
        if (!json.has("attribute")) return "missing 'attribute' field";
        if (!json.has("amount")) return "missing 'amount' field";
        return null;
    }
}
