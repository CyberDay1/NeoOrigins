package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

/**
 * Gives the player a specific item (optionally enchanted) the first time this power is granted.
 * Uses grantId to track whether the item has already been given, preventing duplicate grants
 * on respawn or re-login.
 */
public class StartingEquipmentPower extends PowerType<StartingEquipmentPower.Config> {

    public record EnchantEntry(String id, int level) {
        public static final Codec<EnchantEntry> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("id").forGetter(EnchantEntry::id),
            Codec.INT.optionalFieldOf("level", 1).forGetter(EnchantEntry::level)
        ).apply(inst, EnchantEntry::new));
    }

    public record Config(
        String grantId,
        String item,
        List<EnchantEntry> enchantments,
        int count,
        String type
    ) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("grant_id").forGetter(Config::grantId),
            Codec.STRING.fieldOf("item").forGetter(Config::item),
            EnchantEntry.CODEC.listOf().optionalFieldOf("enchantments", List.of()).forGetter(Config::enchantments),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Config::count),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    @Override
    public void onGranted(ServerPlayer player, Config config) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (data.hasGrantedEquipment(config.grantId())) return;

        var itemOpt = BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(config.item()));
        if (itemOpt.isEmpty()) return;

        ItemStack stack = new ItemStack(itemOpt.get(), config.count());

        if (!config.enchantments().isEmpty()) {
            var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable enchMutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (var entry : config.enchantments()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse(entry.id()));
                enchLookup.get(key).ifPresent(h -> enchMutable.set(h, entry.level()));
            }
            stack.set(DataComponents.ENCHANTMENTS, enchMutable.toImmutable());
        }

        player.addItem(stack);
        data.markEquipmentGranted(config.grantId());
    }
}
