package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
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
 * {@code LegacyTagToComponentsStringTest}.
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
        "display"        // skipped on 26.1 — see applyTo() comment
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
            // 26.1: TagParser.parseTag is gone; use parseCompoundFully.
            tag = TagParser.parseCompoundFully(snbt);
        } catch (Exception e) {
            NeoOrigins.LOGGER.warn("[CompatB] LegacyTagToComponents: malformed SNBT '{}' — no-op", snbt);
            return false;
        }
        applyTo(stack, tag, registries);
        return true;
    }

    public static void applyTo(ItemStack stack, CompoundTag tag, @Nullable RegistryAccess registries) {
        if (stack.isEmpty() || tag == null) return;

        if (tag.contains("Potion")) applyPotion(stack, tag.getStringOr("Potion", ""));
        if (tag.contains("CustomModelData")) applyCustomModelData(stack, tag.getIntOr("CustomModelData", 0));
        if (tag.contains("Damage")) {
            int dmg = tag.getIntOr("Damage", 0);
            if (stack.isDamageableItem()) stack.setDamageValue(dmg);
        }
        if (tag.contains("Unbreakable")) {
            // 26.1: UNBREAKABLE component carries Unit (a singleton marker)
            // rather than the 1.21.1 Unbreakable record.
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }
        if (tag.contains("RepairCost")) stack.set(DataComponents.REPAIR_COST, tag.getIntOr("RepairCost", 0));

        // display.Name / display.Lore parsing intentionally omitted on 26.1
        // — Component.Serializer.fromJson signature shifted between 1.21.1
        // and 26.1 and the chidori test pack (and most legacy packs) don't
        // use these fields. TODO: restore via ComponentSerialization.CODEC
        // if a target pack actually needs custom names.

        if (registries != null) {
            if (tag.contains("Enchantments")) applyEnchantments(stack, tag.getListOrEmpty("Enchantments"), registries, false);
            if (tag.contains("ench"))         applyEnchantments(stack, tag.getListOrEmpty("ench"),         registries, false);
            if (tag.contains("StoredEnchantments")) applyEnchantments(stack, tag.getListOrEmpty("StoredEnchantments"), registries, true);
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
            for (String key : leftover.keySet()) {
                Tag v = leftover.get(key);
                if (v != null) merged.put(key, v);
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        }
    }

    private static void applyPotion(ItemStack stack, String potionId) {
        if (potionId == null || potionId.isEmpty()) return;
        Identifier id = Identifier.tryParse(potionId);
        if (id == null) {
            NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: unparseable potion '{}'", potionId);
            return;
        }
        // 26.1: Registry.get(Identifier) returns Optional<Holder.Reference> directly.
        var potionOpt = BuiltInRegistries.POTION.get(id);
        if (potionOpt.isEmpty()) {
            NeoOrigins.LOGGER.debug("[CompatB] LegacyTagToComponents: unknown potion '{}'", potionId);
            return;
        }
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(potionOpt.get()));
    }

    private static void applyCustomModelData(ItemStack stack, int cmd) {
        // 26.1: CustomModelData is a 4-list record (floats, flags, strings, colors).
        // Vanilla maps the legacy int through the floats slot.
        stack.set(DataComponents.CUSTOM_MODEL_DATA,
            new net.minecraft.world.item.component.CustomModelData(
                java.util.List.of((float) cmd),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()));
    }

    private static void applyEnchantments(ItemStack stack, ListTag list, RegistryAccess registries, boolean stored) {
        if (list == null || list.isEmpty()) return;
        HolderLookup.RegistryLookup<Enchantment> lookup = registries.lookupOrThrow(Registries.ENCHANTMENT);
        ItemEnchantments base = stack.getOrDefault(
            stored ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS,
            ItemEnchantments.EMPTY);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(base);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            String idStr = entry.getStringOr("id", "");
            // Legacy "lvl" was a short; 1.21+ uses int.
            int level = entry.contains("lvl") ? entry.getIntOr("lvl", 0)
                                              : entry.getIntOr("level", 0);
            if (idStr.isEmpty() || level <= 0) continue;
            Identifier eid = Identifier.tryParse(idStr);
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
     * e.g. {@code [minecraft:custom_name={text:"Magic Bean"},minecraft:enchantments=...]}.
     *
     * <p>This is the string twin of {@link #applyTo} and covers exactly the same
     * {@link #RECOGNISED_KEYS}; anything else lands in {@code minecraft:custom_data}
     * rather than being dropped. Deliberately registry-free — the resource layer
     * calls this at pack-read time, long before {@code RegistryAccess} exists, so
     * ids are emitted as strings and resolved later by the command parser itself.
     *
     * <p>Three of the emitted forms are 26.x-specific and differ from the 1.21.1
     * branch, because the components themselves changed shape:
     * <ul>
     *   <li>{@code custom_model_data} is a four-list record here, not a bare int,
     *       so the legacy value rides in the {@code floats} slot exactly as
     *       {@link #applyCustomModelData} puts it;</li>
     *   <li>{@code ItemEnchantments.CODEC} lost its record alternative, so it is
     *       always the bare levels map and the {@code HideFlags} tooltip bit is
     *       expressed through the separate {@code tooltip_display} component;</li>
     *   <li>{@code custom_name}/{@code lore} moved from
     *       {@code ComponentSerialization.FLAT_CODEC} (a JSON <em>string</em>) to
     *       {@code ComponentSerialization.CODEC} (a structural value), so legacy
     *       display text is converted into an SNBT compound instead of being
     *       re-quoted. See {@link #asComponentSnbt}.</li>
     * </ul>
     *
     * @return the bracket body including the enclosing {@code [ ]}, or an empty
     *         string when the tag maps to nothing at all.
     */
    public static String toComponentString(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) return "";

        // HideFlags is a pre-1.21 tooltip bitfield. Bit 0 (value 1) hid the
        // enchantment list. The remaining bits (attribute modifiers, unbreakable,
        // can_destroy, …) each name a different component and are not worth
        // guessing at, so they are dropped rather than risked.
        boolean hideEnchantments = false;
        if (tag.contains("HideFlags")) {
            int flags = tag.getIntOr("HideFlags", 0);
            hideEnchantments = (flags & 1) != 0;
            if ((flags & ~1) != 0) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyTagToComponents: HideFlags bits 0x{} have no cheap 26.x equivalent — dropped",
                    Integer.toHexString(flags & ~1));
            }
        }

        List<String> parts = new ArrayList<>();

        if (tag.contains("Potion")) {
            String potion = tag.getStringOr("Potion", "");
            // PotionContents.CODEC accepts a bare potion id as an alternative to
            // the full record, so the legacy string carries over verbatim.
            if (!potion.isEmpty()) parts.add("minecraft:potion_contents=" + StringTag.quoteAndEscape(potion));
        }
        if (tag.contains("CustomModelData")) {
            // 26.x: the component is {floats,flags,strings,colors}; vanilla's own
            // legacy fixer routes the old int through the floats slot.
            parts.add("minecraft:custom_model_data={floats:[" + (float) tag.getIntOr("CustomModelData", 0) + "f]}");
        }
        if (tag.contains("Damage"))     parts.add("minecraft:damage=" + tag.getIntOr("Damage", 0));
        if (tag.contains("RepairCost")) parts.add("minecraft:repair_cost=" + tag.getIntOr("RepairCost", 0));
        // 1.20's Unbreakable:1b flag → the 26.x Unit marker, whose MapCodec reads
        // an empty compound.
        if (tag.contains("Unbreakable")) parts.add("minecraft:unbreakable={}");

        if (tag.contains("display")) appendDisplay(parts, tag.getCompoundOrEmpty("display"));

        // Enchantments and the 1.12-era `ench` alias both target ONE component;
        // the command parser rejects a repeated component key, so they merge.
        ListTag worn = new ListTag();
        worn.addAll(tag.getListOrEmpty("Enchantments"));
        worn.addAll(tag.getListOrEmpty("ench"));
        boolean wroteWorn = appendEnchantments(parts, "minecraft:enchantments", worn);
        boolean wroteStored = appendEnchantments(parts, "minecraft:stored_enchantments",
            tag.getListOrEmpty("StoredEnchantments"));

        // 26.x: per-component show_in_tooltip is gone; hiding is centralised on
        // minecraft:tooltip_display. Only name components we actually emitted, or
        // the parser would be told to hide something that isn't there.
        if (hideEnchantments && (wroteWorn || wroteStored)) {
            List<String> hidden = new ArrayList<>();
            if (wroteWorn)   hidden.add("\"minecraft:enchantments\"");
            if (wroteStored) hidden.add("\"minecraft:stored_enchantments\"");
            parts.add("minecraft:tooltip_display={hidden_components:[" + String.join(",", hidden) + "]}");
        }

        CompoundTag leftover = leftoverOf(tag);
        if (leftover != null) parts.add("minecraft:custom_data=" + leftover);

        return parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]";
    }

    /** Everything outside {@link #RECOGNISED_KEYS}, or null when there is none. */
    @Nullable
    private static CompoundTag leftoverOf(CompoundTag tag) {
        CompoundTag leftover = null;
        for (String key : tag.keySet()) {
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
            String name = asComponentSnbt(display.getStringOr("Name", ""));
            if (name != null) parts.add("minecraft:custom_name=" + name);
        }
        if (display.contains("Lore")) {
            ListTag loreList = display.getListOrEmpty("Lore");
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < loreList.size(); i++) {
                String line = asComponentSnbt(loreList.getStringOr(i, ""));
                if (line != null) lines.add(line);
            }
            // ItemLore.CODEC is a size-limited list over ComponentSerialization.
            // NBT lists are homogeneous, so asComponentSnbt always yields a
            // compound and the list is well-formed by construction.
            if (!lines.isEmpty()) parts.add("minecraft:lore=[" + String.join(",", lines) + "]");
        }
    }

    /**
     * Turn a legacy {@code display.Name}/{@code Lore} entry into SNBT that the
     * chat-component codec can read.
     *
     * <p>This is the one place the 26.x rendering genuinely diverges from the
     * 1.21.1 branch. There, {@code custom_name} was backed by
     * {@code ComponentSerialization.FLAT_CODEC}, which reads a <em>string</em>
     * and JSON-parses its contents — exactly the shape legacy {@code display.Name}
     * already had, so it only needed re-quoting. On 26.x the component is backed
     * by {@code ComponentSerialization.CODEC}, which reads the value
     * structurally; handing it the legacy JSON string would produce an item
     * literally named {@code {"text":"Magic Bean"}}. So the JSON is parsed and
     * transcoded to an SNBT compound instead.
     *
     * <p>Always a compound, never a bare string: {@code lore} is an NBT list and
     * NBT lists are homogeneous, so a mix of compound and string entries would
     * not parse at all.
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
                JsonElement parsed = JsonParser.parseString(trimmed);
                Tag converted = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, parsed);
                if (converted instanceof CompoundTag compound) return compound.toString();
                // A JSON array or bare string is legal component JSON but would
                // break lore's list homogeneity, so it degrades to literal text.
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyTagToComponents: display text '{}' is not a component object — treated as literal text",
                    trimmed);
            } catch (Exception e) {
                NeoOrigins.LOGGER.debug(
                    "[CompatB] LegacyTagToComponents: display text '{}' is not valid JSON — treated as literal text",
                    trimmed);
            }
        }
        CompoundTag literal = new CompoundTag();
        literal.putString("text", raw);
        return literal.toString();
    }

    /**
     * @return true when the component was actually emitted, so the caller knows
     *         whether naming it in {@code tooltip_display} would be meaningful.
     */
    private static boolean appendEnchantments(List<String> parts, String component, ListTag list) {
        if (list == null || list.isEmpty()) return false;
        List<String> levels = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompoundOrEmpty(i);
            String id = entry.getStringOr("id", "");
            int level = entry.contains("lvl") ? entry.getIntOr("lvl", 0) : entry.getIntOr("level", 0);
            if (id.isEmpty() || level <= 0) continue;
            if (Identifier.tryParse(id) == null) continue;
            levels.add(StringTag.quoteAndEscape(id) + ":" + level);
        }
        if (levels.isEmpty()) return false;
        // 26.x: ItemEnchantments.CODEC is a plain unbounded map — the 1.21.1
        // {levels:…,show_in_tooltip:…} record form no longer exists.
        parts.add(component + "={" + String.join(",", levels) + "}");
        return true;
    }
}
