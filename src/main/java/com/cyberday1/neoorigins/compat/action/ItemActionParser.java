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
        "neoorigins:consume", "neoorigins:damage", "neoorigins:set_count");

    public static ItemAction parse(JsonObject json) {
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
                case "neoorigins:and"        -> parseAnd(json);
                case "neoorigins:if_else"    -> parseIfElse(json);
                case "neoorigins:merge_nbt"  -> parseMergeNbt(json);
                case "neoorigins:consume"    -> parseConsume(json);
                case "neoorigins:damage"     -> parseDamage(json);
                case "neoorigins:set_count"  -> parseSetCount(json);
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

    private static ItemAction parseAnd(JsonObject json) {
        List<ItemAction> list = new ArrayList<>();
        for (JsonElement el : com.cyberday1.neoorigins.compat.util.JsonHelpers.asArray(json, "actions")) {
            if (el.isJsonObject()) list.add(parse(el.getAsJsonObject()));
        }
        return s -> { for (ItemAction a : list) a.execute(s); };
    }

    private static ItemAction parseIfElse(JsonObject json) {
        var cond = json.has("condition") && json.get("condition").isJsonObject()
            ? com.cyberday1.neoorigins.compat.condition.ItemConditionParser.parse(json.getAsJsonObject("condition"))
            : com.cyberday1.neoorigins.compat.condition.ItemCondition.alwaysTrue();
        ItemAction ifAction = json.has("if_action") && json.get("if_action").isJsonObject()
            ? parse(json.getAsJsonObject("if_action")) : ItemAction.noop();
        ItemAction elseAction = json.has("else_action") && json.get("else_action").isJsonObject()
            ? parse(json.getAsJsonObject("else_action")) : ItemAction.noop();
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
}
