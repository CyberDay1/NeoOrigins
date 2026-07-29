package com.cyberday1.neoorigins.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Pure-data guard against the "two live sources of one status effect" bug class.
 *
 * <p>Issues #111/#112 were a {@code persistent_effect} "stomp": two powers both
 * owned {@code minecraft:<effect>} and fought over its duration/amplifier, so
 * whichever ran last each tick won. The same shape appears silently when an
 * evolution tier {@code add}s an apex power granting an effect the base kit
 * already grants and the tier's {@code remove} list forgets to tear the base
 * power down.
 *
 * <p>This test walks the shipped origin JSONs, resolves the live power set at
 * every tier (base {@code powers} plus cumulative {@code tier_powers}
 * add/remove, mirroring {@code Origin#powersForTier}), works out which
 * {@code minecraft:} effects each live power grants, and fails when one effect
 * has two live sources <em>and at least one of them is unconditional</em>.
 *
 * <p>The "at least one unconditional" clause is the whole point. An
 * always-on source makes any second source either redundant or a duration
 * stomp. Two purely <em>conditional</em> sources with different triggers are
 * legitimate design — e.g. piglin's in-the-nether strength buff and its
 * below-30%-HP brute rage — and must pass.
 *
 * <p>Attributes are deliberately out of scope: stoneguard's tier-2 knockback
 * resistance overlaps an attribute, not a status effect, and does not trip
 * this test.
 */
class OriginTierEffectOverlapTest {

    private static final String NS = "neoorigins:";

    /**
     * Native power types that grant a fixed effect with no effect id in their
     * JSON body. These are rewritten into {@code neoorigins:persistent_effect}
     * at load time by {@code LegacyPowerTypeAliases}; the map mirrors the
     * {@code writeSingleEffect} calls there.
     */
    private static final Map<String, String> NATIVE_EFFECT_TYPES = Map.of(
        NS + "night_vision", "minecraft:night_vision",
        NS + "glow", "minecraft:glowing",
        NS + "water_breathing", "minecraft:water_breathing"
    );

    /**
     * Native types from {@link #NATIVE_EFFECT_TYPES} that are always-on. The
     * alias for {@code water_breathing} injects a {@code neoorigins:in_water}
     * condition, so it is a conditional source; the others are not gated.
     */
    private static final Set<String> UNCONDITIONAL_NATIVE_TYPES = Set.of(
        NS + "night_vision", NS + "glow"
    );

    /**
     * Keys whose sub-trees describe a <em>test</em>, not a grant. An effect id
     * mentioned under one of these ("does the player have regeneration?") is
     * not a source of that effect and must not be collected.
     */
    private static final Set<String> CONDITION_KEYS = Set.of(
        "condition", "conditions", "entity_condition", "bientity_condition",
        "item_condition", "block_condition", "target_condition", "biome_condition",
        "fluid_condition", "damage_condition", "held_item_condition",
        "source_condition", "attacker_condition", "predicate"
    );

    /** Power/action types that name an effect they strip or block rather than grant. */
    private static final Set<String> NON_GRANTING_TYPES = Set.of(
        NS + "effect_immunity", NS + "remove_effect", NS + "clear_effect",
        NS + "effect_condition", NS + "status_effect_condition"
    );

    /** One live power that grants at least one effect at a given tier. */
    private record Source(String powerId, String powerType, Set<String> effects, boolean unconditional) {}

    // ── The guard ───────────────────────────────────────────────────────

    @Test
    void noEffectHasTwoLiveSourcesWhenOneIsUnconditional() {
        Map<String, JsonObject> powers = loadPowers();
        List<String> failures = new ArrayList<>();

        for (Map.Entry<String, JsonObject> originEntry : loadOrigins().entrySet()) {
            String originFile = originEntry.getKey();
            JsonObject origin = originEntry.getValue();

            for (Map.Entry<Integer, Set<String>> tierEntry : livePowersByTier(origin).entrySet()) {
                int tier = tierEntry.getKey();

                Map<String, List<Source>> byEffect = new LinkedHashMap<>();
                for (String powerId : tierEntry.getValue()) {
                    Source src = describe(powerId, powers.get(powerId));
                    if (src == null) continue;
                    for (String effect : src.effects()) {
                        byEffect.computeIfAbsent(effect, k -> new ArrayList<>()).add(src);
                    }
                }

                for (Map.Entry<String, List<Source>> e : byEffect.entrySet()) {
                    List<Source> sources = e.getValue();
                    if (sources.size() < 2) continue;
                    if (sources.stream().noneMatch(Source::unconditional)) continue; // piglin case: legit

                    StringBuilder sb = new StringBuilder();
                    sb.append(originFile).append(" tier ").append(tier)
                      .append(": '").append(e.getKey())
                      .append("' is granted by ").append(sources.size())
                      .append(" live powers, at least one unconditional — ");
                    for (int i = 0; i < sources.size(); i++) {
                        Source s = sources.get(i);
                        if (i > 0) sb.append(" AND ");
                        sb.append(s.powerId()).append(" (type ").append(s.powerType())
                          .append(", ").append(s.unconditional() ? "unconditional" : "conditional").append(')');
                    }
                    sb.append(". Add the superseded power to tier ").append(tier)
                      .append("'s \"remove\" list.");
                    failures.add(sb.toString());
                }
            }
        }

        if (!failures.isEmpty()) {
            fail("Duplicate status-effect sources on live power sets (#111/#112 stomp class):\n  "
                + String.join("\n  ", failures));
        }
    }

    // ── Task 3: the caveborn tier-2 night-vision swap, as far as data goes ──

    /**
     * Caveborn's tier-2 overlay swaps {@code caveborn_night_vision} (type
     * {@code neoorigins:night_vision}) for {@code caveborn_ascended_night_vision}
     * (a {@code persistent_effect}). Both resolve to the same
     * {@code minecraft:night_vision} at amplifier 0 once the legacy alias runs,
     * so if the swap ever regressed the player would carry two owners of one
     * effect. Pin it: exactly one night-vision source at every caveborn tier.
     */
    @Test
    void caveborn_hasExactlyOneNightVisionSourceAtEveryTier() {
        Map<String, JsonObject> powers = loadPowers();
        JsonObject caveborn = loadOrigins().get("caveborn.json");
        assertTrue(caveborn != null, "caveborn.json must exist");

        Map<Integer, Set<String>> byTier = livePowersByTier(caveborn);
        assertTrue(byTier.containsKey(2), "caveborn must define a tier-2 overlay");

        for (Map.Entry<Integer, Set<String>> tierEntry : byTier.entrySet()) {
            List<String> nightVisionSources = new ArrayList<>();
            for (String powerId : tierEntry.getValue()) {
                Source src = describe(powerId, powers.get(powerId));
                if (src != null && src.effects().contains("minecraft:night_vision")) {
                    nightVisionSources.add(powerId);
                }
            }
            assertEquals(1, nightVisionSources.size(),
                "caveborn tier " + tierEntry.getKey()
                    + " must have exactly one minecraft:night_vision source, found "
                    + nightVisionSources);
        }

        // The swap itself: base power gone at tier 2, ascended power live.
        Set<String> tier2 = byTier.get(2);
        assertFalse(tier2.contains(NS + "caveborn_night_vision"),
            "caveborn tier 2 must remove the base night-vision power");
        assertTrue(tier2.contains(NS + "caveborn_ascended_night_vision"),
            "caveborn tier 2 must add the ascended night-vision power");
    }

    // ── Model ───────────────────────────────────────────────────────────

    /** Live power ids per tier, cumulative, mirroring {@code Origin#powersForTier}. */
    private static Map<Integer, Set<String>> livePowersByTier(JsonObject origin) {
        List<String> base = stringList(origin.get("powers"));
        List<JsonObject> overlays = new ArrayList<>();
        if (origin.get("tier_powers") instanceof JsonArray arr) {
            for (JsonElement el : arr) {
                if (el.isJsonObject()) overlays.add(el.getAsJsonObject());
            }
        }
        int maxTier = 0;
        for (JsonObject ov : overlays) {
            maxTier = Math.max(maxTier, ov.has("tier") ? ov.get("tier").getAsInt() : 0);
        }

        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        for (int tier = 0; tier <= maxTier; tier++) {
            Set<String> live = new LinkedHashSet<>(base);
            for (JsonObject ov : overlays) {
                int ovTier = ov.has("tier") ? ov.get("tier").getAsInt() : 0;
                if (ovTier > tier) continue;
                stringList(ov.get("remove")).forEach(live::remove);
                live.addAll(stringList(ov.get("add")));
            }
            out.put(tier, live);
        }
        return out;
    }

    /** Null when the power is unknown or grants no {@code minecraft:} effect. */
    private static Source describe(String powerId, JsonObject power) {
        if (power == null) return null;
        String type = power.has("type") && power.get("type").isJsonPrimitive()
            ? power.get("type").getAsString() : "";

        Set<String> effects = new LinkedHashSet<>();
        boolean unconditional;
        if (NATIVE_EFFECT_TYPES.containsKey(type)) {
            effects.add(NATIVE_EFFECT_TYPES.get(type));
            unconditional = UNCONDITIONAL_NATIVE_TYPES.contains(type);
        } else {
            collectEffects(power, effects);
            // persistent_effect is always-on unless the power itself carries a
            // top-level condition. Toggleable or not is irrelevant: a toggle is
            // player choice, not a gameplay gate, and the default state is on.
            unconditional = (NS + "persistent_effect").equals(type) && !power.has("condition");
        }
        return effects.isEmpty() ? null : new Source(powerId, type, effects, unconditional);
    }

    /**
     * Recursively collects every {@code minecraft:} effect id the node grants.
     * Handles the single {@code "effect"} form, the {@code "effects": [...]}
     * list form (persistent_effect, low_hp_threshold), and nested
     * {@code apply_effect} actions wherever they sit (entity_action, if_action,
     * else_action, actions lists, ...). Condition sub-trees are skipped — an
     * effect named there is being tested for, not granted.
     */
    private static void collectEffects(JsonElement node, Set<String> out) {
        if (node == null || node.isJsonNull()) return;
        if (node.isJsonArray()) {
            for (JsonElement el : node.getAsJsonArray()) collectEffects(el, out);
            return;
        }
        if (!node.isJsonObject()) return;
        JsonObject obj = node.getAsJsonObject();

        if (obj.has("type") && obj.get("type").isJsonPrimitive()
            && NON_GRANTING_TYPES.contains(obj.get("type").getAsString())) {
            return;
        }

        addIfEffectId(obj.get("effect"), out);
        if (obj.get("effects") instanceof JsonArray effects) {
            for (JsonElement el : effects) {
                if (el.isJsonObject()) addIfEffectId(el.getAsJsonObject().get("effect"), out);
                else addIfEffectId(el, out);
            }
        }

        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (CONDITION_KEYS.contains(e.getKey())) continue;
            if ("effect".equals(e.getKey()) || "effects".equals(e.getKey())) continue;
            collectEffects(e.getValue(), out);
        }
    }

    private static void addIfEffectId(JsonElement el, Set<String> out) {
        if (el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString()) {
            String id = el.getAsString();
            if (id.startsWith("minecraft:")) out.add(id);
        }
    }

    private static List<String> stringList(JsonElement el) {
        List<String> out = new ArrayList<>();
        if (el instanceof JsonArray arr) {
            for (JsonElement e : arr) {
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString()) out.add(e.getAsString());
            }
        }
        return out;
    }

    // ── Resource loading ────────────────────────────────────────────────

    /** File name → parsed JSON, for {@code data/neoorigins/origins/origins}. */
    private static Map<String, JsonObject> loadOrigins() {
        Map<String, JsonObject> out = new LinkedHashMap<>();
        forEachJson(dataRoot().resolve("origins"), (name, json) -> out.put(name, json));
        assertFalse(out.isEmpty(), "no origin JSONs found under " + dataRoot().resolve("origins"));
        return out;
    }

    /** {@code neoorigins:<name>} → parsed JSON, for {@code data/neoorigins/origins/powers}. */
    private static Map<String, JsonObject> loadPowers() {
        Map<String, JsonObject> out = new HashMap<>();
        forEachJson(dataRoot().resolve("powers"),
            (name, json) -> out.put(NS + name.substring(0, name.length() - ".json".length()), json));
        assertFalse(out.isEmpty(), "no power JSONs found under " + dataRoot().resolve("powers"));
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
                         if (el.isJsonObject()) sink.accept(p.getFileName().toString(), el.getAsJsonObject());
                     } catch (IOException io) {
                         throw new UncheckedIOException(io);
                     } catch (RuntimeException ex) {
                         fail("Malformed JSON at " + p + ": " + ex.getMessage());
                     }
                 });
        } catch (IOException io) {
            throw new UncheckedIOException(io);
        }
    }

    /**
     * Locates {@code src/main/resources/data/neoorigins/origins} from the test
     * working directory, walking up so the test survives a run/ or worktree cwd.
     */
    private static Path dataRoot() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParent()) {
            Path candidate = dir.resolve("src/main/resources/data/neoorigins/origins");
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException(
            "could not locate src/main/resources/data/neoorigins/origins from "
                + System.getProperty("user.dir"));
    }
}
