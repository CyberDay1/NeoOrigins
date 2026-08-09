package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses Apoli-style "item action" JSON into {@link ItemAction} consumers
 * that mutate a single ItemStack.
 *
 * <p>Supported types:
 * <ul>
 *   <li>{@code origins:and} — chain multiple item actions in order</li>
 *   <li>{@code origins:if_else} — branch on an item-condition (over the stack)</li>
 *   <li>{@code origins:merge_nbt} — merge SNBT into the stack's NBT-state, with
 *       known top-level keys (CustomModelData, Charged, ...) routed to their
 *       vanilla data components and unknown keys merged into {@code custom_data}</li>
 *   <li>{@code origins:consume} — shrink the stack by N (default 1)</li>
 *   <li>{@code origins:damage} — damage the stack by N</li>
 *   <li>{@code origins:set_count} — set the stack count outright</li>
 *   <li>{@code origins:remove_enchantment} - strip named enchantments from the
 *       stack, optionally resetting its anvil repair cost</li>
 * </ul>
 *
 * <p>Fail-soft: on parse error or unsupported type, returns a no-op action
 * and logs a warning.
 */
public final class ItemActionParser {

    private ItemActionParser() {}

    /**
     * Canonical {@code neoorigins:} ids the {@code parse} switch accepts —
     * the item-action analogue of {@link ActionParser#KNOWN_TYPES}. Exposed
     * so the compat golden-master harness can audit recognition over a corpus
     * against real code rather than a private transcription. (Phase-1 registry
     * refactor: this becomes {@code CompatRegistries.itemActionKeys()} once the
     * switch retires.)
     */
    public static final java.util.Set<String> KNOWN_TYPES = java.util.Set.of(
        "neoorigins:and", "neoorigins:if_else", "neoorigins:merge_nbt",
        "neoorigins:consume", "neoorigins:damage", "neoorigins:set_count",
        "neoorigins:remove_enchantment");

    public static ItemAction parse(JsonObject json) {
        return parse(json, null);
    }

    /**
     * Parse an item action that may inherit fields from the object carrying it.
     *
     * @param json      the item-action object itself
     * @param enclosing the object the item action hangs off (an
     *                  {@code equipped_item_action} / {@code modify_inventory}),
     *                  or {@code null} when there is none. Only consulted for
     *                  fields packs are observed to place on either side; see
     *                  {@link #resolveResetRepairCost}.
     */
    public static ItemAction parse(JsonObject json, JsonObject enclosing) {
        if (json == null) return ItemAction.noop();
        String type = json.has("type") ? json.get("type").getAsString() : "";
        // Canonicalize prefixes — same convention as ActionParser/ConditionParser.
        if (!type.isEmpty() && type.indexOf(':') < 0) {
            type = "neoorigins:" + type;
        } else if (!type.isEmpty() && !type.startsWith("neoorigins:")) {
            type = "neoorigins:" + type.substring(type.indexOf(':') + 1);
        }
        try {
            return switch (type) {
                case "neoorigins:and"        -> parseAnd(json, enclosing);
                case "neoorigins:if_else"    -> parseIfElse(json, enclosing);
                case "neoorigins:merge_nbt"  -> parseMergeNbt(json);
                case "neoorigins:consume"    -> parseConsume(json);
                case "neoorigins:damage"     -> parseDamage(json);
                case "neoorigins:set_count"  -> parseSetCount(json);
                case "neoorigins:remove_enchantment" -> parseRemoveEnchantment(json, enclosing);
                default -> {
                    com.cyberday1.neoorigins.compat.CompatWarningCollector
                        .recordItemActionUnsupported(type);
                    yield ItemAction.noop();
                }
            };
        } catch (Exception e) {
            com.cyberday1.neoorigins.compat.CompatWarningCollector
                .recordItemActionParseError(type, e.getMessage());
            return ItemAction.noop();
        }
    }

    // The combinators forward `enclosing` so a remove_enchantment nested one
    // level down (inside an `and`, or an if_else branch) still sees the
    // reset_repair_cost the pack put on the equipped_item_action above it.
    private static ItemAction parseAnd(JsonObject json, JsonObject enclosing) {
        List<ItemAction> list = new ArrayList<>();
        for (JsonElement el : com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "actions")) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject(), enclosing));
        }
        return s -> { for (ItemAction a : list) a.execute(s); };
    }

    private static ItemAction parseIfElse(JsonObject json, JsonObject enclosing) {
        var cond = json.has("condition") && json.get("condition").isJsonObject()
            ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(json.getAsJsonObject("condition"))
            : com.cyberday1.neoorigins.compat.condition.ItemCondition.alwaysTrue();
        ItemAction ifAction = json.has("if_action") && json.get("if_action").isJsonObject()
            ? parse(json.getAsJsonObject("if_action"), enclosing) : ItemAction.noop();
        ItemAction elseAction = json.has("else_action") && json.get("else_action").isJsonObject()
            ? parse(json.getAsJsonObject("else_action"), enclosing) : ItemAction.noop();
        return s -> {
            if (cond.test(s)) ifAction.execute(s);
            else elseAction.execute(s);
        };
    }

    /**
     * Apoli's merge_nbt was authored against pre-1.21 ItemStack NBT. On
     * 1.21+ items use data components, with {@code minecraft:custom_data}
     * as the official escape hatch for arbitrary pack-authored NBT.
     *
     * <p>Strategy: parse the SNBT, then for each top-level key:
     * <ul>
     *   <li>{@code CustomModelData} — set the int component</li>
     *   <li>{@code Charged} (crossbow flag) — translated to charged_projectiles
     *       presence/absence</li>
     *   <li>everything else — merged into {@code custom_data}</li>
     * </ul>
     *
     * <p>Pack authors who wrote against 1.20-shape NBT (Unbreakable,
     * Enchantments, display.Name, etc.) will see those keys land in
     * custom_data harmlessly but without the visual effect they expected.
     * The gameplay-state keys (the misch pack's {@code _weapon_mode},
     * {@code _bullet}, etc.) work correctly. Pack authors who need
     * vanilla-component edits should use a dedicated action verb.
     */
    private static ItemAction parseMergeNbt(JsonObject json) {
        String snbt = json.has("nbt") ? json.get("nbt").getAsString() : null;
        if (snbt == null) return ItemAction.noop();
        final CompoundTag tagToMerge;
        try {
            tagToMerge = TagParser.parseTag(snbt);
        } catch (Exception e) {
            com.cyberday1.neoorigins.compat.CompatWarningCollector
                .recordSnbtMalformed("merge_nbt", snbt);
            return ItemAction.noop();
        }
        return stack -> {
            if (stack.isEmpty()) return;
            // Delegate to the shared legacy-tag translator: it routes Potion,
            // CustomModelData, Damage, Unbreakable, RepairCost, display.Name/Lore
            // to dedicated components and dumps the rest into custom_data.
            // Enchantments need a RegistryAccess (datapack registry on 1.21+);
            // merge_nbt has no player handy so they're skipped with a debug log.
            com.cyberday1.neoorigins.compat.LegacyTagToComponents.applyTo(stack, tagToMerge, null);
        };
    }

    private static ItemAction parseConsume(JsonObject json) {
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
        return s -> { if (!s.isEmpty()) s.shrink(amount); };
    }

    private static ItemAction parseDamage(JsonObject json) {
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1;
        boolean ignoreUnbreaking = json.has("ignore_unbreaking") && json.get("ignore_unbreaking").getAsBoolean();
        return s -> {
            if (s.isEmpty() || !s.isDamageableItem()) return;
            // Direct damage-value bump avoids needing an entity reference.
            // Unbreaking enchantment skip is honoured when ignore_unbreaking
            // is true; otherwise we apply the damage as-is.
            if (ignoreUnbreaking) {
                s.setDamageValue(s.getDamageValue() + amount);
            } else {
                s.setDamageValue(s.getDamageValue() + amount);
            }
            if (s.getDamageValue() >= s.getMaxDamage()) s.shrink(s.getCount());
        };
    }

    private static ItemAction parseSetCount(JsonObject json) {
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        return s -> { if (!s.isEmpty()) s.setCount(count); };
    }

    /**
     * {@code remove_enchantment}: strip the named enchantments off the stack,
     * optionally clearing its accumulated anvil repair cost. The classic use is
     * a "Remove Curse" power that ticks over the worn armour and held weapon
     * once a second stripping {@code vanishing_curse} / {@code binding_curse}.
     *
     * <p><b>Two shapes are accepted for both of its fields.</b> The upstream
     * Apoli docs describe a singular {@code enchantment} string plus a
     * {@code reset_repair_cost} flag on this object; packs in the wild instead
     * write a plural {@code enchantments} array and hang
     * {@code reset_repair_cost} off the enclosing {@code equipped_item_action}
     * beside {@code item_action}. Both are read, exactly as
     * {@code parseEquippedItemAction} already reads both {@code item_action}
     * and {@code action}, and for the same reason: the alternative is a silent
     * no-op that looks to the pack author like the power simply does nothing.
     *
     * <p>Ids are matched by resource location against the enchantments actually
     * present on the stack, so no registry access is needed (an {@link ItemAction}
     * only ever gets the stack). An id that is not on the stack, including one no
     * datapack ever registered, matches nothing and is skipped; a malformed id
     * warns once at parse time and is dropped.
     *
     * <p>Enchanted books are handled: the removal goes through
     * {@code EnchantmentHelper.updateEnchantments}, which is vanilla's own
     * dispatch onto {@code stored_enchantments} for books and
     * {@code enchantments} for everything else.
     */
    private static ItemAction parseRemoveEnchantment(JsonObject json, JsonObject enclosing) {
        final List<net.minecraft.resources.ResourceLocation> ids = resolveEnchantmentIds(json);
        final boolean resetRepairCost = resolveResetRepairCost(json, enclosing);
        if (ids.isEmpty() && !resetRepairCost) return ItemAction.noop();
        return stack -> {
            if (stack.isEmpty()) return;
            if (!ids.isEmpty()) {
                net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(
                    stack, mutable -> removeMatching(mutable, ids));
            }
            if (resetRepairCost) {
                stack.set(net.minecraft.core.component.DataComponents.REPAIR_COST, 0);
            }
        };
    }

    /**
     * Collect the enchantment ids a {@code remove_enchantment} names, from
     * either the documented singular {@code enchantment} key or the plural
     * {@code enchantments} key packs actually ship. Each key accepts a bare
     * string or an array of strings; both keys may be present at once, in which
     * case the union is used. Order is preserved and duplicates collapse.
     *
     * <p>Malformed ids warn and are dropped rather than throwing, so one bad
     * entry cannot take the whole power down with it.
     *
     * <p>Pure and static so the shape tolerance is directly testable: a
     * parse-only test would pass on a parser that recognises the type and then
     * resolves nothing.
     */
    static List<net.minecraft.resources.ResourceLocation> resolveEnchantmentIds(JsonObject json) {
        java.util.LinkedHashSet<net.minecraft.resources.ResourceLocation> out = new java.util.LinkedHashSet<>();
        if (json != null) {
            collectEnchantmentIds(json.get("enchantment"), out);
            collectEnchantmentIds(json.get("enchantments"), out);
        }
        return List.copyOf(out);
    }

    private static void collectEnchantmentIds(
            JsonElement el, java.util.Collection<net.minecraft.resources.ResourceLocation> out) {
        if (el == null || el.isJsonNull()) return;
        if (el.isJsonArray()) {
            for (JsonElement child : el.getAsJsonArray()) collectEnchantmentIds(child, out);
            return;
        }
        if (!el.isJsonPrimitive()) return;
        String raw = el.getAsString();
        net.minecraft.resources.ResourceLocation id =
            net.minecraft.resources.ResourceLocation.tryParse(raw);
        if (id == null) {
            NeoOrigins.LOGGER.warn(
                "[CompatB] remove_enchantment: '{}' is not a valid enchantment id - skipped", raw);
            return;
        }
        out.add(id);
    }

    /**
     * Resolve {@code reset_repair_cost}. Upstream documents it on the
     * {@code remove_enchantment} object; the packs that prompted this read it
     * off the enclosing {@code equipped_item_action}, as a sibling of
     * {@code item_action}. Either position winning on {@code true} means being
     * wrong about which one upstream really blesses costs nothing.
     *
     * <p>Pure and static so both positions are directly testable.
     */
    static boolean resolveResetRepairCost(JsonObject json, JsonObject enclosing) {
        return readBooleanFlag(json, "reset_repair_cost")
            || readBooleanFlag(enclosing, "reset_repair_cost");
    }

    private static boolean readBooleanFlag(JsonObject json, String key) {
        if (json == null || !json.has(key)) return false;
        JsonElement el = json.get(key);
        if (!el.isJsonPrimitive()) return false;
        try {
            return el.getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Drop every enchantment whose registry id is in {@code ids}. Matching is by
     * {@link net.minecraft.resources.ResourceLocation}, resolved off the holder
     * already on the stack, which is what lets this work without a registry
     * lookup - the same approach {@code ItemConditionParser}'s
     * {@code enchantment} condition uses.
     *
     * <p>Pure and static so the removal itself is unit-testable.
     */
    static void removeMatching(
            net.minecraft.world.item.enchantment.ItemEnchantments.Mutable mutable,
            java.util.Collection<net.minecraft.resources.ResourceLocation> ids) {
        if (mutable == null || ids == null || ids.isEmpty()) return;
        mutable.removeIf(holder -> holder.unwrapKey()
            .map(key -> ids.contains(key.location()))
            .orElse(false));
    }
}
