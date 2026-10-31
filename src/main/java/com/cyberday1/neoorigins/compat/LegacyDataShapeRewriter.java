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
 * Loot data and advancements get the {@code alternative} condition renamed {@code any_of},
 * and item, block and entity tags get the ids 26.x renamed or split.
 */
public final class LegacyDataShapeRewriter {

    private LegacyDataShapeRewriter() {}

    private static final String[] RECIPE_DIRS = {"recipe/", "recipes/"};
    private static final String[] ADVANCEMENT_DIRS = {"advancement/", "advancements/"};
    private static final String[] LOOT_DIRS = {"predicate/", "predicates/", "item_modifier/", "item_modifiers/",
        "loot_table/", "loot_tables/"};
    private static final String ALTERNATIVE = "alternative";

    // Vanilla recipe types only; other mods' schemas are not ours to guess.
    private static final Set<String> VANILLA_RECIPES = Set.of(
        "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
        "campfire_cooking", "stonecutting", "smithing_transform", "smithing_trim");
    private static final List<String> SINGLE_INGREDIENT_FIELDS = List.of(
        "ingredient", "template", "base", "addition");

    /** True for the in-namespace paths this rewriter looks at. */
    public static boolean handles(String path) {
        return path.endsWith(".json") && (startsWithAny(path, RECIPE_DIRS) || startsWithAny(path, ADVANCEMENT_DIRS)
            || startsWithAny(path, LOOT_DIRS) || tagKind(path) != null);
    }

    /**
     * @param path in-namespace path, e.g. {@code recipe/golden_boots.json}
     * @return the rewritten text, or null when nothing changed
     */
    @Nullable
    public static String rewrite(String path, String json) {
        String tagKind = tagKind(path);
        if (tagKind != null) return rewriteTag(tagKind, json);
        boolean alternative = json.contains(ALTERNATIVE);
        if (!alternative && (startsWithAny(path, LOOT_DIRS) || !json.contains("\"item\"") && !json.contains("\"tag\""))) {
            return null;
        }
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

    // ── tags (26.x) ─────────────────────────────────────────────────────────

    private static final String[][] TAG_DIRS = {
        {"tags/item/", "item"}, {"tags/items/", "item"}, {"tags/block/", "block"}, {"tags/blocks/", "block"},
        {"tags/entity_type/", "entity_type"}, {"tags/entity_types/", "entity_type"}};

    // Ids 26.x renamed or split. The legacy boat entity covered every wood, which is #minecraft:boat now.
    private static final Map<String, Map<String, List<String>>> TAG_RENAMES = Map.of(
        "item", Map.of("minecraft:chain", List.of("minecraft:iron_chain")),
        "block", Map.of("minecraft:chain", List.of("minecraft:iron_chain")),
        "entity_type", Map.of(
            "minecraft:boat", List.of("#minecraft:boat"),
            "minecraft:potion", List.of("minecraft:splash_potion", "minecraft:lingering_potion")));

    @Nullable
    private static String tagKind(String path) {
        for (String[] dir : TAG_DIRS) if (path.startsWith(dir[0])) return dir[1];
        return null;
    }

    /** Tag values naming a renamed or split id are replaced by the current id(s), keeping {@code required}. */
    @Nullable
    static String rewriteTag(String kind, String json) {
        Map<String, List<String>> renames = TAG_RENAMES.get(kind);
        boolean mentioned = false;
        for (String id : renames.keySet()) mentioned |= json.contains("\"" + id + "\"");
        if (!mentioned) return null;
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return null;
        }
        if (!(root instanceof JsonObject obj) || !(obj.get("values") instanceof JsonArray values)) return null;
        JsonArray out = new JsonArray();
        boolean changed = false;
        for (JsonElement value : values) {
            JsonObject entry = value instanceof JsonObject o ? o : null;
            String id = entry != null ? stringOrNull(entry.get("id")) : stringOrNull(value);
            List<String> current = id == null ? null : renames.get(id);
            if (current == null) {
                out.add(value);
                continue;
            }
            changed = true;
            for (String to : current) {
                if (entry == null) {
                    out.add(to);
                } else {
                    JsonObject copy = entry.deepCopy();
                    copy.addProperty("id", to);
                    out.add(copy);
                }
            }
        }
        if (!changed) return null;
        obj.add("values", out);
        return new GsonBuilder().disableHtmlEscaping().create().toJson(root);
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
