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
 * Rewrites legacy JSON shapes in originpack recipes, advancements, predicates,
 * item modifiers and loot tables that the 26.3 codecs reject. Only the invalid
 * shape is touched; valid files come back null.
 *
 * <p>An item stack written {@code {"item":...}} where the codec requires {@code id}
 * (recipe {@code result}, advancement {@code display.icon}), and a 1.21.1-style
 * ingredient object, which 26.x only accepts as an id, a {@code #tag} or an id list.
 * A child advancement's {@code background} is dropped: 26.3 rejects it and earlier
 * versions only ever drew the root's.
 *
 * <p>26.3 also re-keyed loot data: conditions and functions dispatch on {@code type}
 * (was {@code condition} / {@code function}), the {@code conditions} and
 * {@code functions} lists became one {@code condition} and one {@code modifier},
 * a top-level array is no longer a predicate or item modifier, and a number
 * provider needs its {@code type}.
 */
public final class LegacyDataShapeRewriter {

    private LegacyDataShapeRewriter() {}

    private static final String[] RECIPE_DIRS = {"recipe/", "recipes/"};
    private static final String[] ADVANCEMENT_DIRS = {"advancement/", "advancements/"};
    private static final String[] PREDICATE_DIRS = {"predicate/", "predicates/"};
    private static final String[] ITEM_MODIFIER_DIRS = {"item_modifier/", "item_modifiers/"};
    private static final String[] LOOT_TABLE_DIRS = {"loot_table/", "loot_tables/"};

    private static final String ALL_OF = "minecraft:all_of";
    private static final String SEQUENCE = "minecraft:sequence";
    private static final String UNIFORM = "minecraft:uniform";
    // Number-provider fields of the vanilla loot functions legacy packs use.
    private static final Map<String, String> FUNCTION_NUMBER_FIELDS = Map.of(
        "set_count", "count", "set_damage", "damage",
        "enchant_with_levels", "levels", "enchanted_count_increase", "count");

    // Vanilla recipe types only; other mods' schemas are not ours to guess.
    private static final Set<String> VANILLA_RECIPES = Set.of(
        "crafting_shaped", "crafting_shapeless", "smelting", "blasting", "smoking",
        "campfire_cooking", "stonecutting", "smithing_transform", "smithing_trim");
    private static final List<String> SINGLE_INGREDIENT_FIELDS = List.of(
        "ingredient", "template", "base", "addition");

    /** True for the in-namespace paths this rewriter looks at. */
    public static boolean handles(String path) {
        return path.endsWith(".json") && (startsWithAny(path, RECIPE_DIRS) || startsWithAny(path, ADVANCEMENT_DIRS)
            || isLoot(path));
    }

    private static boolean isLoot(String path) {
        return startsWithAny(path, PREDICATE_DIRS) || startsWithAny(path, ITEM_MODIFIER_DIRS)
            || startsWithAny(path, LOOT_TABLE_DIRS);
    }

    /**
     * @param path in-namespace path, e.g. {@code recipe/golden_boots.json}
     * @return the rewritten text, or null when nothing changed
     */
    @Nullable
    public static String rewrite(String path, String json) {
        boolean loot = isLoot(path);
        if (!loot && !json.contains("\"item\"") && !json.contains("\"tag\"") && !json.contains("\"background\"")) {
            return null;
        }
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (Exception e) {
            return null; // malformed JSON is vanilla's to report
        }
        if (loot) {
            LootWalk walk = new LootWalk();
            JsonElement out;
            if (startsWithAny(path, PREDICATE_DIRS)) out = walk.condition(root);
            else if (startsWithAny(path, ITEM_MODIFIER_DIRS)) out = walk.function(root);
            else out = root instanceof JsonObject table ? walk.table(table) : root;
            return walk.changed ? new GsonBuilder().disableHtmlEscaping().create().toJson(out) : null;
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
        if (!(adv.get("display") instanceof JsonObject display)) return false;
        boolean changed = itemToId(display.get("icon"), true);
        if (adv.has("parent") && display.remove("background") != null) changed = true;
        return changed;
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
        replaceKey(obj, from, to, obj.get(from));
    }

    /** Swaps one key for another (dropping it when value is null), keeping its position. */
    static void replaceKey(JsonObject obj, String from, String to, @Nullable JsonElement value) {
        var entries = new ArrayList<Map.Entry<String, JsonElement>>(obj.entrySet());
        for (var e : entries) obj.remove(e.getKey());
        for (var e : entries) {
            if (!e.getKey().equals(from)) obj.add(e.getKey(), e.getValue());
            else if (value != null) obj.add(to, value);
        }
    }

    // ── loot data (26.3) ────────────────────────────────────────────────────

    /** Schema-aware walk: each method knows what sits at its position, so only legacy keys there change. */
    static final class LootWalk {
        boolean changed;

        JsonObject table(JsonObject table) {
            if (table.get("pools") instanceof JsonArray pools) {
                for (JsonElement pool : pools) if (pool instanceof JsonObject p) pool(p);
            }
            functionsToModifier(table);
            return table;
        }

        private void pool(JsonObject pool) {
            number(pool, "rolls");
            number(pool, "bonus_rolls");
            entries(pool.get("entries"));
            conditionsToCondition(pool);
            functionsToModifier(pool);
        }

        private void entries(@Nullable JsonElement list) {
            if (!(list instanceof JsonArray arr)) return;
            for (JsonElement el : arr) {
                if (!(el instanceof JsonObject entry)) continue;
                entries(entry.get("children"));
                conditionsToCondition(entry);
                functionsToModifier(entry);
            }
        }

        /** A predicate: {@code condition} becomes {@code type}; a bare array becomes {@code all_of}. */
        JsonElement condition(JsonElement el) {
            if (el instanceof JsonArray arr) {
                changed = true;
                return wrap(ALL_OF, "terms", mapConditions(arr));
            }
            if (!(el instanceof JsonObject obj)) return el;
            if (!obj.has("type") && stringOrNull(obj.get("condition")) != null) {
                renameKey(obj, "condition", "type");
                changed = true;
            }
            String type = shortType(obj);
            if ("inverted".equals(type) && obj.has("term")) obj.add("term", condition(obj.get("term")));
            if (("any_of".equals(type) || "all_of".equals(type)) && obj.get("terms") instanceof JsonArray terms) {
                obj.add("terms", mapConditions(terms));
            }
            if ("random_chance".equals(type)) number(obj, "chance");
            return obj;
        }

        /** An item modifier: {@code function} becomes {@code type}; a bare array becomes {@code sequence}. */
        JsonElement function(JsonElement el) {
            if (el instanceof JsonArray arr) {
                changed = true;
                return wrap(SEQUENCE, "functions", mapFunctions(arr));
            }
            if (!(el instanceof JsonObject obj)) return el;
            if (!obj.has("type") && stringOrNull(obj.get("function")) != null) {
                renameKey(obj, "function", "type");
                changed = true;
            }
            conditionsToCondition(obj);
            String type = shortType(obj);
            String numberField = type == null ? null : FUNCTION_NUMBER_FIELDS.get(type);
            if (numberField != null) number(obj, numberField);
            // A sequence keeps its functions list: that one is still the 26.3 key.
            if ("sequence".equals(type) && obj.get("functions") instanceof JsonArray fns) {
                obj.add("functions", mapFunctions(fns));
            }
            return obj;
        }

        private JsonArray mapConditions(JsonArray arr) {
            JsonArray out = new JsonArray();
            for (JsonElement el : arr) out.add(condition(el));
            return out;
        }

        private JsonArray mapFunctions(JsonArray arr) {
            JsonArray out = new JsonArray();
            for (JsonElement el : arr) out.add(function(el));
            return out;
        }

        /** Legacy {@code conditions} list to one {@code condition}, in {@code all_of} when there are several. */
        private void conditionsToCondition(JsonObject obj) {
            if (obj.has("condition") || !(obj.get("conditions") instanceof JsonArray list)) return;
            replaceKey(obj, "conditions", "condition", single(mapConditions(list), ALL_OF, "terms"));
            changed = true;
        }

        /** Legacy {@code functions} list to one {@code modifier}, in {@code sequence} when there are several. */
        private void functionsToModifier(JsonObject obj) {
            if (obj.has("modifier") || !(obj.get("functions") instanceof JsonArray list)) return;
            replaceKey(obj, "functions", "modifier", single(mapFunctions(list), SEQUENCE, "functions"));
            changed = true;
        }

        /** Untyped {@code {"min":..,"max":..}} becomes {@code minecraft:uniform}. */
        private void number(JsonObject owner, String field) {
            if (!(owner.get(field) instanceof JsonObject n) || n.has("type")) return;
            if (n.size() != 2 || !n.has("min") || !n.has("max")) return;
            number(n, "min");
            number(n, "max");
            JsonObject typed = new JsonObject();
            typed.addProperty("type", UNIFORM);
            for (var e : n.entrySet()) typed.add(e.getKey(), e.getValue());
            owner.add(field, typed);
            changed = true;
        }

        @Nullable
        private static JsonElement single(JsonArray list, String wrapperType, String listKey) {
            if (list.isEmpty()) return null;
            return list.size() == 1 ? list.get(0) : wrap(wrapperType, listKey, list);
        }

        private static JsonObject wrap(String type, String listKey, JsonArray list) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", type);
            obj.add(listKey, list);
            return obj;
        }

        @Nullable
        private static String shortType(JsonObject obj) {
            String type = stringOrNull(obj.get("type"));
            return type != null && type.startsWith("minecraft:") ? type.substring("minecraft:".length()) : type;
        }
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
