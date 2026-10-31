package com.cyberday1.neoorigins.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites legacy JSON shapes in originpack recipes and advancements that the
 * 1.21.1 codecs reject. Only the invalid shape is touched; valid files come back null.
 *
 * <p>1.21.1 subset: an item stack written {@code {"item":...}} where the codec
 * requires {@code id} (recipe {@code result}, advancement {@code display.icon}).
 * Ingredients still accept {@code {"item":...}} on this line, so they are left alone.
 * Loot data and advancements also get the {@code alternative} condition renamed {@code any_of}.
 */
public final class LegacyDataShapeRewriter {

    private LegacyDataShapeRewriter() {}

    private static final String[] RECIPE_DIRS = {"recipe/", "recipes/"};
    private static final String[] ADVANCEMENT_DIRS = {"advancement/", "advancements/"};
    private static final String[] LOOT_DIRS = {"predicate/", "predicates/", "item_modifier/", "item_modifiers/",
        "loot_table/", "loot_tables/"};
    private static final String ALTERNATIVE = "alternative";

    // Vanilla recipe types whose result is an ItemStack; other mods' schemas are not ours to guess.
    private static final Set<String> VANILLA_RECIPES = Set.of(
        "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
        "campfire_cooking", "stonecutting", "smithing_transform");

    /** True for the in-namespace paths this rewriter looks at. */
    public static boolean handles(String path) {
        return path.endsWith(".json") && (startsWithAny(path, RECIPE_DIRS) || startsWithAny(path, ADVANCEMENT_DIRS)
            || startsWithAny(path, LOOT_DIRS));
    }

    /**
     * @param path in-namespace path, e.g. {@code recipe/golden_boots.json}
     * @return the rewritten text, or null when nothing changed
     */
    @Nullable
    public static String rewrite(String path, String json) {
        boolean alternative = json.contains(ALTERNATIVE);
        if (!alternative && (startsWithAny(path, LOOT_DIRS) || !json.contains("\"item\""))) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return null; // malformed JSON is vanilla's to report
        }
        boolean changed = false;
        if (root instanceof JsonObject obj) {
            if (startsWithAny(path, RECIPE_DIRS)) changed = rewriteRecipe(obj);
            else if (startsWithAny(path, ADVANCEMENT_DIRS)) changed = rewriteAdvancement(obj);
        }
        if (alternative && !startsWithAny(path, RECIPE_DIRS)) changed |= alternativeToAnyOf(root);
        return changed ? new GsonBuilder().disableHtmlEscaping().create().toJson(root) : null;
    }

    private static boolean rewriteRecipe(JsonObject recipe) {
        String type = stringOrNull(recipe.get("type"));
        if (type == null) return false;
        if (type.startsWith("minecraft:")) type = type.substring("minecraft:".length());
        if (!VANILLA_RECIPES.contains(type)) return false;
        return itemToId(recipe.get("result"), false);
    }

    private static boolean rewriteAdvancement(JsonObject adv) {
        JsonElement display = adv.get("display");
        if (display == null || !display.isJsonObject()) return false;
        return itemToId(display.getAsJsonObject().get("icon"), true);
    }

    /**
     * {@code {"item":X}} → {@code {"id":X}}, in place. A recipe result carrying
     * legacy {@code nbt} is left alone: dropping it would craft the wrong item.
     */
    static boolean itemToId(@Nullable JsonElement el, boolean allowNbt) {
        if (el == null || !el.isJsonObject()) return false;
        JsonObject stack = el.getAsJsonObject();
        if (stack.has("id") || stringOrNull(stack.get("item")) == null) return false;
        if (!allowNbt && stack.has("nbt")) return false;
        renameKey(stack, "item", "id");
        return true;
    }

    /** The loot condition {@code alternative}, renamed {@code any_of} with the same {@code terms}. */
    static boolean alternativeToAnyOf(JsonElement el) {
        boolean changed = false;
        if (el instanceof JsonObject obj) {
            String type = stringOrNull(obj.get("condition"));
            if (ALTERNATIVE.equals(type) || ("minecraft:" + ALTERNATIVE).equals(type)) {
                obj.addProperty("condition", "minecraft:any_of");
                changed = true;
            }
            for (var e : obj.entrySet()) changed |= alternativeToAnyOf(e.getValue());
        } else if (el instanceof JsonArray arr) {
            for (JsonElement child : arr) changed |= alternativeToAnyOf(child);
        }
        return changed;
    }

    /** Renames a key in place, keeping its position. */
    static void renameKey(JsonObject obj, String from, String to) {
        var entries = new ArrayList<Map.Entry<String, JsonElement>>(obj.entrySet());
        for (var e : entries) obj.remove(e.getKey());
        for (var e : entries) obj.add(e.getKey().equals(from) ? to : e.getKey(), e.getValue());
    }

    @Nullable
    static String stringOrNull(@Nullable JsonElement el) {
        return el != null && el.isJsonPrimitive() && el.getAsJsonPrimitive().isString() ? el.getAsString() : null;
    }

    private static boolean startsWithAny(String path, String[] prefixes) {
        for (String p : prefixes) if (path.startsWith(p)) return true;
        return false;
    }
}
