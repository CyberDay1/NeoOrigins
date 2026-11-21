package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * {@code neoorigins:has_ingredient}: true when a stack in the slot range is crafted
 * (directly, or up to {@code depth} recipe levels down) from the target item or tag.
 */
public final class HasIngredientCondition {

    private HasIngredientCondition() {}

    static final String TYPE = "neoorigins:has_ingredient";
    static final int MAX_DEPTH = 8;
    static final Identifier CRAFTING = Identifier.withDefaultNamespace("crafting");

    public enum Slot { HELD, HOTBAR, INVENTORY }

    /** A parsed config: exactly one of {@code item}/{@code tag} is set. */
    record Spec(Item item, TagKey<Item> tag, Slot slot, int depth, Set<Identifier> recipeTypes) {}

    /** result item -> accepted items of every input of every recipe producing it. */
    @FunctionalInterface
    interface RecipeSource {
        Map<Item, Set<Item>> directInputs(Set<Identifier> recipeTypes);
    }

    // ---- parsing ---------------------------------------------------------

    static EntityCondition parse(JsonObject json, String ctx) {
        Spec spec = parseSpec(json, ctx);
        return spec == null ? p -> false : create(spec);
    }

    /** The config, or null (after recording why) when the condition must fail closed. */
    static Spec parseSpec(JsonObject json, String ctx) {
        String itemId = string(json, "item");
        String tagId = string(json, "tag");
        if ((itemId == null) == (tagId == null)) {
            return fail(ctx, "needs exactly one of 'item' or 'tag': never matches");
        }
        Item item = null;
        TagKey<Item> tag = null;
        if (itemId != null) {
            Identifier id = Identifier.tryParse(itemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return fail(ctx, "unknown item '" + itemId + "': never matches");
            }
            item = BuiltInRegistries.ITEM.getValue(id);
        } else {
            Identifier id = Identifier.tryParse(tagId.startsWith("#") ? tagId.substring(1) : tagId);
            if (id == null) return fail(ctx, "invalid tag '" + tagId + "': never matches");
            tag = TagKey.create(Registries.ITEM, id);
        }
        Slot slot = parseSlot(string(json, "slot"), ctx);
        int depth = json.has("depth") ? Math.max(1, Math.min(MAX_DEPTH, json.get("depth").getAsInt())) : 1;
        Set<Identifier> types = parseRecipeTypes(json, ctx);
        if (types.isEmpty()) return fail(ctx, "no known recipe_types: never matches");
        return new Spec(item, tag, slot, depth, Set.copyOf(types));
    }

    static Slot parseSlot(String raw, String ctx) {
        if (raw == null) return Slot.HELD;
        try {
            return Slot.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            NeoOrigins.LOGGER.warn("{} in '{}': unknown slot '{}', using held", TYPE, ctx, raw);
            return Slot.HELD;
        }
    }

    private static Set<Identifier> parseRecipeTypes(JsonObject json, String ctx) {
        if (!json.has("recipe_types")) return Set.of(CRAFTING);
        List<String> raw = new ArrayList<>();
        JsonElement el = json.get("recipe_types");
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) raw.add(e.getAsString());
        } else {
            raw.add(el.getAsString());
        }
        Set<Identifier> out = new LinkedHashSet<>();
        for (String s : raw) {
            Identifier id = Identifier.tryParse(s);
            if (id != null && BuiltInRegistries.RECIPE_TYPE.containsKey(id)) out.add(id);
            else NeoOrigins.LOGGER.warn("{} in '{}': unknown recipe type '{}', skipped", TYPE, ctx, s);
        }
        return out;
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) return null;
        String s = json.get(key).getAsString();
        return s.isBlank() ? null : s;
    }

    private static Spec fail(String ctx, String detail) {
        com.cyberday1.neoorigins.compat.CompatWarningCollector.recordUnsupportedCondition("has_ingredient", ctx, detail);
        return null;
    }

    // ---- evaluation ------------------------------------------------------

    static EntityCondition create(Spec spec) {
        Predicate<Item> target = spec.item() != null
            ? i -> i == spec.item()
            : i -> i.builtInRegistryHolder().is(spec.tag());
        // Per-condition memo of stack item -> verdict, dropped when the graph is rebuilt.
        Map<Item, Boolean> memo = new ConcurrentHashMap<>();
        Object[] memoGraph = new Object[1];
        return player -> {
            Graph<Item> graph = graphFor(player, spec.recipeTypes());
            if (graph == null) return false;
            if (memoGraph[0] != graph) {
                memo.clear();
                memoGraph[0] = graph;
            }
            for (ItemStack stack : stacks(player, spec.slot())) {
                if (stack.isEmpty()) continue;
                Item item = stack.getItem();
                Boolean hit = memo.get(item);
                if (hit == null) {
                    hit = graph.reachable(item, spec.depth()).stream().anyMatch(target);
                    memo.put(item, hit);
                }
                if (hit) return true;
            }
            return false;
        };
    }

    /** The stacks a slot mode reads: held = both hands; inventory = all main slots plus offhand. */
    static List<ItemStack> stacks(ServerPlayer player, Slot slot) {
        var items = player.getInventory().getNonEquipmentItems();
        return switch (slot) {
            case HELD -> List.of(player.getMainHandItem(), player.getOffhandItem());
            case HOTBAR -> items.subList(0, 9);
            case INVENTORY -> {
                List<ItemStack> all = new ArrayList<>(items);
                all.add(player.getOffhandItem());
                yield all;
            }
        };
    }

    // ---- recipe graph + cache --------------------------------------------

    /** Item -> direct inputs, with memoized depth-limited, cycle-safe closures. */
    static final class Graph<T> {
        private final Map<T, Set<T>> direct;
        private final Map<Integer, Map<T, Set<T>>> closures = new ConcurrentHashMap<>();

        Graph(Map<T, Set<T>> direct) { this.direct = direct; }

        /** Every item reachable from {@code root}'s recipes within {@code depth} levels. */
        Set<T> reachable(T root, int depth) {
            Map<T, Set<T>> byItem = closures.computeIfAbsent(depth, d -> new ConcurrentHashMap<>());
            Set<T> cached = byItem.get(root);
            if (cached != null) return cached;
            Set<T> seen = new HashSet<>();
            ArrayDeque<T> frontier = new ArrayDeque<>();
            frontier.add(root);
            for (int level = 0; level < depth && !frontier.isEmpty(); level++) {
                ArrayDeque<T> next = new ArrayDeque<>();
                for (T item : frontier) {
                    for (T input : direct.getOrDefault(item, Set.of())) {
                        if (seen.add(input)) next.add(input);
                    }
                }
                frontier = next;
            }
            Set<T> result = Collections.unmodifiableSet(seen);
            byItem.put(root, result);
            return result;
        }
    }

    private static final Map<Set<Identifier>, Graph<Item>> GRAPHS = new ConcurrentHashMap<>();
    private static volatile WeakReference<RecipeManager> graphsOwner = new WeakReference<>(null);
    private static volatile RecipeSource testSource;

    /** Drop every cached graph; called on datapack reload and sync. */
    public static void clearCache() {
        GRAPHS.clear();
    }

    /** Test hook: a recipe lookup that needs no server. Pass null to restore the server lookup. */
    static void setSourceForTest(RecipeSource source) {
        testSource = source;
        GRAPHS.clear();
    }

    private static Graph<Item> graphFor(ServerPlayer player, Set<Identifier> types) {
        RecipeSource source = testSource;
        if (source == null) {
            if (!(player.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
            MinecraftServer server = level.getServer();
            if (server == null) return null;
            RecipeManager rm = server.getRecipeManager();
            if (rm == null) return null;
            if (graphsOwner.get() != rm) {
                GRAPHS.clear();
                graphsOwner = new WeakReference<>(rm);
            }
            ContextMap display = new ContextMap.Builder()
                .withParameter(SlotDisplayContext.REGISTRIES, server.registryAccess())
                .create(SlotDisplayContext.CONTEXT);
            source = t -> directInputs(rm, display, t);
        }
        final RecipeSource s = source;
        return GRAPHS.computeIfAbsent(types, t -> new Graph<>(s.directInputs(t)));
    }

    @SuppressWarnings("deprecation")
    private static Map<Item, Set<Item>> directInputs(RecipeManager rm, ContextMap display, Set<Identifier> types) {
        Map<Item, Set<Item>> out = new HashMap<>();
        for (RecipeHolder<?> holder : rm.getRecipes()) {
            Recipe<?> recipe = holder.value();
            Identifier type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
            if (type == null || !types.contains(type)) continue;
            // Special and custom recipes can throw or report nothing; they add no edge.
            try {
                Set<Item> results = new HashSet<>();
                for (RecipeDisplay shown : recipe.display()) {
                    for (ItemStack result : shown.result().resolveForStacks(display)) {
                        if (!result.isEmpty()) results.add(result.getItem());
                    }
                }
                if (results.isEmpty()) continue;
                Set<Item> inputs = new HashSet<>();
                for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                    if (ingredient == null) continue;
                    ingredient.items().forEach(h -> inputs.add(h.value()));
                }
                inputs.remove(Items.AIR);
                if (inputs.isEmpty()) continue;
                for (Item result : results) out.computeIfAbsent(result, k -> new HashSet<>()).addAll(inputs);
            } catch (RuntimeException e) {
                // skip
            }
        }
        return out;
    }
}
