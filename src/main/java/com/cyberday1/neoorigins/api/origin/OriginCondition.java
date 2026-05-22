package com.cyberday1.neoorigins.api.origin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Condition that gates origin visibility in the picker based on previous
 * layer choices. Evaluated purely against the player's chosen-origins map
 * (no entity dependency), so it works identically on client and server.
 *
 * <p>Parsed from the {@code "condition"} field of conditioned origin entries
 * in layer JSON. Supports three condition types:
 * <ul>
 *   <li>{@code origins:origin} — checks if a specific origin was chosen in a specific layer</li>
 *   <li>{@code origins:and} — all sub-conditions must pass</li>
 *   <li>{@code origins:or} — any sub-condition must pass</li>
 * </ul>
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "type": "origins:origin",
 *   "layer": "origins:origin",
 *   "origin": "mypack:elf",
 *   "inverted": false
 * }
 * }</pre>
 */
public sealed interface OriginCondition {

    /**
     * Evaluate this condition against the player's current layer→origin map.
     *
     * @param chosenOrigins map of layer ID → chosen origin ID
     * @return true if the condition is satisfied
     */
    boolean test(Map<Identifier, Identifier> chosenOrigins);

    /**
     * Parse an OriginCondition from a JSON object. Returns null if the
     * condition type is unrecognized (caller should treat as always-false
     * or log a warning).
     */
    static OriginCondition parse(JsonObject json) {
        if (json == null) return null;
        String type = json.has("type") ? json.get("type").getAsString() : "";

        return switch (type) {
            case "origins:origin", "apace:origin", "neoorigins:origin" -> {
                Identifier layer = json.has("layer")
                    ? Identifier.parse(json.get("layer").getAsString())
                    : Identifier.fromNamespaceAndPath("neoorigins", "origin");
                Identifier origin = json.has("origin")
                    ? Identifier.parse(json.get("origin").getAsString())
                    : null;
                if (origin == null) yield null;
                boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
                yield new OriginCheck(layer, origin, inverted);
            }
            case "origins:and", "apace:and", "neoorigins:and" -> {
                List<OriginCondition> subs = parseSubConditions(json);
                yield subs.isEmpty() ? null : new And(subs);
            }
            case "origins:or", "apace:or", "neoorigins:or" -> {
                List<OriginCondition> subs = parseSubConditions(json);
                yield subs.isEmpty() ? null : new Or(subs);
            }
            default -> null;
        };
    }

    private static List<OriginCondition> parseSubConditions(JsonObject json) {
        List<OriginCondition> subs = new ArrayList<>();
        if (json.has("conditions") && json.get("conditions").isJsonArray()) {
            for (JsonElement el : json.getAsJsonArray("conditions")) {
                if (el.isJsonObject()) {
                    OriginCondition sub = parse(el.getAsJsonObject());
                    if (sub != null) subs.add(sub);
                }
            }
        }
        return subs;
    }

    /**
     * Serialize this condition back to JSON for codec round-tripping
     * (used by SyncOriginRegistryPayload via ConditionedOrigin.CODEC).
     */
    JsonObject toJson();

    // ── Implementations ─────────────────────────────────────────────────

    /**
     * Checks if a specific origin was chosen in a specific layer.
     * When {@code inverted} is true, passes when the origin was NOT chosen.
     */
    record OriginCheck(Identifier layer, Identifier origin, boolean inverted) implements OriginCondition {
        @Override
        public boolean test(Map<Identifier, Identifier> chosenOrigins) {
            Identifier chosen = chosenOrigins.get(layer);
            boolean matches = origin.equals(chosen);
            return inverted ? !matches : matches;
        }

        @Override
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "origins:origin");
            obj.addProperty("layer", layer.toString());
            obj.addProperty("origin", origin.toString());
            if (inverted) obj.addProperty("inverted", true);
            return obj;
        }
    }

    /** All sub-conditions must pass. */
    record And(List<OriginCondition> conditions) implements OriginCondition {
        @Override
        public boolean test(Map<Identifier, Identifier> chosenOrigins) {
            for (OriginCondition c : conditions) {
                if (!c.test(chosenOrigins)) return false;
            }
            return true;
        }

        @Override
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "origins:and");
            JsonArray arr = new JsonArray();
            for (OriginCondition c : conditions) arr.add(c.toJson());
            obj.add("conditions", arr);
            return obj;
        }
    }

    /** Any sub-condition must pass. */
    record Or(List<OriginCondition> conditions) implements OriginCondition {
        @Override
        public boolean test(Map<Identifier, Identifier> chosenOrigins) {
            for (OriginCondition c : conditions) {
                if (c.test(chosenOrigins)) return true;
            }
            return false;
        }

        @Override
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", "origins:or");
            JsonArray arr = new JsonArray();
            for (OriginCondition c : conditions) arr.add(c.toJson());
            obj.add("conditions", arr);
            return obj;
        }
    }
}
