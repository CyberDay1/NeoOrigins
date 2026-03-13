package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import java.util.*;

/**
 * Boosts saturation of food items in inventory (tick-based workaround for no ItemCraftedEvent).
 * Tracks item identity hashes to only boost new food items.
 */
public class BetterCraftedFoodPower extends PowerType<BetterCraftedFoodPower.Config> {

    private static final Map<UUID, Set<Integer>> TRACKED = new WeakHashMap<>();

    public record Config(float saturationBonus, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.FLOAT.optionalFieldOf("saturation_bonus", 0.4f).forGetter(Config::saturationBonus),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 10 != 0) return;
        if (!player.hasContainerOpen()) return;

        Set<Integer> tracked = TRACKED.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
        var inv = player.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food == null) continue;

            int hash = System.identityHashCode(stack);
            if (tracked.contains(hash)) continue;
            tracked.add(hash);

            FoodProperties boosted = new FoodProperties(
                food.nutrition(),
                food.saturation() + config.saturationBonus(),
                food.canAlwaysEat()
            );
            stack.set(DataComponents.FOOD, boosted);
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        TRACKED.remove(player.getUUID());
    }
}
