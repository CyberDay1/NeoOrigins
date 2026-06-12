package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Prevents certain items from being equipped in certain slots. When a matching
 * item is put into a restricted slot, the power ejects it back to the player's
 * inventory (or drops it to the ground if inventory is full).
 */
public class RestrictArmorPower extends PowerType<RestrictArmorPower.Config> {

    public record SlotRestriction(
        String slot,
        Optional<ResourceLocation> item,
        Optional<ResourceLocation> tag
    ) {
        public static final Codec<SlotRestriction> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("slot").forGetter(SlotRestriction::slot),
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(SlotRestriction::item),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(SlotRestriction::tag)
        ).apply(inst, SlotRestriction::new));
    }

    public record Config(java.util.List<SlotRestriction> restrictions, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            SlotRestriction.CODEC.listOf().optionalFieldOf("restrictions", java.util.List.of())
                .forGetter(Config::restrictions),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static boolean isRestricted(ItemStack stack, EquipmentSlot slot, Config config) {
        if (stack.isEmpty()) return false;
        String slotName = slot.getName();
        for (SlotRestriction r : config.restrictions()) {
            if (!r.slot().equalsIgnoreCase(slotName)) continue;
            if (r.item().isPresent()) {
                Item item = BuiltInRegistries.ITEM.get(r.item().get());
                if (stack.is(item)) return true;
            }
            if (r.tag().isPresent()) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, r.tag().get());
                if (stack.is(tag)) return true;
                // Also check config-defined armor class extensions
                if (matchesConfigArmorClass(stack, r.tag().get())) return true;
            }
            if (r.item().isEmpty() && r.tag().isEmpty()) return true;
        }
        return false;
    }

    /**
     * Checks if the stack matches any item in the config-defined armor class
     * list that extends the given tag. Only applies to neoorigins:heavy_armor
     * and neoorigins:light_armor tags.
     */
    private static boolean matchesConfigArmorClass(ItemStack stack, ResourceLocation tagId) {
        List<String> configItems;
        if (tagId.equals(ResourceLocation.fromNamespaceAndPath("neoorigins", "heavy_armor"))) {
            configItems = GameplayConfig.getHeavyArmorItems();
        } else if (tagId.equals(ResourceLocation.fromNamespaceAndPath("neoorigins", "light_armor"))) {
            configItems = GameplayConfig.getLightArmorItems();
        } else {
            return false;
        }

        ResourceLocation stackId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (String entry : configItems) {
            if (entry.startsWith("#")) {
                // Tag reference
                TagKey<Item> extraTag = TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1)));
                if (stack.is(extraTag)) return true;
            } else {
                // Item ID
                if (stackId.equals(ResourceLocation.parse(entry))) return true;
            }
        }
        return false;
    }
}
