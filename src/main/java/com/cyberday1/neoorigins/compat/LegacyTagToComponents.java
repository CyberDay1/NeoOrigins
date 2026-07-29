package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Translates pre-1.21 ItemStack NBT into 1.21+ data components. Pack
 * authors who wrote against legacy Origins/Apoli expressed item state
 * as flat NBT keys ({@code Enchantments}, {@code Potion}, {@code Unbreakable},
 * {@code display.Name}, etc.) — on 1.21+ each of those lives in its own
 * data component.
 *
 * <p>Pack-author intent recovery, not a faithful round-trip: keys that
 * have a known component target are routed; everything else falls into
 * {@code minecraft:custom_data} so {@code origins:nbt} conditions still
 * see it. Values that need datapack-registry lookups (enchantments,
 * potions referenced by id) require {@link RegistryAccess}; if the
 * caller doesn't have one, those keys are skipped with a debug log.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code ItemActionParser.parseMergeNbt} — runtime merge into the
 *       acted-on stack (no RegistryAccess available)</li>
 *   <li>{@code StartingEquipmentPower} — applies the {@code legacy_tag}
 *       field at grant time (player → registries available)</li>
 *   <li>{@link LegacyCommandRewriter} — rewrites {@code give <item>{snbt}}
 *       in legacy {@code .mcfunction} files to {@code <item>[components]}
 *       via {@link #toComponentString}</li>
 * </ul>
 *
 * <p>There are therefore two entry points into one mapping: {@link #applyTo}
 * mutates a live {@link ItemStack}, {@link #toComponentString} renders the
 * same mapping as command-argument text. They are driven off the same
 * {@link #RECOGNISED_KEYS} set so they cannot silently drift; see
 * {@code LegacyTagToComponentsTest}.
 */
public final class LegacyTagToComponents {

    private LegacyTagToComponents() {}

    /** Keys handled by the dedicated-component path; everything else goes to custom_data. */
    private static final Set<String> RECOGNISED_KEYS = Set.of(
        "Potion",
        "CustomModelData",
        "Damage",
        "Unbreakable",
        "RepairCost",
        "Enchantments",
        "ench",          // legacy 1.12-style key
        "StoredEnchantments",
        "display"        // contains Name/Lore as nested compound
    );

    /** Read-only view for parity assertions between the two entry points. */
    public static Set<String> recognisedKeys() {
        return RECOGNISED_KEYS;
    }

    /**
     * Parse SNBT and apply the recognised keys to the stack as data
     * components. Returns {@code true} on parse success, {@code false}
     * (no-op) on malformed input. Logs a warning on parse failure.
     */
    public static boolean applySnbt(ItemStack stack, String snbt, @Nullable RegistryAccess registries) {
        if (snbt == null || snbt.isEmpty()) return true;
        CompoundTag tag;
        try {
            tag = TagParser.parseTag(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] LegacyTagToComponents: malformed SNBT '{}' — no-op", snbt);
            return false;
        }
        applyTo(stack, tag, registries);
        return true;
    }

    public static void applyTo(ItemStack stack, CompoundTag tag, @Nullable RegistryAccess registries) {
        if (stack.isEmpty() || tag == null) return;

        if (tag.contains("Potion")) applyPotion(stack, tag.getString("Potion"));
        if (tag.contains("CustomModelData")) applyCustomModelData(stack, tag.getInt("CustomModelData"));
        if (tag.contains("Damage")) {
            int dmg = tag.getInt("Damage");
            if (stack.isDamageableItem()) stack.setDamageValue(dmg);
        }
        if (tag.contains("Unbreakable")) {
            // Pre-1.21 stored as a 1b flag; 1.21+ has an Unbreakable record
            // with a show_in_tooltip boolean. Default to true to match
            // legacy display behaviour.
            stack.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        }
        if (tag.contains("RepairCost")) stack.set(DataComponents.REPAIR_COST, tag.getInt("RepairCost"));

        if (tag.contains("display")) applyDisplay(stack, tag.getCompound("display"));

        if (registries != null) {
            if (tag.contains("Enchantments")) applyEnchantments(stack, tag.getList("Enchantments", Tag.TAG_COMPOUND), registries, false);
            if (tag.contains("ench"))         applyEnchantments(stack, tag.getList("ench",         Tag.TAG_COMPOUND), registries, false);
            if (tag.contains("StoredEnchantments")) applyEnchantments(stack, tag.getList("StoredEnchantments", Tag.TAG_COMPOUND), registries, true);
        } else {
            if (tag.contains("Enchantments") || tag.contains("ench") || tag.contains("StoredEnchantments")) {
                NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: enchantments present but no RegistryAccess — skipped");
            }
        }

        // Anything not in the recognised set lands in custom_data so origins:nbt
        // conditions and other arbitrary-NBT mechanisms still see it.
        CompoundTag leftover = leftoverOf(tag);
        if (leftover != null) {
            CustomData existing = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
            CompoundTag merged = existing.copyTag();
            for (String key : leftover.getAllKeys()) {
                Tag v = leftover.get(key);
                if (v != null) merged.put(key, v);
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        }
    }

    private static void applyPotion(ItemStack stack, String potionId) {
        if (potionId == null || potionId.isEmpty()) return;
        ResourceLocation id = ResourceLocation.tryParse(potionId);
        if (id == null) {
            NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: unparseable potion '{}'", potionId);
            return;
        }
        var potionOpt = BuiltInRegistries.POTION.getHolder(ResourceKey.create(Registries.POTION, id));
        if (potionOpt.isEmpty()) {
            NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: unknown potion '{}'", potionId);
            return;
        }
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potionOpt.get()));
    }

    private static void applyCustomModelData(ItemStack stack, int cmd) {
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new net.minecraft.world.item.component.CustomModelData(cmd));
    }

    private static void applyDisplay(ItemStack stack, CompoundTag display) {
        if (display == null) return;
        if (display.contains("Name")) {
            try {
                Component name = Component.Serializer.fromJson(display.getString("Name"),
                    net.minecraft.core.RegistryAccess.EMPTY);
                if (name != null) stack.set(DataComponents.CUSTOM_NAME, name);
            } catch (Exception e) {
                NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: bad display.Name JSON: {}", e.getMessage());
            }
        }
        if (display.contains("Lore")) {
            ListTag loreList = display.getList("Lore", Tag.TAG_STRING);
            List<Component> lore = new ArrayList<>();
            for (int i = 0; i < loreList.size(); i++) {
                try {
                    Component c = Component.Serializer.fromJson(loreList.getString(i),
                        net.minecraft.core.RegistryAccess.EMPTY);
                    if (c != null) lore.add(c);
                } catch (Exception ignored) {}
            }
            if (!lore.isEmpty()) stack.set(DataComponents.LORE, new ItemLore(lore));
        }
    }

    private static void applyEnchantments(ItemStack stack, ListTag list, RegistryAccess registries, boolean stored) {
        if (list == null || list.isEmpty()) return;
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments base = stack.getOrDefault(
            stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
            ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(base);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String idStr = entry.getString("id");
            // Legacy "lvl" was a short; 1.21+ uses int but Mojang's ItemEnchantments expects int.
            int level = entry.contains("lvl") ? entry.getInt("lvl") : entry.getInt("level");
            if (idStr.isEmpty() || level <= 0) continue;
            ResourceLocation eid = ResourceLocation.tryParse(idStr);
            if (eid == null) continue;
            ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, eid);
            lookup.get(key).ifPresent(h -> mutable.set(h, level));
        }
        stack.set(stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
            mutable.toImmutable());
    }

    // ── string rendering (command-argument form) ─────────────────────────────

    /**
     * Render a legacy 1.20 item tag as the 1.21 component bracket-syntax body,
     * e.g. {@code [minecraft:custom_name={"text":"Magic Bean"},minecraft:enchantments=...]}.
     *
     * <p>This is the string twin of {@link #applyTo} and covers exactly the same
     * {@link #RECOGNISED_KEYS}; anything else lands in {@code minecraft:custom_data}
     * rather than being dropped. Deliberately registry-free — the resource layer
     * calls this at pack-read time, long before {@code RegistryAccess} exists, so
     * ids are emitted as strings and resolved later by the command parser itself.
     *
     * @return the bracket body including the enclosing {@code [ ]}, or an empty
     *         string when the tag maps to nothing at all.
     */
    public static String toComponentString(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return "";

        // HideFlags is a pre-1.21 tooltip bitfield. Bit 0 (value 1) hid the
        // enchantment list, which 1.21 expresses as show_in_tooltip:false on the
        // enchantments component. The remaining bits (attribute modifiers,
        // unbreakable, can_destroy, …) each moved onto a different component and
        // are not worth guessing at, so they are dropped rather than risked.
        boolean hideEnchantments = false;
        if (tag.contains("HideFlags")) {
            int flags = tag.getInt("HideFlags");
            hideEnchantments = (flags & 1) != 0;
            if ((flags & ~1) != 0) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyTagToComponents: HideFlags bits 0x{} have no cheap 1.21 equivalent — dropped",
                    Integer.toHexString(flags & ~1));
            }
        }

        List<String> parts = new ArrayList<>();

        if (tag.contains("Potion")) {
            String potion = tag.getString("Potion");
            // PotionContents.CODEC accepts a bare potion id as an alternative to
            // the full record, so the legacy string carries over verbatim.
            if (!potion.isEmpty()) parts.add("minecraft:potion_contents=" + StringTag.quoteAndEscape(potion));
        }
        if (tag.contains("CustomModelData")) parts.add("minecraft:custom_model_data=" + tag.getInt("CustomModelData"));
        if (tag.contains("Damage"))          parts.add("minecraft:damage=" + tag.getInt("Damage"));
        if (tag.contains("RepairCost"))      parts.add("minecraft:repair_cost=" + tag.getInt("RepairCost"));
        // 1.20's Unbreakable:1b flag → the 1.21 record; the empty compound takes
        // show_in_tooltip's `true` default, matching legacy display behaviour.
        if (tag.contains("Unbreakable"))     parts.add("minecraft:unbreakable={}");

        if (tag.contains("display")) appendDisplay(parts, tag.getCompound("display"));

        // Enchantments and the 1.12-era `ench` alias both target ONE component;
        // the command parser rejects a repeated component key, so they merge.
        ListTag worn = new ListTag();
        worn.addAll(tag.getList("Enchantments", Tag.TAG_COMPOUND));
        worn.addAll(tag.getList("ench", Tag.TAG_COMPOUND));
        appendEnchantments(parts, "minecraft:enchantments", worn, hideEnchantments);
        appendEnchantments(parts, "minecraft:stored_enchantments",
            tag.getList("StoredEnchantments", Tag.TAG_COMPOUND), hideEnchantments);

        CompoundTag leftover = leftoverOf(tag);
        if (leftover != null) parts.add("minecraft:custom_data=" + leftover);

        return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
    }

    /** Everything outside {@link #RECOGNISED_KEYS}, or null when there is none. */
    @Nullable
    private static CompoundTag leftoverOf(CompoundTag tag) {
        CompoundTag leftover = null;
        for (String key : tag.getAllKeys()) {
            if (RECOGNISED_KEYS.contains(key)) continue;
            Tag value = tag.get(key);
            if (value == null) continue;
            if (leftover == null) leftover = new CompoundTag();
            leftover.put(key, value);
        }
        return leftover;
    }

    private static void appendDisplay(List<String> parts, CompoundTag display) {
        if (display == null) return;
        if (display.contains("Name")) {
            String name = asComponentSnbt(display.getString("Name"));
            if (name != null) parts.add("minecraft:custom_name=" + name);
        }
        if (display.contains("Lore")) {
            ListTag loreList = display.getList("Lore", Tag.TAG_STRING);
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < loreList.size(); i++) {
                String line = asComponentSnbt(loreList.getString(i));
                if (line != null) lines.add(line);
            }
            // ItemLore.CODEC is a size-limited list over the same flat codec.
            // SNBT lists are homogeneous and asComponentSnbt always yields a
            // string, so the list is well-formed by construction.
            if (!lines.isEmpty()) parts.add("minecraft:lore=[" + String.join(",", lines) + "]");
        }
    }

    /**
     * Turn a legacy {@code display.Name}/{@code Lore} entry into SNBT that the
     * chat-component codec can read.
     *
     * <p>{@code minecraft:custom_name} and {@code minecraft:lore} are backed by
     * {@code ComponentSerialization.FLAT_CODEC}, which reads a <em>string</em>
     * and JSON-parses its contents — not an SNBT compound. That is exactly the
     * shape legacy {@code display.Name} already had, so a valid legacy entry
     * rides through verbatim; only the SNBT quoting is added. Text that isn't
     * JSON is promoted to a JSON string literal so it reads back as plain text.
     *
     * @return SNBT for one component, or null if the entry is unusable.
     */
    @Nullable
    private static String asComponentSnbt(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        String trimmed = raw.trim();
        char first = trimmed.charAt(0);
        if (first == '{' || first == '[' || first == '"') {
            try {
                JsonParser.parseString(trimmed);
                return StringTag.quoteAndEscape(trimmed);
            } catch (Exception e) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyTagToComponents: display text '{}' is not valid JSON — treated as literal text",
                    trimmed);
            }
        }
        return StringTag.quoteAndEscape(new JsonPrimitive(raw).toString());
    }

    private static void appendEnchantments(List<String> parts, String component, ListTag list, boolean hideInTooltip) {
        if (list == null || list.isEmpty()) return;
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            String id = entry.getString("id");
            int level = entry.contains("lvl") ? entry.getInt("lvl") : entry.getInt("level");
            if (id.isEmpty() || level <= 0) continue;
            if (ResourceLocation.tryParse(id) == null) continue;
            levels.add(StringTag.quoteAndEscape(id) + ":" + level);
        }
        if (levels.isEmpty()) return;
        String map = "{" + String.join(",", levels) + "}";
        // ItemEnchantments.CODEC takes the bare levels map as an alternative to
        // the full record; the record form is only needed to carry the flag.
        parts.add(component + "=" + (hideInTooltip
            ? "{levels:" + map + ",show_in_tooltip:false}"
            : map));
    }
}
