package com.cyberday1.neoorigins.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites legacy JSON shapes in originpack recipes and advancements that the
 * 26.x codecs reject. Only the invalid shape is touched; valid files come back null.
 *
 * <p>An item stack written {@code {"item":...}} where the codec requires {@code id}
 * (recipe {@code result}, advancement {@code display.icon}), and a 1.21.1-style
 * ingredient object, which 26.x only accepts as an id, a {@code #tag} or an id list.
 */
public final class LegacyDataShapeRewriter {

    private LegacyDataShapeRewriter() {}

    private static final String[] RECIPE_DIRS = {"recipe/", "recipes/"};
    private static final String[] ADVANCEMENT_DIRS = {"advancement/", "advancements/"};

    // Vanilla recipe types only; other mods' schemas are not ours to guess.
    private static final Set<String> VANILLA_RECIPES = Set.of(
        "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
        "campfire_cooking", "stonecutting", "smithing_transform", "smithing_trim");
    private static final List<String> SINGLE_INGREDIENT_FIELDS = List.of(
        "ingredient", "template", "base", "addition");

    /** True for the in-namespace paths this rewriter looks at. */
    public static boolean handles(String path) {
        return path.endsWith(".json") && (startsWithAny(path, RECIPE_DIRS) || startsWithAny(path, ADVANCEMENT_DIRS));
    }

    /**
     * @param path in-namespace path, e.g. {@code recipe/golden_boots.json}
     * @return the rewritten text, or null when nothing changed
     */
    @Nullable
    public static String rewrite(String path, String json) {
        if (!json.contains("\"item\"") && !json.contains("\"tag\"")) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return null; // malformed JSON is vanilla's to report
        }
        if (!root.isJsonObject()) return null;
        JsonObject obj = root.getAsJsonObject();
        boolean changed = false;
        if (startsWithAny(path, RECIPE_DIRS)) changed = rewriteRecipe(obj);
        else if (startsWithAny(path, ADVANCEMENT_DIRS)) changed = rewriteAdvancement(obj);
        return changed ? new GsonBuilder().disableHtmlEscaping().create().toJson(root) : null;
    }

    private static boolean rewriteRecipe(JsonObject recipe) {
        String type = stringOrNull(recipe.get("type"));
        if (type == null) return false;
        if (type.startsWith("minecraft:")) type = type.substring("minecraft:".length());
        if (!VANILLA_RECIPES.contains(type)) return false;
        boolean changed = itemToId(recipe.get("result"), false);
        if (recipe.get("key") instanceof JsonObject key) {
            for (var e : key.entrySet()) changed |= replaceIngredient(e);
        }
        if (recipe.get("ingredients") instanceof JsonArray list) {
            for (int i = 0; i < list.size(); i++) {
                JsonElement modern = modernIngredient(list.get(i));
                if (modern != null) { list.set(i, modern); changed = true; }
            }
        }
        for (String field : SINGLE_INGREDIENT_FIELDS) {
            JsonElement modern = modernIngredient(recipe.get(field));
            if (modern != null) { recipe.add(field, modern); changed = true; }
        }
        return changed;
    }

    private static boolean replaceIngredient(Map.Entry<String, JsonElement> e) {
        JsonElement modern = modernIngredient(e.getValue());
        if (modern == null) return false;
        e.setValue(modern);
        return true;
    }

    /**
     * {@code {"item":X}} → {@code "X"}, {@code {"tag":T}} → {@code "#T"}, and a list
     * of item objects → a list of ids. Null when already modern or not expressible
     * (a list mixing tags, or an object with other keys such as a custom ingredient).
     */
    @Nullable
    static JsonElement modernIngredient(@Nullable JsonElement el) {
        if (el instanceof JsonObject obj) {
            if (obj.size() != 1) return null;
            String item = stringOrNull(obj.get("item"));
            if (item != null) return new JsonPrimitive(item);
            String tag = stringOrNull(obj.get("tag"));
            return tag != null ? new JsonPrimitive("#" + tag) : null;
        }
        if (el instanceof JsonArray arr && !arr.isEmpty()) {
            if (arr.size() == 1 && arr.get(0) instanceof JsonObject only && only.size() == 1 && only.has("tag")) {
                return modernIngredient(only);
            }
            JsonArray ids = new JsonArray();
            boolean legacy = false;
            for (JsonElement child : arr) {
                String id = stringOrNull(child);
                if (id == null && child instanceof JsonObject obj && obj.size() == 1) {
                    id = stringOrNull(obj.get("item"));
                    legacy |= id != null;
                }
                if (id == null || id.startsWith("#")) return null;
                ids.add(id);
            }
            return legacy ? ids : null;
        }
        return null;
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
