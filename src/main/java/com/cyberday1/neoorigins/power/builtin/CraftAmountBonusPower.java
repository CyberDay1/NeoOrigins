package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Grants bonus items when crafting specific outputs (e.g., more planks from logs).
 * Uses tick-based inventory monitoring since no ItemCraftedEvent exists.
 * Tracks total count of the target item and gives bonus when it increases.
 */
public class CraftAmountBonusPower extends PowerType<CraftAmountBonusPower.Config> {

    private static final Map<UUID, Integer> LAST_RESULT_COUNT = new WeakHashMap<>();

    public record Config(String outputItem, int bonusCount, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.optionalFieldOf("output_item", "minecraft:oak_planks").forGetter(Config::outputItem),
            Codec.INT.optionalFieldOf("bonus_count", 4).forGetter(Config::bonusCount),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 5 != 0) return;
        if (!player.hasContainerOpen()) return;

        var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(config.outputItem()));
        if (itemOpt.isEmpty()) return;
        var targetItem = itemOpt.get();

        int count = 0;
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(targetItem)) count += s.getCount();
        }

        UUID id = player.getUUID();
        Integer prev = LAST_RESULT_COUNT.get(id);
        if (prev != null && count > prev) {
            int diff = count - prev;
            int bonus = Math.min(diff, config.bonusCount());
            player.getInventory().add(new ItemStack(targetItem, bonus));
        }
        LAST_RESULT_COUNT.put(id, count);
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        LAST_RESULT_COUNT.remove(player.getUUID());
    }
}
