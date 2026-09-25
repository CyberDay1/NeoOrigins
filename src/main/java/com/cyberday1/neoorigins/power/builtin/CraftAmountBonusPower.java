package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.NeoOrigins;
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
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Grants bonus items when crafting specific outputs (e.g., more planks from logs).
 * Uses {@link PlayerEvent.ItemCraftedEvent} to detect crafting and award bonuses
 * only for genuine crafting operations.
 */
public class CraftAmountBonusPower extends PowerType<CraftAmountBonusPower.Config> {

    public record Config(String outputItem, int bonusCount, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("output_item", "minecraft:oak_planks").forGetter(Config::outputItem),
            Codec.INT.optionalFieldOf("bonus_count", 4).forGetter(Config::bonusCount),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @EventBusSubscriber(modid = NeoOrigins.MOD_ID)
    public static final class Handler {
        @SubscribeEvent
        public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer sp)) return;

            ItemStack crafted = event.getCrafting();
            if (crafted.isEmpty()) return;
            ActiveOriginService.forEachOfType(sp, CraftAmountBonusPower.class, config -> {
                if (!matches(crafted, config.outputItem())) return;

                int bonus = config.bonusCount();
                if (bonus > 0) {
                    sp.getInventory().add(new ItemStack(crafted.getItem(), bonus));
                }
            });
        }

        /** {@code output_item} is an item id or a {@code #tag}; the Lumberjack uses {@code #minecraft:planks}. */
        static boolean matches(ItemStack crafted, String outputItem) {
            if (outputItem.startsWith("#")) {
                ResourceLocation tagId = ResourceLocation.tryParse(outputItem.substring(1));
                return tagId != null && crafted.is(TagKey.create(Registries.ITEM, tagId));
            }
            ResourceLocation id = ResourceLocation.tryParse(outputItem);
            if (id == null) return false;
            var item = BuiltInRegistries.ITEM.getOptional(id);
            return item.isPresent() && crafted.is(item.get());
        }
    }
}
