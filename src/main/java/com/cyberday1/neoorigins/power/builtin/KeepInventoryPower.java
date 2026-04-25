package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * On death, selectively retain inventory items that match the power's filters.
 * Matching items are removed from drops and restored to the player's inventory
 * on respawn.
 *
 * <p>Filters (any match → kept):
 * <ul>
 *   <li>{@code slots} — a list of slot-name prefixes ({@code "hotbar"}, {@code "offhand"},
 *       {@code "armor"}, {@code "main"}, or {@code "*"}). Restricts which inventory
 *       slots are considered. Default: all slots.</li>
 *   <li>{@code items} — list of item IDs to keep.</li>
 *   <li>{@code tags} — list of item tag IDs to keep (with or without {@code #} prefix).</li>
 * </ul>
 *
 * <p>If no items/tags are specified but {@code slots} is set, all items in those
 * slots are kept. If neither is set, all inventory is kept (equivalent to
 * vanilla {@code keepInventory} gamerule, but origin-scoped).
 *
 * <p>Wired via {@code PlayerLifecycleEvents} on death + respawn.
 */
public class KeepInventoryPower extends PowerType<KeepInventoryPower.Config> {

    public record Config(
        List<String> slots,
        List<Identifier> items,
        List<Identifier> tags,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("slots", List.of("*")).forGetter(Config::slots),
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Config::items),
            Identifier.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Config::tags),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** Slot categorization matching Apoli conventions. */
    public enum SlotCategory {
        HOTBAR, MAIN, ARMOR, OFFHAND, ALL;

        public static SlotCategory forInventoryIndex(int i) {
            if (i >= 0 && i < 9) return HOTBAR;
            if (i >= 9 && i < 36) return MAIN;
            if (i >= 36 && i < 40) return ARMOR;
            if (i == 40) return OFFHAND;
            return ALL;
        }
    }

    public static boolean matchesSlot(Config config, SlotCategory cat) {
        for (String s : config.slots()) {
            if (s.equalsIgnoreCase("*") || s.equalsIgnoreCase("all")) return true;
            if (s.equalsIgnoreCase(cat.name())) return true;
            // "main" matches both HOTBAR and MAIN for convenience
            if (s.equalsIgnoreCase("main") && (cat == SlotCategory.HOTBAR || cat == SlotCategory.MAIN)) return true;
        }
        return false;
    }

    public static boolean matchesItem(Config config, ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Empty item/tag lists with a slots filter → keep everything in the slots.
        if (config.items().isEmpty() && config.tags().isEmpty()) return true;
        for (Identifier id : config.items()) {
            var holder = BuiltInRegistries.ITEM.get(id);
            if (holder.isPresent() && stack.is(holder.get())) return true;
        }
        for (Identifier id : config.tags()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            if (stack.is(tag)) return true;
        }
        return false;
    }
}
