package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Adds Unbreaking to items the player <em>crafts</em>.
 *
 * <p>Only fires on the crafting event — items obtained from enchanting tables,
 * anvils, loot, or trades are left untouched. This prevents the old tick-scan
 * approach from slapping Unbreaking on every tool the moment it enters the
 * inventory, which blocked players from using the enchanting table (vanilla
 * rejects items that already carry enchantments).
 */
public class QualityEquipmentPower extends PowerType<QualityEquipmentPower.Config> {

    public record Config(int unbreakingLevel, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("unbreaking_level", 1).forGetter(Config::unbreakingLevel),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    /**
     * Called by {@link com.cyberday1.neoorigins.event.CraftingPowerEvents}
     * when the player crafts an item. Adds Unbreaking if the item is
     * damageable and doesn't already have it.
     */
    public static void onItemCrafted(ServerPlayer player, ItemStack stack, int unbreakingLevel) {
        if (stack.isEmpty() || !stack.isDamageableItem()) return;

        ItemEnchantments existing = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreakingHolder = enchLookup.get(Enchantments.UNBREAKING);
        if (unbreakingHolder.isEmpty()) return;
        if (existing.getLevel(unbreakingHolder.get()) > 0) return;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        mutable.set(unbreakingHolder.get(), unbreakingLevel);
        stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }
}
