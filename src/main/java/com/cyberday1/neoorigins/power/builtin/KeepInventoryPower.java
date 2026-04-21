package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * On death, selectively retain inventory items that match the power's filters.
 * Matching items are removed from drops and restored to the player's inventory
 * on respawn.
 */
public class KeepInventoryPower extends PowerType<KeepInventoryPower.Config> {

    public record Config(
        List<String> slots,
        List<ResourceLocation> items,
        List<ResourceLocation> tags,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("slots", List.of("*")).forGetter(Config::slots),
            ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Config::items),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Config::tags),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

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
            if (s.equalsIgnoreCase("main") && (cat == SlotCategory.HOTBAR || cat == SlotCategory.MAIN)) return true;
        }
        return false;
    }

    public static boolean matchesItem(Config config, ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (config.items().isEmpty() && config.tags().isEmpty()) return true;
        for (ResourceLocation id : config.items()) {
            Item item = BuiltInRegistries.ITEM.get(id);
            if (stack.is(item)) return true;
        }
        for (ResourceLocation id : config.tags()) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            if (stack.is(tag)) return true;
        }
        return false;
    }
}
