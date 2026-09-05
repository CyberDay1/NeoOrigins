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
 * <p>{@code tiers} optionally splits that set into value bands: a nugget is
 * worth less than an ingot, a storage block more. Each tier carries its own
 * item/tag filter and nutrition, and matches on top of the base lists, so a
 * tier can introduce items the base lists never mentioned. The first tier that
 * matches wins; anything unmatched falls back to the power's own values.
 *
 * <p>Wired via {@code InteractionPowerEvents.onRightClickItem}.
 */
public class EdibleItemPower extends PowerType<EdibleItemPower.Config> {

    /**
     * A value band inside one edible set. {@code saturation} is optional: when
     * absent the tier inherits the power's saturation modifier, which is the
     * usual case — vanilla computes the saturation gain as
     * {@code nutrition * modifier * 2}, so scaling nutrition alone already
     * scales saturation with it.
     */
    public record Tier(
        List<Identifier> items,
        List<Identifier> tags,
        int nutrition,
        Optional<Float> saturation
    ) {
        public static final Codec<Tier> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Tier::items),
            Identifier.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Tier::tags),
            Codec.INT.fieldOf("nutrition").forGetter(Tier::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation").forGetter(Tier::saturation)
        ).apply(inst, Tier::new));
    }

    public record Config(
        List<Identifier> items,
        List<Identifier> tags,
        List<Tier> tiers,
        int nutrition,
        float saturation,
        boolean alwaysEdible,
        Optional<Identifier> consumeSound,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Identifier.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Config::items),
            Identifier.CODEC.listOf().optionalFieldOf("tags", List.of()).forGetter(Config::tags),
            Tier.CODEC.listOf().optionalFieldOf("tiers", List.of()).forGetter(Config::tiers),
            Codec.INT.optionalFieldOf("nutrition", 4).forGetter(Config::nutrition),
            Codec.FLOAT.optionalFieldOf("saturation", 0.3f).forGetter(Config::saturation),
            Codec.BOOL.optionalFieldOf("always_edible", true).forGetter(Config::alwaysEdible),
            Identifier.CODEC.optionalFieldOf("consume_sound").forGetter(Config::consumeSound),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    public static boolean matches(ItemStack stack, Config config) {
        if (stack.isEmpty()) return false;
        if (matchesAny(stack, config.items(), config.tags())) return true;
        return tierFor(stack, config).isPresent();
    }

    /** The first tier claiming this stack, if any. */
    public static Optional<Tier> tierFor(ItemStack stack, Config config) {
        if (stack.isEmpty()) return Optional.empty();
        for (Tier tier : config.tiers()) {
            if (matchesAny(stack, tier.items(), tier.tags())) return Optional.of(tier);
        }
        return Optional.empty();
    }

    public static int nutritionFor(ItemStack stack, Config config) {
        return tierFor(stack, config).map(Tier::nutrition).orElse(config.nutrition());
    }

    public static float saturationFor(ItemStack stack, Config config) {
        return tierFor(stack, config)
            .flatMap(Tier::saturation)
            .orElse(config.saturation());
    }

    private static boolean matchesAny(ItemStack stack, List<Identifier> items, List<Identifier> tags) {
        for (Identifier id : items) {
            var holder = BuiltInRegistries.ITEM.get(id);
            if (holder.isPresent() && stack.is(holder.get())) return true;
        }
        for (Identifier id : tags) {
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            if (stack.is(tag)) return true;
        }
        return false;
    }
}
