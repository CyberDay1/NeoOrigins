package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Apoli's {@code origins:starting_equipment} carries an array of stacks under
 * a single power ID. NeoOrigins' {@code neoorigins:starting_equipment} is
 * one-stack-per-power. This expander bridges the two: it takes the Apoli
 * JSON and returns a map of synthetic IDs ({@code <orig>/stack_<i>}) →
 * canonical NeoOrigins JSONs, one per stack entry.
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

    public static Map<ResourceLocation, JsonObject> expand(ResourceLocation id, JsonObject src) {
        Map<ResourceLocation, JsonObject> out = new HashMap<>();
        java.util.List<ResourceLocation> syntheticIds = new java.util.ArrayList<>();
        if (!src.has("stacks") || !src.get("stacks").isJsonArray()) {
            NeoOrigins.LOGGER.warn("[CompatB] starting_equipment '{}' has no 'stacks' array — skipped", id);
            return out;
        }
        JsonArray stacks = src.getAsJsonArray("stacks");
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

            ResourceLocation syntheticId = ResourceLocation.fromNamespaceAndPath(
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
}
