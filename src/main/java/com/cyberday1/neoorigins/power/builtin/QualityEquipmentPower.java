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
 * Adds Unbreaking to items crafted by the player. Only fires at craft time
 * via {@code CraftingPowerEvents.onItemCrafted} — items from enchanting
 * tables, anvils, loot, or trades are left untouched so players can enchant
 * normally first.
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
     * Called from {@link com.cyberday1.neoorigins.event.CraftingPowerEvents#onItemCrafted}
     * when the player has this power. Applies Unbreaking to the freshly crafted item
     * if it is damageable and doesn't already have the enchantment.
     */
    public static void onItemCrafted(ServerPlayer player, ItemStack result, int level) {
        if (result.isEmpty() || !result.isDamageableItem()) return;

        ItemEnchantments existing = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        var unbreakingHolder = enchLookup.get(Enchantments.UNBREAKING);
        if (unbreakingHolder.isEmpty()) return;
        if (existing.getLevel(unbreakingHolder.get()) > 0) return;

        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(existing);
        mutable.set(unbreakingHolder.get(), level);
        result.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
    }
}
