package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.List;

/**
 * Gives the player a specific item (optionally enchanted) the first time this power is granted —
 * but only after the player has committed to their full origin selection. During the initial
 * picker walk-through the player can click through multiple origins before confirming; granting
 * items on each preview click creates a dupe where backing out and picking a different origin
 * leaves the original origin's items in the inventory (issue #22).
 *
 * <p>Gate: {@link PlayerOriginData#isHadAllOrigins()} must be true when onGranted fires.
 * {@link NeoOriginsNetwork#handleChooseOrigin} calls {@link #grantAllPending} once that flag
 * flips so any deferred grants catch up.
 *
 * <p>Dedup: {@code grantId} is stored in {@link PlayerOriginData#grantedEquipmentPowers} so the
 * same item can't be given twice. The set is cleared by the Orb of Origin and full
 * {@code /origin reset} so users re-pay for re-granted items.
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
        // Defer until the player has committed to their full origin set.
        // Otherwise a player can pick an origin, receive its items, click "back",
        // and pick a different origin — keeping the first origin's items. See
        // handleChooseOrigin which calls grantAllPending once hadAllOrigins flips.
        if (!data.isHadAllOrigins()) return;
        grantIfUngranted(player, config, data);
    }

    /**
     * Runs all currently-active StartingEquipmentPower grants for the player.
     * Called by handleChooseOrigin once the picker commits (hadAllOrigins true)
     * so previously-deferred grants catch up. Idempotent: grantId flags prevent
     * double-granting.
     */
    public static void grantAllPending(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        if (!data.isHadAllOrigins()) return;
        ActiveOriginService.forEachOfType(player, StartingEquipmentPower.class,
            cfg -> grantIfUngranted(player, cfg, data));
    }

    private static void grantIfUngranted(ServerPlayer player, Config config, PlayerOriginData data) {
        if (data.hasGrantedEquipment(config.grantId())) return;

        var itemOpt = BuiltInRegistries.ITEM.get(Identifier.parse(config.item()));
        if (itemOpt.isEmpty()) return;

        ItemStack stack = new ItemStack(itemOpt.get().value(), config.count());

        if (!config.enchantments().isEmpty()) {
            var enchLookup = player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            ItemEnchantments.Mutable enchMutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            for (var entry : config.enchantments()) {
                ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, Identifier.parse(entry.id()));
                enchLookup.get(key).ifPresent(h -> enchMutable.set(h, entry.level()));
            }
            stack.set(DataComponents.ENCHANTMENTS, enchMutable.toImmutable());
        }

        player.addItem(stack);
        data.markEquipmentGranted(config.grantId());
    }
}
