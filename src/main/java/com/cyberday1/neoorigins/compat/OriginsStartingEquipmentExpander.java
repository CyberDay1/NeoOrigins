package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Apoli's {@code origins:starting_equipment} carries an array of stacks under
 * a single power ID (or one stack under the singular {@code stack} alias).
 * NeoOrigins' {@code neoorigins:starting_equipment} is one-stack-per-power.
 * This expander bridges the two: it takes the Apoli JSON and returns a map of
 * synthetic IDs ({@code <orig>/stack_<i>}) → canonical NeoOrigins JSONs, one
 * per stack entry. See {@link #readStacks} for the singular/plural handling.
 *
 * <p>Designed to run alongside {@link OriginsMultipleExpander} during
 * {@code PowerDataManager.apply()} — each output stack carries the
 * translated NeoOrigins type directly, so the standard translator
 * pipeline doesn't need to touch it.
 *
 * <p>Per-stack fields:
 * <ul>
 *   <li>{@code item} — required, Apoli's item id string</li>
 *   <li>{@code amount} — optional int (default 1) → {@code count}</li>
 *   <li>{@code tag} — optional SNBT string → forwarded as {@code legacy_tag}
 *       and resolved at grant time by {@link LegacyTagToComponents}</li>
 * </ul>
 *
 * <p>Each synthetic power carries a {@code grant_id} of
 * {@code <namespace>:<path>__stack_<i>} so the dedup tracker in
 * {@code PlayerOriginData.grantedEquipmentPowers} differentiates them.
 *
 * <p>Display metadata (name/description) from the parent is copied to
 * the FIRST synthetic only — pack authors typically expect a single
 * tooltip entry covering the whole gear bundle.
 */
public final class OriginsStartingEquipmentExpander {

    private OriginsStartingEquipmentExpander() {}

    public static boolean isStartingEquipment(String type) {
        return "origins:starting_equipment".equals(type) || "apace:starting_equipment".equals(type);
    }

    public static Map<Identifier, JsonObject> expand(Identifier id, JsonObject src) {
        Map<Identifier, JsonObject> out = new HashMap<>();
        java.util.List<Identifier> syntheticIds = new java.util.ArrayList<>();
        JsonArray stacks = readStacks(id, src);
        if (stacks == null) return out;
        for (int i = 0; i < stacks.size(); i++) {
            JsonElement el = stacks.get(i);
            if (!el.isJsonObject()) continue;
            JsonObject stack = el.getAsJsonObject();
            String item = stack.has("item") ? stack.get("item").getAsString() : null;
            if (item == null) {
                NeoOrigins.LOGGER.warn("[CompatB] starting_equipment '{}' stack[{}] missing 'item' — skipped", id, i);
                continue;
            }
            int amount = stack.has("amount") ? stack.get("amount").getAsInt() : 1;
            String tag = stack.has("tag") ? stack.get("tag").getAsString() : "";

            JsonObject synthetic = new JsonObject();
            synthetic.addProperty("type", "neoorigins:starting_equipment");
            synthetic.addProperty("grant_id", id.getNamespace() + ":" + id.getPath() + "__stack_" + i);
            synthetic.addProperty("item", item);
            synthetic.addProperty("count", amount);
            if (!tag.isEmpty()) synthetic.addProperty("legacy_tag", tag);

            // Mirror parent display on the first stack only — keeps the origin
            // info screen from showing N copies of the same gear-bundle line.
            if (i == 0) {
                if (src.has("name"))        synthetic.add("name",        src.get("name"));
                if (src.has("description")) synthetic.add("description", src.get("description"));
            } else {
                synthetic.addProperty("hidden", true);
            }

            Identifier syntheticId = Identifier.fromNamespaceAndPath(
                id.getNamespace(), id.getPath() + "/stack_" + i);
            out.put(syntheticId, synthetic);
            syntheticIds.add(syntheticId);
        }
        // Reuse the multiple-expansion map so OriginsOriginTranslator's
        // power-list rewrite picks up the parent → sub-power mapping for
        // free, the same way origins:multiple expansion does.
        if (!syntheticIds.isEmpty()) {
            OriginsMultipleExpander.MULTIPLE_EXPANSION_MAP.put(id, syntheticIds);
        }
        return out;
    }

    /**
     * Read the stack list, tolerating Apoli's singular {@code stack} alias.
     *
     * <p>Apoli declares <em>both</em> fields on this power: {@code stack} (a
     * single {@code IndexedStack}, default null) and {@code stacks} (a list,
     * whose functioned default is {@code singletonListOrNull(get("stack"))}),
     * with a {@code validateAnyFieldsPresent("stack", "stacks")} guard. So
     * {@code stacks} wins when present and {@code stack} is otherwise promoted
     * to a one-element list — mirrored exactly here. Same singular/plural
     * tolerance the layer already applies to {@code hands}/{@code hand}.
     *
     * @return the stacks to expand, or {@code null} if neither field is usable
     */
    @javax.annotation.Nullable
    private static JsonArray readStacks(Identifier id, JsonObject src) {
        if (src.has("stacks") && src.get("stacks").isJsonArray()) {
            return src.getAsJsonArray("stacks");
        }
        if (src.has("stack") && src.get("stack").isJsonObject()) {
            JsonArray singleton = new JsonArray();
            singleton.add(src.getAsJsonObject("stack"));
            return singleton;
        }
        NeoOrigins.LOGGER.warn("[CompatB] starting_equipment '{}' has no 'stacks' array or 'stack' object — skipped", id);
        return null;
    }
}
