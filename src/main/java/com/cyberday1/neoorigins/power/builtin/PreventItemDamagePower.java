package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Prevents items from losing durability while the holder has this power.
 *
 * <p>Unlike an "unbreakable item" handed out at spawn, this is a passive trait
 * of the origin: any matching tool/weapon/armor the player uses simply never
 * takes durability damage. With no {@code items} filter it covers every
 * damageable item; otherwise only the listed item ids / {@code #tags} are spared.
 *
 * <p>The actual cancellation happens in a HEAD-cancellable mixin on
 * {@code ItemStack.hurtAndBreak}; this class only holds the config and the
 * match logic.
 *
 * <pre>
 * { "type": "neoorigins:prevent_item_damage" }
 * { "type": "neoorigins:prevent_item_damage", "items": ["minecraft:diamond_pickaxe", "#minecraft:bows"] }
 * </pre>
 */
public class PreventItemDamagePower extends PowerType<PreventItemDamagePower.Config> {

    public record Config(List<String> items, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.listOf().optionalFieldOf("items", List.of()).forGetter(Config::items),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /** True if the given stack matches this power's filter (empty filter = all items). */
    public static boolean matchesFilter(ItemStack stack, Config config) {
        if (config.items().isEmpty()) return true;
        for (String entry : config.items()) {
            if (entry == null || entry.isEmpty()) continue;
            if (entry.startsWith("#")) {
                var tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(entry.substring(1)));
                if (stack.is(tagKey)) return true;
            } else {
                var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(entry));
                if (itemOpt.isPresent() && stack.is(itemOpt.get())) return true;
            }
        }
        return false;
    }

    /** True if {@code player} owns a prevent_item_damage power that spares {@code stack}. */
    public static boolean prevents(ServerPlayer player, ItemStack stack) {
        return ActiveOriginService.has(player, PreventItemDamagePower.class,
            cfg -> matchesFilter(stack, cfg));
    }
}
