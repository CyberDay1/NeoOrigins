package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites the one retired loot function that stops legacy Origins packs dead:
 * {@code minecraft:set_nbt}, removed in 1.20.5.
 *
 * <p>A loot table carrying it does not merely lose that function — the whole
 * table fails to parse ({@code Unknown registry key … minecraft:set_nbt}),
 * which cascades into every function/advancement that references the table,
 * so items the pack hands out become unobtainable.
 *
 * <p><b>Scope is deliberately one function, one direction.</b> The replacement
 * is {@code minecraft:set_custom_data}, which takes the identical {@code tag}
 * field — but that is only a faithful translation when the blob is
 * <em>pure custom data</em>. Keys that moved to a real data component on 1.21
 * ({@code Enchantments}, {@code Damage}, {@code display}, {@code Potion}, …)
 * would end up buried inside {@code custom_data} where nothing reads them,
 * so a blob carrying one of those is left exactly as it was and reported. The
 * vanilla-key set is {@link LegacyTagToComponents#recognisedKeys()}, shared
 * with {@code LegacyCommandRewriter}'s clear-predicate path so the two cannot
 * drift. This is <em>not</em> a general NBT → components converter.
 */
public final class LegacyLootFunctionRewriter {

    private LegacyLootFunctionRewriter() {}

    /** Cheap pre-parse gate — packs ship hundreds of JSONs and almost none match. */
    public static final String MARKER = "set_nbt";

    private static final String LEGACY_SHORT = "set_nbt";
    private static final String LEGACY_FULL = "minecraft:set_nbt";
    private static final String REPLACEMENT = "minecraft:set_custom_data";

    /** Guard against a pathological nesting depth in hand-written pack JSON. */
    private static final int MAX_DEPTH = 24;

    /**
     * Rewrite {@code set_nbt} occurrences in a loot-table / item-modifier JSON
     * document.
     *
     * @param json    raw file text
     * @param packId  pack name, used only for the "left alone" warning
     * @return the rewritten text, or {@code null} if nothing changed (caller
     *         should serve the original bytes untouched)
     */
    @Nullable
    public static String rewrite(String json, String packId) {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            // Malformed JSON is vanilla's problem to report, not ours to mask.
            return null;
        }
        return visit(root, packId, 0) ? sanitise(root) : null;
    }

    /** Recursively convert every eligible {@code set_nbt}. Returns true if anything changed. */
    private static boolean visit(JsonElement el, String packId, int depth) {
        if (depth > MAX_DEPTH) return false;
        boolean changed = false;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement child : arr) changed |= visit(child, packId, depth + 1);
            return changed;
        }
        if (!el.isJsonObject()) return false;

        JsonObject obj = el.getAsJsonObject();
        if (isLegacySetNbt(obj) && convert(obj, packId)) changed = true;
        for (var entry : obj.entrySet()) changed |= visit(entry.getValue(), packId, depth + 1);
        return changed;
    }

    private static boolean isLegacySetNbt(JsonObject obj) {
        JsonElement fn = obj.get("function");
        if (fn == null || !fn.isJsonPrimitive() || !fn.getAsJsonPrimitive().isString()) return false;
        String name = fn.getAsString();
        return LEGACY_SHORT.equals(name) || LEGACY_FULL.equals(name);
    }

    /**
     * Retarget one function object, or leave it alone when the tag is not pure
     * custom data. Returns true only when the object was actually modified.
     */
    private static boolean convert(JsonObject fn, String packId) {
        JsonElement tagEl = fn.get("tag");
        if (tagEl == null || !tagEl.isJsonPrimitive() || !tagEl.getAsJsonPrimitive().isString()) {
            NeoOrigins.LOGGER.warn(
                "OriginsCompat: pack '{}' uses the removed 'set_nbt' loot function with no string 'tag' — left as-is (the table will still fail to load)",
                packId);
            return false;
        }
        CompoundTag tag;
        try {
            // 26.1: TagParser.parseTag is gone; use parseCompoundFully.
            tag = TagParser.parseCompoundFully(tagEl.getAsString());
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn(
                "OriginsCompat: pack '{}' has a 'set_nbt' loot function with malformed SNBT '{}' — left as-is",
                packId, tagEl.getAsString());
            return false;
        }
        List<String> vanillaKeys = new ArrayList<>();
        for (String key : tag.keySet()) {
            if (LegacyTagToComponents.recognisedKeys().contains(key)) vanillaKeys.add(key);
        }
        if (!vanillaKeys.isEmpty()) {
            // set_custom_data would hide these from the game entirely; a correct
            // conversion needs per-key component mapping, which is out of scope.
            NeoOrigins.LOGGER.warn(
                "OriginsCompat: pack '{}' has a 'set_nbt' loot function whose tag carries component-backed key(s) {} — left as-is, it needs a real component mapping",
                packId, vanillaKeys);
            return false;
        }
        fn.addProperty("function", REPLACEMENT);
        return true;
    }

    /** Serialise without HTML-escaping, so §-codes and quoted text survive verbatim. */
    private static String sanitise(JsonElement root) {
        return new com.google.gson.GsonBuilder().disableHtmlEscaping().create().toJson(root);
    }
}
