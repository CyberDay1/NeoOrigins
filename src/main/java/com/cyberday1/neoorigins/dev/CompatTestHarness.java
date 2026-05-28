package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.compat.OriginsFormatDetector;
import com.cyberday1.neoorigins.compat.OriginsMultipleExpander;
import com.cyberday1.neoorigins.compat.OriginsPowerTranslator;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Headless compat validation harness. Loads origin pack power JSONs and runs
 * them through the Route A translator ({@link OriginsPowerTranslator}) and
 * structural validation for Route B types, reporting pass/fail/skip/warn
 * without launching Minecraft.
 *
 * <p>Invoke via {@code ./gradlew compatTest} or:
 * <pre>
 *   ./gradlew compatTest --args="/path/to/pack/data /path/to/another"
 * </pre>
 *
 * <p>Exit code 0 = all OK, 1 = failures detected, 2 = usage error.
 */
public final class CompatTestHarness {

    // Route B types — validated structurally (can't run the lambdas headlessly)
    private static final Set<String> ROUTE_B_TYPES = Set.of(
        "origins:active_self",           "apace:active_self",
        "origins:action_over_time",      "apace:action_over_time",
        "origins:action_on_callback",    "apace:action_on_callback",
        "origins:resource",              "apace:resource",
        "origins:toggle",                "apace:toggle",
        "origins:conditioned_attribute", "apace:conditioned_attribute",
        "origins:conditioned_status_effect", "apace:conditioned_status_effect",
        "origins:action_on_being_hit",   "apace:action_on_being_hit",
        "origins:self_action_when_hit",  "apace:self_action_when_hit",
        "origins:self_action_on_hit",    "apace:self_action_on_hit",
        "origins:action_on_hit",         "apace:action_on_hit",
        "origins:damage_over_time",      "apace:damage_over_time",
        "origins:action_on_kill",        "apace:action_on_kill",
        "origins:fire_projectile",       "apace:fire_projectile",
        "origins:target_action_on_hit",  "apace:target_action_on_hit",
        "origins:self_action_on_kill",   "apace:self_action_on_kill",
        "origins:launch",               "apace:launch",
        "origins:entity_glow",          "apace:entity_glow",
        "origins:self_glow",            "apace:self_glow",
        "origins:prevent_death",        "apace:prevent_death",
        "origins:action_when_hit",      "apace:action_when_hit",
        "origins:action_when_damage_taken", "apace:action_when_damage_taken",
        "origins:attacker_action_when_hit", "apace:attacker_action_when_hit",
        "origins:action_on_land",       "apace:action_on_land",
        "origins:prevent_item_use",     "apace:prevent_item_use",
        "origins:restrict_armor",       "apace:restrict_armor",
        "origins:prevent_sleep",        "apace:prevent_sleep",
        "origins:prevent_block_use",    "apace:prevent_block_use",
        "origins:prevent_entity_use",   "apace:prevent_entity_use",
        "origins:modify_food",          "apace:modify_food",
        "origins:modify_jump",          "apace:modify_jump",
        "origins:prevent_sprinting",    "apace:prevent_sprinting",
        "origins:modify_crafting",      "apace:modify_crafting",
        "origins:modify_lava_speed",    "apace:modify_lava_speed",
        "origins:modify_xp_gain",       "apace:modify_xp_gain",
        "origins:shaking",              "apace:shaking",
        "apoli:overlay",
        "origins:modify_status_effect_amplifier", "apace:modify_status_effect_amplifier",
        "origins:modify_falling",       "apace:modify_falling",
        "origins:conditioned_restrict_armor", "apace:conditioned_restrict_armor",
        "origins:freeze",               "apace:freeze",
        "origins:modify_harvest",       "apace:modify_harvest",
        "origins:recipe",               "apace:recipe",
        "origins:prevent_game_event",   "apace:prevent_game_event"
    );

    // Display-only types that produce no gameplay effect
    private static final Set<String> DISPLAY_ONLY = Set.of(
        "origins:tooltip", "apace:tooltip",
        "origins:simple", "apace:simple",
        "origins:cooldown", "apace:cooldown"
    );

    // Native NeoOrigins types — no translation needed
    private static final String NEO_PREFIX = "neoorigins:";

    private CompatTestHarness() {}

    // ── Results ─────────────────────────────────────────────────────────────

    enum Result { PASS, SKIP, FAIL, WARN }

    record Finding(Result result, String path, String type, String detail) {
        @Override public String toString() {
            return "[" + result + "] " + path + "  (" + type + ")" + (detail.isEmpty() ? "" : " — " + detail);
        }
    }

    // ── Entry point ─────────────────────────────────────────────────────────

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("usage: CompatTestHarness <dir> [<dir>...]");
            System.err.println("  Each <dir> is a datapack data/ root or a pack zip extraction root.");
            System.err.println("  The harness scans for **/powers/**/*.json files.");
            System.exit(2);
        }

        List<Finding> findings = new ArrayList<>();
        int totalScanned = 0;

        for (String arg : args) {
            Path root = Path.of(arg);
            if (!Files.isDirectory(root)) {
                System.err.println("WARN: not a directory, skipping: " + root);
                continue;
            }
            totalScanned += scanDirectory(root, findings);
        }

        // Tally
        int pass = 0, skip = 0, fail = 0, warn = 0;
        for (Finding f : findings) {
            switch (f.result) {
                case PASS -> pass++;
                case SKIP -> skip++;
                case FAIL -> fail++;
                case WARN -> warn++;
            }
        }

        // Report
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  COMPAT TEST RESULTS");
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.printf("  Scanned: %d files%n", totalScanned);
        System.out.printf("  PASS:    %d%n", pass);
        System.out.printf("  SKIP:    %d  (Route B — structure validated)%n", skip);
        System.out.printf("  WARN:    %d  (lossy translation — fields dropped)%n", warn);
        System.out.printf("  FAIL:    %d  (unsupported — power will be a no-op)%n", fail);
        System.out.println("═══════════════════════════════════════════════════════════════");

        if (warn > 0) {
            System.out.println();
            System.out.println("── WARNINGS ──────────────────────────────────────────────────");
            findings.stream().filter(f -> f.result == Result.WARN).forEach(System.out::println);
        }

        if (fail > 0) {
            System.out.println();
            System.out.println("── FAILURES ──────────────────────────────────────────────────");
            findings.stream().filter(f -> f.result == Result.FAIL).forEach(System.out::println);
        }

        System.out.println();
        System.exit(fail > 0 ? 1 : 0);
    }

    // ── Scanner ─────────────────────────────────────────────────────────────

    private static int scanDirectory(Path root, List<Finding> findings) throws IOException {
        int scanned = 0;
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (!p.toString().endsWith(".json")) continue;
                String normalized = p.toString().replace('\\', '/');
                if (!normalized.contains("/powers/")) continue;
                scanned++;

                JsonObject json;
                try {
                    JsonElement el = JsonParser.parseString(Files.readString(p));
                    if (!el.isJsonObject()) continue;
                    json = el.getAsJsonObject();
                } catch (Exception e) {
                    findings.add(new Finding(Result.FAIL, relative(root, p), "?", "JSON parse error: " + e.getMessage()));
                    continue;
                }

                String relPath = relative(root, p);
                Identifier id = inferPowerId(root, p);
                processJson(id, json, relPath, findings);
            }
        }
        return scanned;
    }

    private static void processJson(Identifier id, JsonObject json, String path, List<Finding> findings) {
        String type = OriginsFormatDetector.getType(json);
        if (type == null || type.isBlank()) {
            // No type field — might be a layer or origin file that snuck in
            return;
        }

        // Entity-action lint runs over every power JSON regardless of namespace,
        // because nested entity_action trees can reach into Apoli/Apace power
        // shapes (e.g. action_on_hit) just as much as into native ones.
        validateEntityActions(json, path, findings);

        // Native NeoOrigins types — structurally validated, then pass.
        // Previously these auto-PASSed; v2.1.6 added per-type structural hooks
        // (see validateNativeStructure) so authoring mistakes in native packs
        // surface as WARN here instead of silently no-op'ing at runtime.
        if (type.startsWith(NEO_PREFIX)) {
            String nativeIssue = validateNativeStructure(type, json);
            if (nativeIssue != null) {
                findings.add(new Finding(Result.WARN, path, type, "native struct: " + nativeIssue));
            } else {
                findings.add(new Finding(Result.PASS, path, type, "native type"));
            }
            return;
        }

        // origins:multiple — expand and recurse
        if (type.equals("origins:multiple") || type.equals("apace:multiple")) {
            try {
                Map<Identifier, JsonObject> expanded = OriginsMultipleExpander.expand(id, json);
                for (var entry : expanded.entrySet()) {
                    String subPath = path + "#" + entry.getKey().getPath();
                    processJson(entry.getKey(), entry.getValue(), subPath, findings);
                }
            } catch (Exception e) {
                findings.add(new Finding(Result.FAIL, path, type, "multiple expand error: " + e.getMessage()));
            }
            return;
        }

        // Display-only types — skip
        if (DISPLAY_ONLY.contains(type)) {
            findings.add(new Finding(Result.SKIP, path, type, "display-only"));
            return;
        }

        // Route B types — structural validation
        if (ROUTE_B_TYPES.contains(type)) {
            String structIssue = validateRouteBStructure(type, json);
            if (structIssue != null) {
                findings.add(new Finding(Result.WARN, path, type, "Route B struct: " + structIssue));
            } else {
                findings.add(new Finding(Result.SKIP, path, type, ""));
            }
            // Also check for known lossy patterns
            checkKnownLossyPatterns(path, type, json, findings);
            return;
        }

        // Route A — attempt translation
        try {
            Optional<JsonObject> result = OriginsPowerTranslator.translate(id, json);
            if (result.isPresent()) {
                // Check for lossy translation
                List<String> losses = detectFieldLoss(type, json, result.get());
                if (!losses.isEmpty()) {
                    for (String loss : losses) {
                        findings.add(new Finding(Result.WARN, path, type, loss));
                    }
                } else {
                    findings.add(new Finding(Result.PASS, path, type, ""));
                }
            } else {
                // Check if this is a Route B fallback (conditioned modify_damage with condition field)
                boolean routeBFallback = (type.contains("modify_damage_taken") || type.contains("modify_damage_dealt"))
                    && json.has("condition");
                if (routeBFallback) {
                    // Same parser-canonical structural check as the explicit Route B
                    // path. parseConditionedModifyDamage* accepts singular `modifier`
                    // or plural `modifiers`; missing both is a no-op power → WARN.
                    String structIssue = validateRouteBStructure(type, json);
                    if (structIssue != null) {
                        findings.add(new Finding(Result.WARN, path, type, "Route B struct: " + structIssue));
                    } else {
                        findings.add(new Finding(Result.SKIP, path, type, "Route B fallback (conditioned)"));
                    }
                } else {
                    findings.add(new Finding(Result.FAIL, path, type, "translator returned empty"));
                }
            }
        } catch (Exception e) {
            findings.add(new Finding(Result.FAIL, path, type, "translator threw: " + e.getClass().getSimpleName() + ": " + e.getMessage()));
        }
    }

    // ── Field Loss Detection ────────────────────────────────────────────────

    private static List<String> detectFieldLoss(String type, JsonObject input, JsonObject output) {
        List<String> losses = new ArrayList<>();

        // damage_condition dropped
        if (input.has("damage_condition") && !output.has("damage_type") && !output.has("damage_condition")) {
            losses.add("damage_condition dropped — modifier will apply to ALL damage types");
        }

        // block_condition dropped (phasing, prevent_sleep, etc.)
        if (input.has("block_condition") && !output.has("block_condition") && !output.has("blocked_blocks")) {
            losses.add("block_condition dropped — block filter lost");
        }

        // condition dropped (entity condition)
        if (input.has("condition") && !output.has("condition") && !output.has("power_condition")) {
            // Only warn if it's a complex condition, not a string shorthand
            JsonElement cond = input.get("condition");
            if (cond.isJsonObject()) {
                losses.add("entity condition dropped (Route A limitation)");
            }
        }

        // Multiple modifiers collapsed
        if (input.has("modifiers") && input.get("modifiers").isJsonArray()) {
            JsonArray mods = input.getAsJsonArray("modifiers");
            if (mods.size() > 1 && !output.has("modifiers")) {
                losses.add("multiple modifiers (" + mods.size() + ") collapsed to single");
            }
        }

        return losses;
    }

    // ── Known Lossy Pattern Checks ──────────────────────────────────────────

    private static void checkKnownLossyPatterns(String path, String type, JsonObject json, List<Finding> findings) {
        // restrict_armor / conditioned_restrict_armor with item conditions
        if (type.contains("restrict_armor")) {
            if (json.has("item_condition") || json.has("slot_condition")) {
                // Check for unsupported item condition types
                checkItemConditions(path, type, json, findings);
            }
        }

        // modify_food with item_condition
        if (type.contains("modify_food") && json.has("item_condition")) {
            checkItemConditions(path, type, json, findings);
        }

        // prevent_item_use with item_condition
        if (type.contains("prevent_item_use") && json.has("item_condition")) {
            checkItemConditions(path, type, json, findings);
        }

        // Any power with damage_condition
        if (json.has("damage_condition")) {
            JsonElement dc = json.get("damage_condition");
            if (dc.isJsonObject()) {
                String dcType = getNestedType(dc.getAsJsonObject());
                if (dcType != null && !dcType.equals("origins:name") && !dcType.equals("origins:type")) {
                    findings.add(new Finding(Result.WARN, path, type,
                        "damage_condition type '" + dcType + "' not fully supported — filter may be dropped"));
                }
            }
        }

        // entity condition with power_active using wildcards
        if (json.has("condition")) {
            checkConditionForWildcards(path, type, json.get("condition"), findings);
        }
    }

    private static void checkItemConditions(String path, String type, JsonObject json, List<Finding> findings) {
        JsonElement ic = json.has("item_condition") ? json.get("item_condition")
                       : json.has("slot_condition") ? json.get("slot_condition") : null;
        if (ic == null || !ic.isJsonObject()) return;
        String icType = getNestedType(ic.getAsJsonObject());
        if (icType != null) {
            Set<String> unsupported = Set.of("origins:armor_value", "origins:food", "origins:meat",
                "origins:harvest_level", "origins:enchantment", "origins:durability");
            if (unsupported.contains(icType)) {
                findings.add(new Finding(Result.WARN, path, type,
                    "item_condition type '" + icType + "' unsupported — will fall to alwaysTrue/alwaysFalse"));
            }
        }
    }

    private static void checkConditionForWildcards(String path, String type, JsonElement condition, List<Finding> findings) {
        if (!condition.isJsonObject()) return;
        JsonObject cond = condition.getAsJsonObject();
        String condType = getNestedType(cond);
        if ("origins:power_active".equals(condType) || "apace:power_active".equals(condType)) {
            if (cond.has("power")) {
                String powerId = cond.get("power").getAsString();
                if (powerId.contains("*")) {
                    findings.add(new Finding(Result.WARN, path, type,
                        "power_active uses wildcard '" + powerId + "' — Identifier.parse will throw, condition returns false"));
                }
            }
        }
        // Recurse into sub-conditions
        for (String key : List.of("condition", "conditions", "inverted")) {
            if (cond.has(key)) {
                JsonElement sub = cond.get(key);
                if (sub.isJsonObject()) {
                    checkConditionForWildcards(path, type, sub, findings);
                } else if (sub.isJsonArray()) {
                    for (JsonElement el : sub.getAsJsonArray()) {
                        checkConditionForWildcards(path, type, el, findings);
                    }
                }
            }
        }
    }

    // ── Route B Structural Validation ───────────────────────────────────────

    private static String validateRouteBStructure(String type, JsonObject json) {
        String base = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        return switch (base) {
            case "active_self" -> {
                if (!json.has("entity_action") || !json.get("entity_action").isJsonObject())
                    yield "missing 'entity_action' object";
                yield null;
            }
            case "action_over_time" -> {
                if (!json.has("entity_action") && !json.has("rising_action") && !json.has("falling_action"))
                    yield "missing action field (entity_action/rising_action/falling_action)";
                yield null;
            }
            case "resource" -> {
                if (!json.has("min") && !json.has("max"))
                    yield "missing 'min'/'max' fields";
                yield null;
            }
            case "toggle" -> null; // minimal requirements
            case "conditioned_attribute" -> {
                if (!json.has("modifier") && !json.has("modifiers"))
                    yield "missing 'modifier'/'modifiers'";
                yield null;
            }
            case "fire_projectile" -> {
                if (!json.has("entity_type")) yield "missing 'entity_type'";
                yield null;
            }
            case "recipe" -> {
                if (!json.has("recipe")) yield "missing 'recipe' field";
                yield null;
            }
            case "restrict_armor", "conditioned_restrict_armor" -> null;
            case "modify_food" -> null;
            case "modify_jump" -> {
                if (!json.has("modifier") && !json.has("modifiers"))
                    yield "missing 'modifier'/'modifiers'";
                yield null;
            }
            // modify_xp_gain / modify_lava_speed: parseNumericModifier accepts both
            // singular "modifier" and plural "modifiers", and per-entry either
            // "value" or "amount". Mirror the parser's tolerance here.
            case "modify_xp_gain", "modify_lava_speed" -> {
                if (!json.has("modifier") && !json.has("modifiers"))
                    yield "missing 'modifier'/'modifiers'";
                yield null;
            }
            // modify_damage_taken / modify_damage_dealt: parseConditionedModifyDamage*
            // routes through parseModifierList, so both singular "modifier" and plural
            // "modifiers" are accepted (and per-entry either "value" or "amount").
            // An absent modifier no longer hard-fails — the multiplier just stays 1.0
            // (effectively a no-op power), so flag missing-modifier as a WARN so authors
            // notice the dead power without breaking load.
            case "modify_damage_taken", "modify_damage_dealt" -> {
                if (!json.has("modifier") && !json.has("modifiers"))
                    yield "missing 'modifier'/'modifiers'";
                yield null;
            }
            default -> null; // Unknown Route B — can't validate
        };
    }

    // ── Entity-Action Lint (recursive) ──────────────────────────────────────

    /**
     * Walk the entire power JSON tree looking for entity-action objects (any
     * sub-object whose {@code type} matches an entity-action id we care about)
     * and apply parser-canonical structural checks. Currently covers
     * {@code neoorigins:spawn_entity} / {@code apace:spawn_entity}'s
     * {@code quantity} field (v2.1.6); future entity-action lints hook here.
     */
    private static void validateEntityActions(JsonElement el, String path, List<Finding> findings) {
        if (el == null) return;
        if (el.isJsonObject()) {
            JsonObject obj = el.getAsJsonObject();
            String t = getNestedType(obj);
            if (t != null) {
                if (t.equals("neoorigins:spawn_entity") || t.equals("apace:spawn_entity")
                    || t.equals("origins:spawn_entity") || t.equals("spawn_entity")) {
                    String issue = validateSpawnEntityQuantity(obj);
                    if (issue != null) {
                        findings.add(new Finding(Result.WARN, path, t, "spawn_entity: " + issue));
                    }
                }
            }
            for (var entry : obj.entrySet()) {
                validateEntityActions(entry.getValue(), path, findings);
            }
        } else if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) {
                validateEntityActions(child, path, findings);
            }
        }
    }

    private static String validateSpawnEntityQuantity(JsonObject obj) {
        if (!obj.has("quantity")) return null;
        JsonElement q = obj.get("quantity");
        if (!q.isJsonPrimitive() || !q.getAsJsonPrimitive().isNumber()) {
            return "'quantity' must be an integer (got " + q + ") - parser will clamp to 1";
        }
        // Reject non-integer numbers (1.5) and negatives/zero.
        try {
            double dv = q.getAsDouble();
            if (dv != Math.floor(dv)) {
                return "'quantity' must be an integer (got " + dv + ") - parser will truncate";
            }
            int iv = q.getAsInt();
            if (iv < 1) return "'quantity' must be >=1 (got " + iv + ") - parser will clamp to 1";
        } catch (NumberFormatException e) {
            return "'quantity' must be an integer (got " + q + ")";
        }
        return null;
    }

    // ── Native Structural Validation ────────────────────────────────────────

    /**
     * Structural lint for {@code neoorigins:*} types. Returns a short reason
     * string when a load-bearing field is missing/malformed, or {@code null}
     * when the JSON is plausibly grantable. Mirrors the parser-canonical
     * tolerance the runtime applies (the runtime logs a WARN and skips —
     * we surface the same WARN at scan time so authoring mistakes don't
     * silently no-op).
     */
    private static String validateNativeStructure(String type, JsonObject json) {
        String base = type.contains(":") ? type.substring(type.indexOf(':') + 1) : type;
        return switch (base) {
            case "starting_equipment" -> {
                boolean hasItem = json.has("item")
                    && json.get("item").isJsonPrimitive()
                    && !json.get("item").getAsString().isBlank();
                boolean hasStacks = json.has("stacks")
                    && json.get("stacks").isJsonArray()
                    && json.getAsJsonArray("stacks").size() > 0;
                if (!hasItem && !hasStacks) {
                    yield "missing 'item' or non-empty 'stacks'";
                }
                yield null;
            }
            case "loot_pool_grant" -> {
                // Mirrors LootPoolGrantPower.execute(): missing/blank loot_table
                // -> runtime WARN + skipped grant. Non-positive `rolls` (when set
                // explicitly) collapses the effective roll count to bonus_rolls
                // only — if both are unset/zero the power is a no-op.
                boolean hasTable = json.has("loot_table")
                    && json.get("loot_table").isJsonPrimitive()
                    && !json.get("loot_table").getAsString().isBlank();
                if (!hasTable) {
                    yield "missing 'loot_table'";
                }
                if (json.has("rolls") && json.get("rolls").isJsonPrimitive()
                        && json.get("rolls").getAsJsonPrimitive().isNumber()) {
                    int rolls = json.get("rolls").getAsInt();
                    int bonus = json.has("bonus_rolls")
                            && json.get("bonus_rolls").isJsonPrimitive()
                            && json.get("bonus_rolls").getAsJsonPrimitive().isNumber()
                        ? json.get("bonus_rolls").getAsInt() : 0;
                    if (rolls <= 0 && bonus <= 0) {
                        yield "'rolls' must be positive (got " + rolls
                            + ") - power will be a no-op";
                    }
                }
                yield null;
            }
            default -> null;
        };
    }

    // ── Utilities ───────────────────────────────────────────────────────────

    private static String getNestedType(JsonObject obj) {
        if (obj.has("type") && obj.get("type").isJsonPrimitive()) {
            return obj.get("type").getAsString();
        }
        return null;
    }

    private static Identifier inferPowerId(Path root, Path file) {
        // Attempt to derive namespace/path from directory structure
        // e.g., root/namespace/powers/folder/file.json → namespace:folder/file
        Path rel = root.relativize(file);
        String relStr = rel.toString().replace('\\', '/');

        // Try to find "data/<namespace>/powers/<path>" pattern
        int dataIdx = relStr.indexOf("data/");
        if (dataIdx >= 0) {
            String afterData = relStr.substring(dataIdx + 5);
            int slashIdx = afterData.indexOf('/');
            if (slashIdx > 0) {
                String namespace = afterData.substring(0, slashIdx);
                int powersIdx = afterData.indexOf("/powers/");
                if (powersIdx > 0) {
                    String path = afterData.substring(powersIdx + 8).replace(".json", "");
                    return Identifier.fromNamespaceAndPath(namespace, path);
                }
            }
        }

        // Fallback: look for <namespace>/powers/<path>
        String[] parts = relStr.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("powers".equals(parts[i]) && i > 0) {
                String namespace = parts[i - 1];
                StringBuilder path = new StringBuilder();
                for (int j = i + 1; j < parts.length; j++) {
                    if (path.length() > 0) path.append('/');
                    path.append(parts[j]);
                }
                String pathStr = path.toString().replace(".json", "");
                return Identifier.fromNamespaceAndPath(namespace, pathStr);
            }
        }

        // Last resort
        String filename = file.getFileName().toString().replace(".json", "");
        return Identifier.fromNamespaceAndPath("unknown", filename);
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
