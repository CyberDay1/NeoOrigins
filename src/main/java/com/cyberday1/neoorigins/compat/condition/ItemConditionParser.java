package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Apoli-style "item condition" JSON into {@link ItemCondition}
 * predicates evaluated against a single ItemStack.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code origins:and} / {@code origins:or} / {@code origins:not} — composition</li>
 *   <li>{@code origins:empty} — true when the stack is empty</li>
 *   <li>{@code origins:nbt} — true when the stack's custom_data NBT contains the given subtree (substring/equality on the SNBT-parsed tag)</li>
 *   <li>{@code origins:enchantment} — checks enchantment level on the stack itself (not equipment slots)</li>
 *   <li>{@code origins:ingredient} — id / tag matching</li>
 * </ul>
 *
 * <p>Honours the universal {@code inverted: true} flag like
 * {@link ConditionParser#parse}.
 *
 * <p>Fails closed (returns false) on malformed JSON.
 */
public final class ItemConditionParser {

    private ItemConditionParser() {}

    public static ItemCondition parse(JsonObject json) {
        if (json == null) return ItemCondition.alwaysTrue();
        boolean inverted = json.has("inverted") && json.get("inverted").getAsBoolean();
        ItemCondition inner = parseInner(json);
        if (!inverted) return inner;
        return s -> !inner.test(s);
    }

    private static ItemCondition parseInner(JsonObject json) {
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Canonicalize prefixes so the dispatcher only needs neoorigins:* arms.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (!type.isEmpty() && !type.startsWith("neoorigins:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }

        try {
            return switch (type) {
                case "neoorigins:and"          -> parseAnd(json);
                case "neoorigins:or"           -> parseOr(json);
                case "neoorigins:not"          -> parseNot(json);
                case "neoorigins:empty"        -> ItemStack::isEmpty;
                case "neoorigins:nbt",
                     "neoorigins:custom_data"  -> parseNbt(json);
                case "neoorigins:enchantment"  -> parseEnchantment(json);
                case "neoorigins:ingredient"   -> parseIngredient(json);
                default -> {
                    // Direct id / tag fields at the top level (Origins also accepts these
                    // without an explicit type).
                    if (json.has("id") || json.has("item")) {
                        String id = json.has("item") ? json.get("item").getAsString() : json.get("id").getAsString();
                        Identifier target = Identifier.parse(id);
                        // 26.1: BuiltInRegistries.ITEM.get returns Optional<Holder<Item>>.
                        Item item = BuiltInRegistries.ITEM.get(target).map(h -> h.value()).orElse(null);
                        if (item == null) {
                            NeoOrigins.LOGGER.debug("[CompatB] item_condition: unknown item '{}' — fails closed", id);
                            yield ItemCondition.alwaysFalse();
                        }
                        yield s -> !s.isEmpty() && s.is(item);
                    }
                    if (json.has("tag")) {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(json.get("tag").getAsString()));
                        yield s -> !s.isEmpty() && s.is(tag);
                    }
                    NeoOrigins.LOGGER.debug("[CompatB] item_condition unsupported type '{}' — defaults to true", type);
                    yield ItemCondition.alwaysTrue();
                }
            };
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] item_condition parse error ({}): {}", type, e.getMessage());
            return ItemCondition.alwaysFalse();
        }
    }

    private static ItemCondition parseAnd(JsonObject json) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<ItemCondition> list = new ArrayList<>();
        for (JsonElement el : arr) if (el.isJsonObject()) list.add(parse(el.getAsJsonObject()));
        return s -> { for (ItemCondition c : list) if (!c.test(s)) return false; return true; };
    }

    private static ItemCondition parseOr(JsonObject json) {
        JsonArray arr = json.has("conditions") ? json.getAsJsonArray("conditions") : new JsonArray();
        List<ItemCondition> list = new ArrayList<>();
        for (JsonElement el : arr) if (el.isJsonObject()) list.add(parse(el.getAsJsonObject()));
        return s -> { for (ItemCondition c : list) if (c.test(s)) return true; return false; };
    }

    private static ItemCondition parseNot(JsonObject json) {
        ItemCondition inner = json.has("condition") && json.get("condition").isJsonObject()
            ? parse(json.getAsJsonObject("condition")) : ItemCondition.alwaysTrue();
        return s -> !inner.test(s);
    }

    /**
     * NBT containment check. Apoli's {@code origins:nbt} condition tests
     * "does the stack's NBT contain this subtree". On 1.21+ vanilla items
     * use data components instead of legacy NBT, so we read the
     * {@code minecraft:custom_data} component (the official escape
     * hatch for arbitrary pack-authored NBT) and run a recursive
     * subtree match.
     *
     * <p>The subtree match is structural: every key in the expected NBT
     * must exist in the actual NBT with an equal-or-containing value.
     * {@code {a:1}} matches both {@code {a:1}} and {@code {a:1, b:2}}.
     */
    private static ItemCondition parseNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null) return ItemCondition.alwaysFalse();
        final CompoundTag expected;
        try {
            // 26.1: TagParser.parseTag is gone; use parseCompoundFully.
            expected = TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] item_condition.nbt: malformed SNBT '{}' — fails closed", snbt);
            return ItemCondition.alwaysFalse();
        }
        return s -> {
            if (s.isEmpty()) return false;
            var customData = s.get(DataComponents.CUSTOM_DATA);
            if (customData == null) return false;
            return tagContains(customData.copyTag(), expected);
        };
    }

    /** Recursive structural-containment match: actual ⊇ expected. */
    private static boolean tagContains(CompoundTag actual, CompoundTag expected) {
        // 26.1: getAllKeys() → keySet().
        for (String key : expected.keySet()) {
            if (!actual.contains(key)) return false;
            Tag exp = expected.get(key);
            Tag act = actual.get(key);
            if (exp instanceof CompoundTag expCt && act instanceof CompoundTag actCt) {
                if (!tagContains(actCt, expCt)) return false;
            } else if (exp != null && !exp.equals(act)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stack-level enchantment check (the on-stack enchantments component,
     * not equipment slots). Used by Apoli pack authors gating behavior
     * on "is this specific item Quick Charge II". Distinct from the
     * entity-level {@code origins:enchantment} condition that walks all
     * equipment slots.
     */
    private static ItemCondition parseEnchantment(JsonObject json) {
        String id = json.has("enchantment") ? json.get("enchantment").getAsString() : null;
        if (id == null) return ItemCondition.alwaysFalse();
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType cmp = ComparisonType.fromString(comp);
        Identifier eid = Identifier.parse(id);
        return s -> {
            if (s.isEmpty()) return false;
            var enchantments = s.getEnchantments();
            int level = 0;
            for (var entry : enchantments.entrySet()) {
                // Resolve key by registry-lookup — Holder.unwrapKey gives the location
                var keyOpt = entry.getKey().unwrapKey();
                // 26.1: ResourceKey.location() → ResourceKey.identifier()
                if (keyOpt.isPresent() && keyOpt.get().identifier().equals(eid)) {
                    level = entry.getIntValue();
                    break;
                }
            }
            return cmp.test(level, target);
        };
    }

    /** Vanilla-recipe-style ingredient: top-level item or tag string. */
    private static ItemCondition parseIngredient(JsonObject json) {
        if (json.has("item")) {
            Identifier target = Identifier.parse(json.get("item").getAsString());
            Item item = BuiltInRegistries.ITEM.get(target).map(h -> h.value()).orElse(null);
            if (item == null) return ItemCondition.alwaysFalse();
            return s -> !s.isEmpty() && s.is(item);
        }
        if (json.has("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(json.get("tag").getAsString()));
            return s -> !s.isEmpty() && s.is(tag);
        }
        return ItemCondition.alwaysFalse();
    }
}
