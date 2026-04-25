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
import java.util.Optional;

/**
 * Makes arbitrary items consumable. On right-click, a matching item is
 * instantly consumed: the configured nutrition / saturation is applied, one is
 * removed from the stack, and an {@code action_on_event.ITEM_USE_FINISH}
 * dispatch fires with the stack as context (so pack authors can chain
 * custom effects).
 *
 * <p>This power bypasses vanilla FoodProperties and lets a pack define
 * pattern staples like "Merling eats raw fish" or "Phantom eats rotten flesh
 * for full food" without needing to replace the item's data components.
 *
 * <p>{@code items} and {@code tags} filter which items match. At least one
 * must be non-empty. Matching is inclusive — an item need only appear in
 * either list to qualify.
 *
 * <p>Wired via {@code InteractionPowerEvents.onRightClickItem}.
 */
public class EdibleItemPower extends PowerType<EdibleItemPower.Config> {

    public record Config(
        List<ResourceLocation> items,
        List<ResourceLocation> tags,
        int nutrition,
        float saturation,
        boolean alwaysEdible,
        Optional<ResourceLocation> consumeSound,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Config::items),
            ResourceLocation.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Config::tags),
            Codec.INT.optionalFieldOf("nutrition", 4).forGetter(Config::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.3f).forGetter(Config::saturation),
            Codec.BOOL.optionalFieldOf("always_edible", true).forGetter(Config::alwaysEdible),
            ResourceLocation.CODEC.optionalFieldOf("consume_sound").forGetter(Config::consumeSound),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static boolean matches(ItemStack stack, Config config) {
        if (stack.isEmpty()) return false;
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
