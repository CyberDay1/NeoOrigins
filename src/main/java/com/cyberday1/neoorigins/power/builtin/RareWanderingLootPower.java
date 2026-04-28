package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.api.power.PowerConfiguration;
import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Charisma — wandering traders offer the player master-tier villager trades
 * and a chance at a rare treasure item.
 *
 * <p>Trades are injected per-player when the player opens the trader's UI.
 * Each wandering trader instance remembers which players it has already
 * generated charisma trades for (keyed by trader entity ID stored in the
 * player's transient data, since traders despawn anyway).
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

    // ── Professions that have level-5 (master) trades ──────────────────
    private static final VillagerProfession[] PROFESSIONS = {
        VillagerProfession.FARMER,
        VillagerProfession.FISHERMAN,
        VillagerProfession.SHEPHERD,
        VillagerProfession.FLETCHER,
        VillagerProfession.LIBRARIAN,
        VillagerProfession.CARTOGRAPHER,
        VillagerProfession.CLERIC,
        VillagerProfession.ARMORER,
        VillagerProfession.WEAPONSMITH,
        VillagerProfession.TOOLSMITH,
        VillagerProfession.BUTCHER,
        VillagerProfession.LEATHERWORKER,
        VillagerProfession.MASON,
    };

    // Master = level 4–5 trades
    private static final int MIN_MASTER_LEVEL = 4;
    private static final int MAX_MASTER_LEVEL = 5;

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

        // Collect all level 4–5 listings across all professions
        List<VillagerTrades.ItemListing> masterPool = new ArrayList<>();
        for (VillagerProfession prof : PROFESSIONS) {
            Int2ObjectMap<VillagerTrades.ItemListing[]> trades = VillagerTrades.TRADES.get(prof);
            if (trades == null) continue;
            for (int level = MIN_MASTER_LEVEL; level <= MAX_MASTER_LEVEL; level++) {
                VillagerTrades.ItemListing[] listings = trades.get(level);
                if (listings == null) continue;
                for (VillagerTrades.ItemListing listing : listings) {
                    masterPool.add(listing);
                }
            }
        }

        // Pick N random master trades, avoiding duplicates
        int toAdd = Math.min(masterSlots[0], masterPool.size());
        List<VillagerTrades.ItemListing> shuffled = new ArrayList<>(masterPool);
        for (int i = 0; i < toAdd; i++) {
            // Try up to 5 times per slot to get a non-null offer
            for (int attempt = 0; attempt < 5; attempt++) {
                int idx = random.nextInt(shuffled.size());
                VillagerTrades.ItemListing listing = shuffled.get(idx);
                MerchantOffer offer = listing.getOffer(trader, random);
                if (offer != null) {
                    offers.add(offer);
                    shuffled.remove(idx);
                    break;
                }
            }
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
