package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Charisma — wandering traders offer the player master-tier villager trades
 * and a chance at a rare treasure item.
 *
 * <p>Trades are injected per-player when the player opens the trader's UI.
 * Each wandering trader instance remembers which players it has already
 * generated charisma trades for (keyed by trader entity ID stored in the
 * player's transient data, since traders despawn anyway).
 *
 * <p>26.1 note: villager trades are data-driven in this MC version, so
 * master-tier offers are defined inline rather than pulled from
 * VillagerTrades.TRADES.
 */
public class RareWanderingLootPower extends PowerType<RareWanderingLootPower.Config> {

    public record Config(int masterSlots, double treasureChance, String type) implements PowerConfiguration {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("master_slots", 3).forGetter(Config::masterSlots),
            Codec.DOUBLE.optionalFieldOf("treasure_chance", 0.25).forGetter(Config::treasureChance),
            Codec.STRING.optionalFieldOf("type", "").forGetter(Config::type)
        ).apply(inst, Config::new));
    }

    @Override
    public Codec<Config> codec() { return Config.CODEC; }

    // ── Master-tier trade pool (level 4–5 equivalent trades) ───────────
    // These mirror the vanilla master villager trades from all professions.
    // On 26.1, villager trades are data-driven, so we define them inline
    // rather than pulling from a static map.
    private record MasterTrade(ItemCost cost, Optional<ItemCost> cost2, ItemStack result, int maxUses, int xp, float priceMultiplier) {
        MerchantOffer toOffer() {
            return new MerchantOffer(cost, cost2, result.copy(), maxUses, xp, priceMultiplier);
        }
    }

    private static final List<MasterTrade> MASTER_POOL = List.of(
        // Farmer level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 3), Optional.empty(), new ItemStack(Items.GOLDEN_CARROT, 3), 12, 30, 0.05F),
        new MasterTrade(new ItemCost(Items.EMERALD, 4), Optional.empty(), new ItemStack(Items.GLISTERING_MELON_SLICE, 3), 12, 30, 0.05F),
        // Cleric level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 3), Optional.empty(), new ItemStack(Items.EXPERIENCE_BOTTLE), 12, 30, 0.2F),
        // Cleric level 4
        new MasterTrade(new ItemCost(Items.EMERALD, 5), Optional.empty(), new ItemStack(Items.ENDER_PEARL), 12, 15, 0.2F),
        // Librarian level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 20), Optional.empty(), new ItemStack(Items.NAME_TAG), 12, 30, 0.2F),
        // Cartographer level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.GLOBE_BANNER_PATTERN), 12, 30, 0.2F),
        // Armorer level 4 (unenchanted equivalents)
        new MasterTrade(new ItemCost(Items.EMERALD, 14), Optional.empty(), new ItemStack(Items.DIAMOND_LEGGINGS), 3, 15, 0.2F),
        new MasterTrade(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.DIAMOND_BOOTS), 3, 15, 0.2F),
        // Armorer level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.DIAMOND_HELMET), 3, 30, 0.2F),
        new MasterTrade(new ItemCost(Items.EMERALD, 16), Optional.empty(), new ItemStack(Items.DIAMOND_CHESTPLATE), 3, 30, 0.2F),
        // Weaponsmith level 4-5
        new MasterTrade(new ItemCost(Items.EMERALD, 12), Optional.empty(), new ItemStack(Items.DIAMOND_AXE), 3, 15, 0.2F),
        new MasterTrade(new ItemCost(Items.EMERALD, 8), Optional.empty(), new ItemStack(Items.DIAMOND_SWORD), 3, 30, 0.2F),
        // Toolsmith level 4-5
        new MasterTrade(new ItemCost(Items.EMERALD, 5), Optional.empty(), new ItemStack(Items.DIAMOND_SHOVEL), 3, 15, 0.2F),
        new MasterTrade(new ItemCost(Items.EMERALD, 13), Optional.empty(), new ItemStack(Items.DIAMOND_PICKAXE), 3, 30, 0.2F),
        new MasterTrade(new ItemCost(Items.EMERALD, 12), Optional.empty(), new ItemStack(Items.DIAMOND_AXE), 3, 15, 0.2F),
        // Leatherworker level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 6), Optional.empty(), new ItemStack(Items.SADDLE), 12, 30, 0.2F),
        // Shepherd level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 2), Optional.empty(), new ItemStack(Items.PAINTING, 3), 12, 30, 0.2F),
        // Fletcher level 5
        new MasterTrade(new ItemCost(Items.EMERALD, 3), Optional.empty(), new ItemStack(Items.CROSSBOW), 3, 15, 0.2F)
    );

    // ── Treasure pool: rare items costing emeralds + netherite ingot ────
    private static final ItemStack[] TREASURE_RESULTS = {
        new ItemStack(Items.HEART_OF_THE_SEA),
        new ItemStack(Items.NETHER_STAR),
        new ItemStack(Items.TOTEM_OF_UNDYING),
        new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
        new ItemStack(Items.TRIDENT),
        new ItemStack(Items.ELYTRA),
        new ItemStack(Items.DRAGON_HEAD),
    };
    private static final int TREASURE_EMERALD_MIN = 32;
    private static final int TREASURE_EMERALD_MAX = 64;
    private static final int TREASURE_NETHERITE_MIN = 1;
    private static final int TREASURE_NETHERITE_MAX = 6;

    /**
     * Injects charisma trades into a wandering trader's offer list for the
     * given player. Called from InteractionPowerEvents when a player with
     * this power right-clicks a wandering trader.
     */
    public static void injectTrades(ServerPlayer player, WanderingTrader trader) {
        final int[] masterSlots = {3};
        final double[] treasureChance = {0.25};
        ActiveOriginService.forEachOfType(player, RareWanderingLootPower.class, cfg -> {
            masterSlots[0] = cfg.masterSlots();
            treasureChance[0] = cfg.treasureChance();
        });

        MerchantOffers offers = trader.getOffers();
        RandomSource random = trader.getRandom();

        // Pick N random master trades from the inline pool
        int toAdd = Math.min(masterSlots[0], MASTER_POOL.size());
        List<MasterTrade> pool = new ArrayList<>(MASTER_POOL);
        for (int i = 0; i < toAdd; i++) {
            int idx = random.nextInt(pool.size());
            offers.add(pool.get(idx).toOffer());
            pool.remove(idx);
        }

        // Treasure slot — chance-based, randomised cost within range.
        // Tagged with NeoOriginsTreasure so the client mixin can render a golden glow.
        if (random.nextDouble() < treasureChance[0]) {
            int emeraldCost = TREASURE_EMERALD_MIN + random.nextInt(TREASURE_EMERALD_MAX - TREASURE_EMERALD_MIN + 1);
            int netheriteCost = TREASURE_NETHERITE_MIN + random.nextInt(TREASURE_NETHERITE_MAX - TREASURE_NETHERITE_MIN + 1);
            ItemStack treasureResult = TREASURE_RESULTS[random.nextInt(TREASURE_RESULTS.length)].copy();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("NeoOriginsTreasure", true);
            treasureResult.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            MerchantOffer treasureOffer = new MerchantOffer(
                new ItemCost(Items.EMERALD, emeraldCost),
                Optional.of(new ItemCost(Items.NETHERITE_INGOT, netheriteCost)),
                treasureResult,
                1,   // maxUses — single purchase
                30,  // xp
                0.2F // priceMultiplier
            );
            offers.add(treasureOffer);
        }
    }
}
