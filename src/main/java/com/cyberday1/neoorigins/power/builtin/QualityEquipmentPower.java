package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.*;

/**
 * Adds Unbreaking I to newly crafted tools/armor via inventory monitoring.
 * Tracks inventory contents and applies enchantment to new tool/armor items.
 */
public class QualityEquipmentPower extends PowerType<QualityEquipmentPower.Config> {

    private static final Map<UUID, Set<Integer>> TRACKED_HASHES = new WeakHashMap<>();

    public record Config(int unbreakingLevel, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("unbreaking_level", 1).forGetter(Config::unbreakingLevel),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onTick(ServerPlayer player, Config config) {
        if (player.tickCount % 5 != 0) return;

        var inv = player.getInventory();
        Set<Integer> tracked = TRACKED_HASHES.computeIfAbsent(player.getUUID(), k -> new HashSet<>());

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!stack.isDamageableItem()) continue;

            int hash = System.identityHashCode(stack);
            if (tracked.contains(hash)) continue;
            tracked.add(hash);

            ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);

            var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var unbreakingHolder = enchLookup.get(Enchantments.UNBREAKING);
            if (unbreakingHolder.isEmpty()) continue;

            if (existing.getLevel(unbreakingHolder.get()) > 0) continue;

            ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
            mutable.set(unbreakingHolder.get(), config.unbreakingLevel());
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    @Override
    public void onRevoked(ServerPlayer player, Config config) {
        TRACKED_HASHES.remove(player.getUUID());
    }
}
