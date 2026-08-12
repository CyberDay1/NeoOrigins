package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Apoli-style "item condition" JSON into {@link ItemCondition}
 * predicates evaluated against a single ItemStack.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code origins:and} / {@code origins:or} / {@code origins:not} — composition
 *       ({@code all_of} / {@code any_of} are the Apoli 2.9+ renames, same shapes)</li>
 *   <li>{@code origins:empty} — true when the stack is empty</li>
 *   <li>{@code origins:nbt} — legacy-NBT subtree match. The stack's 1.21 data
 *       components are projected back into a pre-1.21 legacy NBT view
 *       ({@code Potion}, {@code Enchantments}, {@code display.Name}, ... — the
 *       read-side mirror of {@link com.cyberday1.neoorigins.compat.LegacyTagToComponents})
 *       merged over {@code minecraft:custom_data}, then matched with
 *       {@link NbtUtils#compareNbt} partial containment</li>
 *   <li>{@code origins:enchantment} — checks enchantment level on the stack itself (not equipment slots)</li>
 *   <li>{@code origins:ingredient} — id / tag matching</li>
 *   <li>{@code origins:amount} — stack-count comparison ({@code comparison}/{@code compare_to})</li>
 *   <li>{@code origins:name} — display-name string equality</li>
 *   <li>{@code origins:food} — item has a food component</li>
 *   <li>{@code origins:meat} — food tagged raw or cooked meat</li>
 *   <li>{@code origins:armor_value} — armour this stack confers ({@code comparison}/{@code compare_to})</li>
 *   <li>{@code origins:harvest_level} — tool tier, recovered from the tier tags that replaced numeric levels in 1.20.5</li>
 *   <li>{@code origins:durability} — remaining durability ({@code comparison}/{@code compare_to})</li>
 *   <li>{@code origins:constant} — fixed {@code value}</li>
 * </ul>
 *
 * <p>Honours the universal {@code inverted: true} flag like
 * {@link ConditionParser#parse}.
 *
 * <p>Fails closed (returns false) on malformed JSON.
 */
public final class ItemConditionParser {

    private ItemConditionParser() {}

    /**
     * Canonical {@code neoorigins:} ids the {@code parseInner} switch accepts —
     * the item-condition analogue of {@link ConditionParser#KNOWN_TYPES}.
     * Exposed so the compat golden-master harness can audit recognition over a
     * corpus against real code. Note the untyped {@code id}/{@code item}/{@code tag}
     * fallback (see {@code parseInner}'s {@code default} arm) is intentionally
     * NOT a verb and not listed here. (Phase-1 registry refactor: this becomes
     * {@code CompatRegistries.itemConditionKeys()} once the switch retires.)
     */
    public static final java.util.Set<String> KNOWN_TYPES = java.util.Set.of(
        "neoorigins:and", "neoorigins:or", "neoorigins:not", "neoorigins:empty",
        "neoorigins:all_of", "neoorigins:any_of",
        "neoorigins:nbt", "neoorigins:custom_data", "neoorigins:enchantment",
        "neoorigins:ingredient", "neoorigins:amount", "neoorigins:name",
        "neoorigins:food", "neoorigins:meat", "neoorigins:armor_value",
        "neoorigins:harvest_level", "neoorigins:durability", "neoorigins:constant");

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
                // all_of/any_of are the Apoli 2.9+ renames of and/or — same shapes
                // (cf. ConditionParser's entity-side aliases).
                case "neoorigins:and",
                     "neoorigins:all_of"       -> parseAnd(json);
                case "neoorigins:or",
                     "neoorigins:any_of"       -> parseOr(json);
                case "neoorigins:not"          -> parseNot(json);
                case "neoorigins:empty"        -> ItemStack::isEmpty;
                case "neoorigins:nbt",
                     "neoorigins:custom_data"  -> parseNbt(json);
                case "neoorigins:enchantment"  -> parseEnchantment(json);
                case "neoorigins:ingredient"   -> parseIngredient(json);
                case "neoorigins:amount"       -> parseAmount(json);
                case "neoorigins:name"         -> parseName(json);
                case "neoorigins:food"         -> s -> s.has(DataComponents.FOOD);
                case "neoorigins:meat"         -> ItemConditionParser::isMeat;
                case "neoorigins:armor_value"  -> parseArmorValue(json);
                case "neoorigins:harvest_level"-> parseHarvestLevel(json);
                case "neoorigins:durability"   -> parseDurability(json);
                case "neoorigins:constant"     -> {
                    boolean value = json.has("value") && json.get("value").getAsBoolean();
                    yield value ? ItemCondition.alwaysTrue() : ItemCondition.alwaysFalse();
                }
                default -> {
                    // Direct id / tag fields at the top level (Origins also accepts these
                    // without an explicit type).
                    if (json.has("id") || json.has("item")) {
                        String id = json.has("item") ? json.get("item").getAsString() : json.get("id").getAsString();
                        ResourceLocation target = ResourceLocation.parse(id);
                        Item item = BuiltInRegistries.ITEM.get(target);
                        yield s -> !s.isEmpty() && s.is(item);
                    }
                    if (json.has("tag")) {
                        TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(json.get("tag").getAsString()));
                        yield s -> !s.isEmpty() && s.is(tag);
                    }
                    com.cyberday1.neoorigins.compat.CompatWarningCollector
                        .recordItemConditionUnsupported(type);
                    yield ItemCondition.alwaysTrue();
                }
            };
        } catch (Exception e) {
            com.cyberday1.neoorigins.compat.CompatWarningCollector
                .recordItemConditionParseError(type, e.getMessage());
            return ItemCondition.alwaysFalse();
        }
    }

    private static ItemCondition parseAnd(JsonObject json) {
        // asArray, not getAsJsonArray: a single-child combinator is routinely
        // authored as a bare object in legacy packs and used to throw here.
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
        List<ItemCondition> list = new ArrayList<>();
        for (JsonElement el : arr) if (el.isJsonObject()) list.add(parse(el.getAsJsonObject()));
        return s -> { for (ItemCondition c : list) if (!c.test(s)) return false; return true; };
    }

    private static ItemCondition parseOr(JsonObject json) {
        JsonArray arr = com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "conditions");
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
     * "does the stack's NBT contain this subtree" — written by 1.20-era
     * pack authors against the pre-component legacy tag layout. On 1.21+
     * that state lives in data components, so we project the components
     * back into a legacy-shaped view (the read-side mirror of
     * {@link com.cyberday1.neoorigins.compat.LegacyTagToComponents}),
     * merged over the stack's {@code minecraft:custom_data}, then run
     * vanilla's partial containment match
     * ({@link NbtUtils#compareNbt}(expected, actual, true)): every key in
     * the expected NBT must exist with an equal-or-containing value, and
     * list elements match if any actual element contains them.
     * {@code {a:1}} matches both {@code {a:1}} and {@code {a:1, b:2}}.
     */
    private static ItemCondition parseNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null) return ItemCondition.alwaysFalse();
        final CompoundTag expected;
        try {
            expected = TagParser.parseTag(snbt);
        } catch (Exception e) {
            com.cyberday1.neoorigins.compat.CompatWarningCollector
                .recordSnbtMalformed("item_condition.nbt", snbt);
            return ItemCondition.alwaysFalse();
        }
        return s -> {
            if (s.isEmpty()) return false;
            return NbtUtils.compareNbt(expected, buildLegacyView(s), true);
        };
    }

    /**
     * Projects the stack's 1.21 data components back into the pre-1.21
     * legacy NBT layout ({@code Potion}, {@code CustomPotionColor},
     * {@code CustomPotionEffects}, {@code Enchantments}/{@code StoredEnchantments},
     * {@code display.Name}/{@code display.Lore}, {@code Damage},
     * {@code RepairCost}, {@code Unbreakable}, {@code CustomModelData}),
     * merged over {@code minecraft:custom_data} as the base.
     *
     * <p>Scalar types mirror the legacy save format exactly, because
     * {@link NbtUtils#compareNbt} uses strict tag equality on leaves:
     * enchantment {@code lvl} is a Short ({@code 5s}), custom-effect
     * {@code amplifier} a Byte — matching what 1.20.1 wrote to disk (and
     * what pack authors copied into their SNBT).
     */
    private static CompoundTag buildLegacyView(ItemStack s) {
        var customData = s.get(DataComponents.CUSTOM_DATA);
        CompoundTag view = customData != null ? customData.copyTag() : new CompoundTag();

        PotionContents potion = s.get(DataComponents.POTION_CONTENTS);
        if (potion != null) {
            potion.potion().flatMap(h -> h.unwrapKey())
                .ifPresent(k -> view.putString("Potion", k.location().toString()));
            potion.customColor().ifPresent(c -> view.putInt("CustomPotionColor", c));
            if (!potion.customEffects().isEmpty()) {
                ListTag effects = new ListTag();
                for (var eff : potion.customEffects()) {
                    CompoundTag e = new CompoundTag();
                    eff.getEffect().unwrapKey()
                        .ifPresent(k -> e.putString("id", k.location().toString()));
                    // Both legacy-capitalised (1.20.1 save format) and modern
                    // lowercase keys; compareNbt ignores the extras.
                    e.putByte("amplifier", (byte) eff.getAmplifier());
                    e.putByte("Amplifier", (byte) eff.getAmplifier());
                    e.putInt("duration", eff.getDuration());
                    e.putInt("Duration", eff.getDuration());
                    effects.add(e);
                }
                view.put("CustomPotionEffects", effects);
                view.put("custom_potion_effects", effects.copy());
            }
        }

        appendLegacyEnchantments(view, "Enchantments", s.get(DataComponents.ENCHANTMENTS));
        appendLegacyEnchantments(view, "StoredEnchantments", s.get(DataComponents.STORED_ENCHANTMENTS));

        CompoundTag display = new CompoundTag();
        Component customName = s.get(DataComponents.CUSTOM_NAME);
        if (customName != null) {
            try {
                display.putString("Name", Component.Serializer.toJson(customName, RegistryAccess.EMPTY));
            } catch (Exception ignored) {}
        }
        ItemLore lore = s.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            ListTag lines = new ListTag();
            for (Component line : lore.lines()) {
                try {
                    lines.add(StringTag.valueOf(Component.Serializer.toJson(line, RegistryAccess.EMPTY)));
                } catch (Exception ignored) {}
            }
            if (!lines.isEmpty()) display.put("Lore", lines);
        }
        if (!display.isEmpty()) view.put("display", display);

        Integer damage = s.get(DataComponents.DAMAGE);
        if (damage != null) view.putInt("Damage", damage);
        Integer repairCost = s.get(DataComponents.REPAIR_COST);
        if (repairCost != null) view.putInt("RepairCost", repairCost);
        if (s.has(DataComponents.UNBREAKABLE)) view.putBoolean("Unbreakable", true);
        var cmd = s.get(DataComponents.CUSTOM_MODEL_DATA);
        if (cmd != null) view.putInt("CustomModelData", cmd.value());

        return view;
    }

    /** Legacy enchantment list: {@code [{id: "minecraft:sharpness", lvl: 5s}]} (lvl as Short, matching the 1.20.1 save format). */
    private static void appendLegacyEnchantments(CompoundTag view, String key, ItemEnchantments enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return;
        ListTag list = new ListTag();
        for (var entry : enchantments.entrySet()) {
            var keyOpt = entry.getKey().unwrapKey();
            if (keyOpt.isEmpty()) continue;
            CompoundTag e = new CompoundTag();
            e.putString("id", keyOpt.get().location().toString());
            e.putShort("lvl", (short) entry.getIntValue());
            list.add(e);
        }
        if (!list.isEmpty()) view.put(key, list);
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
        ResourceLocation eid = ResourceLocation.parse(id);
        return s -> {
            if (s.isEmpty()) return false;
            var enchantments = s.getEnchantments();
            int level = 0;
            for (var entry : enchantments.entrySet()) {
                Enchantment ench = entry.getKey().value();
                // Resolve key by registry-lookup — Holder.unwrapKey gives the location
                var keyOpt = entry.getKey().unwrapKey();
                if (keyOpt.isPresent() && keyOpt.get().location().equals(eid)) {
                    level = entry.getIntValue();
                    break;
                }
            }
            return cmp.test(level, target);
        };
    }

    /** Vanilla-recipe-style ingredient: top-level item or tag string. */
    private static ItemCondition parseIngredient(JsonObject json) {
        // Origins spec nests the actual item/tag inside an "ingredient" key:
        // { "type": "origins:ingredient", "ingredient": { "tag": "..." } }
        // Vanilla Ingredient also accepts the union (array) form —
        // { "ingredient": [ { "tag": "..." }, { "item": "..." } ] } — which
        // matches when ANY entry matches (e.g. Dietary Delights' exemption
        // list over the ignore_diet + vegetarian tags). Unwrap whichever
        // shape is present; otherwise check item/tag at the top level.
        JsonElement nested = json.get("ingredient");
        if (nested != null && nested.isJsonArray()) {
            List<ItemCondition> entries = new ArrayList<>();
            for (JsonElement el : nested.getAsJsonArray()) {
                if (el.isJsonObject()) entries.add(parseIngredientEntry(el.getAsJsonObject()));
            }
            if (entries.isEmpty()) return ItemCondition.alwaysFalse();
            return s -> { for (ItemCondition c : entries) if (c.test(s)) return true; return false; };
        }
        JsonObject effective = nested != null && nested.isJsonObject()
            ? nested.getAsJsonObject() : json;
        return parseIngredientEntry(effective);
    }

    /** One ingredient entry: an object holding an {@code item} id or a {@code tag}. */
    private static ItemCondition parseIngredientEntry(JsonObject effective) {
        if (effective.has("item")) {
            ResourceLocation target = ResourceLocation.parse(effective.get("item").getAsString());
            Item item = BuiltInRegistries.ITEM.get(target);
            return s -> !s.isEmpty() && s.is(item);
        }
        if (effective.has("tag")) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, ResourceLocation.parse(effective.get("tag").getAsString()));
            return s -> !s.isEmpty() && s.is(tag);
        }
        return ItemCondition.alwaysFalse();
    }

    /**
     * Apoli's {@code origins:meat}. Pre-1.21 it read {@code FoodProperties.isMeat()},
     * a flag Mojang deleted when food moved into data components — so there is no
     * literal equivalent left to translate to. The NeoForge common food tags are the
     * closest thing with the same intent and, unlike a hardcoded item list, they pick
     * up modded meats for free, which is what a pack author writing "no meat" for a
     * herbivore origin actually wants.
     */
    static boolean isMeat(ItemStack s) {
        return !s.isEmpty()
            && (s.is(net.neoforged.neoforge.common.Tags.Items.FOODS_RAW_MEAT)
             || s.is(net.neoforged.neoforge.common.Tags.Items.FOODS_COOKED_MEAT));
    }

    /**
     * Apoli's {@code origins:armor_value} in its item-condition form: the armour this
     * one stack confers, not the wearer's total (that is the identically named entity
     * condition, handled in {@link ConditionParser}). Read off
     * {@code minecraft:attribute_modifiers} rather than casting to {@code ArmorItem}:
     * since 1.20.5 armour values are data-driven components, so the component route
     * covers datapack-defined and modded armour that a class cast would miss — and
     * {@code ArmorItem} no longer exists at all on the 26.x branches.
     */
    private static ItemCondition parseArmorValue(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        double target = json.has("compare_to") ? json.get("compare_to").getAsDouble() : 0.0;
        ComparisonType cmp = ComparisonType.fromString(comp);
        return s -> cmp.test(armorValueOf(s), target);
    }

    /** Sum of flat {@code minecraft:armor} bonuses on the stack; 0 for anything that grants none. */
    static double armorValueOf(ItemStack s) {
        if (s.isEmpty()) return 0.0;
        // NeoForge's accessor, not the raw component: vanilla armour leaves
        // minecraft:attribute_modifiers empty on the stack and supplies the
        // values from the item's defaults, so reading the component directly
        // reports 0 armour for a diamond chestplate.
        ItemAttributeModifiers mods = s.getAttributeModifiers();
        double total = 0.0;
        for (ItemAttributeModifiers.Entry entry : mods.modifiers()) {
            if (entry.attribute().value() != Attributes.ARMOR.value()) continue;
            // Only flat additions: a percentage modifier has no meaning without a
            // base to apply it to, and vanilla armour never uses one.
            if (entry.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) continue;
            total += entry.modifier().amount();
        }
        return total;
    }

    /**
     * Apoli's {@code origins:harvest_level}, on versions where Minecraft no longer has
     * one. Numeric harvest levels were removed in 1.20.5 and replaced by per-tier
     * "blocks this tool cannot drop" tags, so the level is recovered from the tag the
     * stack's {@code minecraft:tool} component denies drops for. That keeps the Apoli
     * numbering (wood/gold 0, stone 1, iron 2, diamond 3, netherite 4) for both vanilla
     * and modded tools that follow the vanilla tier tags, and yields 0 for anything that
     * is not a tool.
     */
    private static ItemCondition parseHarvestLevel(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType cmp = ComparisonType.fromString(comp);
        return s -> cmp.test(harvestLevelOf(s), target);
    }

    /** Apoli tier numbering keyed by the vanilla {@code incorrect_for_*_tool} block tag. */
    private static final java.util.Map<String, Integer> TIER_LEVEL_BY_TAG = java.util.Map.of(
        "incorrect_for_wooden_tool",    0,
        "incorrect_for_gold_tool",      0,
        "incorrect_for_stone_tool",     1,
        // 26.x adds a copper tier between stone and iron; Apoli predates it and
        // has no number for it, so it shares stone's. On 1.21.1 this never matches.
        "incorrect_for_copper_tool",    1,
        "incorrect_for_iron_tool",      2,
        "incorrect_for_diamond_tool",   3,
        "incorrect_for_netherite_tool", 4);

    static int harvestLevelOf(ItemStack s) {
        if (s.isEmpty()) return 0;
        Tool tool = s.get(DataComponents.TOOL);
        if (tool == null) return 0;
        int level = 0;
        for (Tool.Rule rule : tool.rules()) {
            // The tier is named by the deny rule ("cannot drop these blocks"), not
            // by the mining-speed rules, which are keyed on material instead.
            if (rule.correctForDrops().orElse(true)) continue;
            var tagKey = rule.blocks().unwrapKey().orElse(null);
            if (tagKey == null) continue;
            Integer mapped = TIER_LEVEL_BY_TAG.get(tagKey.location().getPath());
            if (mapped != null) level = Math.max(level, mapped);
        }
        return level;
    }

    /**
     * Apoli's {@code origins:durability}: remaining durability, i.e. how many more hits
     * the stack has left. Non-damageable items report 0 — matching Apoli, where a stone
     * block is not "infinitely durable" but simply has no durability.
     */
    private static ItemCondition parseDurability(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 0;
        ComparisonType cmp = ComparisonType.fromString(comp);
        return s -> cmp.test(remainingDurabilityOf(s), target);
    }

    static int remainingDurabilityOf(ItemStack s) {
        if (s.isEmpty() || !s.isDamageableItem()) return 0;
        return s.getMaxDamage() - s.getDamageValue();
    }

    /** Stack-count comparison: {@code comparison} (default {@code >=}) against {@code compare_to} (default 1). */
    private static ItemCondition parseAmount(JsonObject json) {
        String comp = json.has("comparison") ? json.get("comparison").getAsString() : ">=";
        int target = json.has("compare_to") ? json.get("compare_to").getAsInt() : 1;
        ComparisonType cmp = ComparisonType.fromString(comp);
        return s -> cmp.test(s.getCount(), target);
    }

    /** Display-name string equality against the stack's hover name (custom name, else default item name). */
    private static ItemCondition parseName(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        if (name == null) return ItemCondition.alwaysFalse();
        return s -> !s.isEmpty() && s.getHoverName().getString().equals(name);
    }
}
