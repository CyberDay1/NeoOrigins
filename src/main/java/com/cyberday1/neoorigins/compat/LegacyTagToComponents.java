package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.NeoOrigins;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
 * </ul>
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
        CompoundTag leftover = null;
        for (String key : tag.getAllKeys()) {
            if (RECOGNISED_KEYS.contains(key)) continue;
            if (leftover == null) leftover = new CompoundTag();
            Tag value = tag.get(key);
            if (value != null) leftover.put(key, value);
        }
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
}
