package net.minecraft.world.entity.npc;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SuspiciousStewEffects;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.item.enchantment.providers.EnchantmentProvider;
import net.minecraft.world.item.enchantment.providers.TradeRebalanceEnchantmentProviders;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationType;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.apache.commons.lang3.tuple.Pair;

public class VillagerTrades {
    private static final int DEFAULT_SUPPLY = 12;
    private static final int COMMON_ITEMS_SUPPLY = 16;
    private static final int UNCOMMON_ITEMS_SUPPLY = 3;
    private static final int XP_LEVEL_1_SELL = 1;
    private static final int XP_LEVEL_1_BUY = 2;
    private static final int XP_LEVEL_2_SELL = 5;
    private static final int XP_LEVEL_2_BUY = 10;
    private static final int XP_LEVEL_3_SELL = 10;
    private static final int XP_LEVEL_3_BUY = 20;
    private static final int XP_LEVEL_4_SELL = 15;
    private static final int XP_LEVEL_4_BUY = 30;
    private static final int XP_LEVEL_5_TRADE = 30;
    private static final float LOW_TIER_PRICE_MULTIPLIER = 0.05F;
    private static final float HIGH_TIER_PRICE_MULTIPLIER = 0.2F;
    public static final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> TRADES = Util.make(
        Maps.newHashMap(),
        p_335271_ -> {
            p_335271_.put(
                VillagerProfession.FARMER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.WHEAT, 20, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.POTATO, 26, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.CARROT, 22, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.BEETROOT, 15, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(Items.BREAD, 1, 6, 16, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Blocks.PUMPKIN, 6, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN_PIE, 1, 4, 5),
                            new VillagerTrades.ItemsForEmeralds(Items.APPLE, 1, 4, 16, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(Items.COOKIE, 3, 18, 10), new VillagerTrades.EmeraldForItems(Blocks.MELON, 4, 12, 20)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(Blocks.CAKE, 1, 1, 12, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.NIGHT_VISION, 100, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.JUMP, 160, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.WEAKNESS, 140, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.BLINDNESS, 120, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.POISON, 280, 15),
                            new VillagerTrades.SuspiciousStewForEmerald(MobEffects.SATURATION, 7, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(Items.GOLDEN_CARROT, 3, 3, 30),
                            new VillagerTrades.ItemsForEmeralds(Items.GLISTERING_MELON_SLICE, 4, 3, 30)
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.FISHERMAN,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.STRING, 20, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.COAL, 10, 16, 2),
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.COD, 6, 1, Items.COOKED_COD, 6, 16, 1, 0.05F),
                            new VillagerTrades.ItemsForEmeralds(Items.COD_BUCKET, 3, 1, 16, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COD, 15, 16, 10),
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.SALMON, 6, 1, Items.COOKED_SALMON, 6, 16, 5, 0.05F),
                            new VillagerTrades.ItemsForEmeralds(Items.CAMPFIRE, 2, 1, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.SALMON, 13, 16, 20),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.FISHING_ROD, 3, 3, 10, 0.2F)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.TROPICAL_FISH, 6, 12, 30)},
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.PUFFERFISH, 4, 12, 30),
                            new VillagerTrades.EmeraldsForVillagerTypeItem(
                                1,
                                12,
                                30,
                                ImmutableMap.<VillagerType, Item>builder()
                                    .put(VillagerType.PLAINS, Items.OAK_BOAT)
                                    .put(VillagerType.TAIGA, Items.SPRUCE_BOAT)
                                    .put(VillagerType.SNOW, Items.SPRUCE_BOAT)
                                    .put(VillagerType.DESERT, Items.JUNGLE_BOAT)
                                    .put(VillagerType.JUNGLE, Items.JUNGLE_BOAT)
                                    .put(VillagerType.SAVANNA, Items.ACACIA_BOAT)
                                    .put(VillagerType.SWAMP, Items.DARK_OAK_BOAT)
                                    .build()
                            )
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.SHEPHERD,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Blocks.WHITE_WOOL, 18, 16, 2),
                            new VillagerTrades.EmeraldForItems(Blocks.BROWN_WOOL, 18, 16, 2),
                            new VillagerTrades.EmeraldForItems(Blocks.BLACK_WOOL, 18, 16, 2),
                            new VillagerTrades.EmeraldForItems(Blocks.GRAY_WOOL, 18, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(Items.SHEARS, 2, 1, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.WHITE_DYE, 12, 16, 10),
                            new VillagerTrades.EmeraldForItems(Items.GRAY_DYE, 12, 16, 10),
                            new VillagerTrades.EmeraldForItems(Items.BLACK_DYE, 12, 16, 10),
                            new VillagerTrades.EmeraldForItems(Items.LIGHT_BLUE_DYE, 12, 16, 10),
                            new VillagerTrades.EmeraldForItems(Items.LIME_DYE, 12, 16, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIME_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PINK_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.RED_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_WOOL, 1, 1, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIME_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PINK_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.RED_CARPET, 1, 4, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_CARPET, 1, 4, 16, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.YELLOW_DYE, 12, 16, 20),
                            new VillagerTrades.EmeraldForItems(Items.LIGHT_GRAY_DYE, 12, 16, 20),
                            new VillagerTrades.EmeraldForItems(Items.ORANGE_DYE, 12, 16, 20),
                            new VillagerTrades.EmeraldForItems(Items.RED_DYE, 12, 16, 20),
                            new VillagerTrades.EmeraldForItems(Items.PINK_DYE, 12, 16, 20),
                            new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.RED_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIME_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PINK_BED, 3, 1, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_BED, 3, 1, 12, 10)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.BROWN_DYE, 12, 16, 30),
                            new VillagerTrades.EmeraldForItems(Items.PURPLE_DYE, 12, 16, 30),
                            new VillagerTrades.EmeraldForItems(Items.BLUE_DYE, 12, 16, 30),
                            new VillagerTrades.EmeraldForItems(Items.GREEN_DYE, 12, 16, 30),
                            new VillagerTrades.EmeraldForItems(Items.MAGENTA_DYE, 12, 16, 30),
                            new VillagerTrades.EmeraldForItems(Items.CYAN_DYE, 12, 16, 30),
                            new VillagerTrades.ItemsForEmeralds(Items.WHITE_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BLUE_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.RED_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.PINK_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.GREEN_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIME_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.GRAY_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BLACK_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.PURPLE_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.CYAN_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BROWN_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.YELLOW_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.ORANGE_BANNER, 3, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_BANNER, 3, 1, 12, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.ItemsForEmeralds(Items.PAINTING, 2, 3, 30)}
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.FLETCHER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.STICK, 32, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(Items.ARROW, 1, 16, 1),
                            new VillagerTrades.ItemsAndEmeraldsToItems(Blocks.GRAVEL, 10, 1, Items.FLINT, 10, 12, 1, 0.05F)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.FLINT, 26, 12, 10), new VillagerTrades.ItemsForEmeralds(Items.BOW, 2, 1, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.STRING, 14, 16, 20), new VillagerTrades.ItemsForEmeralds(Items.CROSSBOW, 3, 1, 10)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.FEATHER, 24, 16, 30), new VillagerTrades.EnchantedItemForEmeralds(Items.BOW, 2, 3, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.TRIPWIRE_HOOK, 8, 12, 30),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.CROSSBOW, 3, 3, 15),
                            new VillagerTrades.TippedArrowForItemsAndEmeralds(Items.ARROW, 5, Items.TIPPED_ARROW, 5, 2, 12, 30)
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.LIBRARIAN,
                toIntMap(
                    ImmutableMap.<Integer, VillagerTrades.ItemListing[]>builder()
                        .put(
                            1,
                            new VillagerTrades.ItemListing[]{
                                new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2),
                                new VillagerTrades.EnchantBookForEmeralds(1, EnchantmentTags.TRADEABLE),
                                new VillagerTrades.ItemsForEmeralds(Blocks.BOOKSHELF, 9, 1, 12, 1)
                            }
                        )
                        .put(
                            2,
                            new VillagerTrades.ItemListing[]{
                                new VillagerTrades.EmeraldForItems(Items.BOOK, 4, 12, 10),
                                new VillagerTrades.EnchantBookForEmeralds(5, EnchantmentTags.TRADEABLE),
                                new VillagerTrades.ItemsForEmeralds(Items.LANTERN, 1, 1, 5)
                            }
                        )
                        .put(
                            3,
                            new VillagerTrades.ItemListing[]{
                                new VillagerTrades.EmeraldForItems(Items.INK_SAC, 5, 12, 20),
                                new VillagerTrades.EnchantBookForEmeralds(10, EnchantmentTags.TRADEABLE),
                                new VillagerTrades.ItemsForEmeralds(Items.GLASS, 1, 4, 10)
                            }
                        )
                        .put(
                            4,
                            new VillagerTrades.ItemListing[]{
                                new VillagerTrades.EmeraldForItems(Items.WRITABLE_BOOK, 2, 12, 30),
                                new VillagerTrades.EnchantBookForEmeralds(15, EnchantmentTags.TRADEABLE),
                                new VillagerTrades.ItemsForEmeralds(Items.CLOCK, 5, 1, 15),
                                new VillagerTrades.ItemsForEmeralds(Items.COMPASS, 4, 1, 15)
                            }
                        )
                        .put(5, new VillagerTrades.ItemListing[]{new VillagerTrades.ItemsForEmeralds(Items.NAME_TAG, 20, 1, 30)})
                        .build()
                )
            );
            p_335271_.put(
                VillagerProfession.CARTOGRAPHER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.MAP, 7, 1, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.GLASS_PANE, 11, 16, 10),
                            new VillagerTrades.TreasureMapForEmeralds(
                                13, StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT, 12, 5
                            )
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COMPASS, 1, 12, 20),
                            new VillagerTrades.TreasureMapForEmeralds(
                                14, StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", MapDecorationTypes.WOODLAND_MANSION, 12, 10
                            ),
                            new VillagerTrades.TreasureMapForEmeralds(
                                12, StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS, 12, 10
                            )
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(Items.ITEM_FRAME, 7, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.WHITE_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BLUE_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.RED_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.PINK_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.GREEN_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIME_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.GRAY_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BLACK_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.PURPLE_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.CYAN_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.BROWN_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.YELLOW_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.ORANGE_BANNER, 3, 1, 15),
                            new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_BANNER, 3, 1, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.ItemsForEmeralds(Items.GLOBE_BANNER_PATTERN, 8, 1, 30)}
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.CLERIC,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.ROTTEN_FLESH, 32, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.REDSTONE, 1, 2, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.GOLD_INGOT, 3, 12, 10), new VillagerTrades.ItemsForEmeralds(Items.LAPIS_LAZULI, 1, 1, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.RABBIT_FOOT, 2, 12, 20),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GLOWSTONE, 4, 1, 12, 10)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.TURTLE_SCUTE, 4, 12, 30),
                            new VillagerTrades.EmeraldForItems(Items.GLASS_BOTTLE, 9, 12, 30),
                            new VillagerTrades.ItemsForEmeralds(Items.ENDER_PEARL, 5, 1, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.NETHER_WART, 22, 12, 30),
                            new VillagerTrades.ItemsForEmeralds(Items.EXPERIENCE_BOTTLE, 3, 1, 30)
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.ARMORER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_LEGGINGS), 7, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_BOOTS), 4, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_HELMET), 5, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_CHESTPLATE), 9, 1, 12, 1, 0.2F)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_BOOTS), 1, 1, 12, 5, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_LEGGINGS), 3, 1, 12, 5, 0.2F)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.LAVA_BUCKET, 1, 12, 20),
                            new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 20),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_HELMET), 1, 1, 12, 10, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.CHAINMAIL_CHESTPLATE), 4, 1, 12, 10, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.SHIELD), 5, 1, 12, 10, 0.2F)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_LEGGINGS, 14, 3, 15, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_BOOTS, 8, 3, 15, 0.2F)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_HELMET, 8, 3, 30, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_CHESTPLATE, 16, 3, 30, 0.2F)
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.WEAPONSMITH,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.IRON_AXE), 3, 1, 12, 1, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_SWORD, 2, 3, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.FLINT, 24, 12, 20)},
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 30),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_AXE, 12, 3, 15, 0.2F)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SWORD, 8, 3, 30, 0.2F)}
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.TOOLSMITH,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_AXE), 1, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_SHOVEL), 1, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_PICKAXE), 1, 1, 12, 1, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.STONE_HOE), 1, 1, 12, 1, 0.2F)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 4, 12, 10),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.BELL), 36, 1, 12, 5, 0.2F)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.FLINT, 30, 12, 20),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_AXE, 1, 3, 10, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_SHOVEL, 2, 3, 10, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_PICKAXE, 3, 3, 10, 0.2F),
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.DIAMOND_HOE), 4, 1, 3, 10, 0.2F)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.DIAMOND, 1, 12, 30),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_AXE, 12, 3, 15, 0.2F),
                            new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_SHOVEL, 5, 3, 15, 0.2F)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EnchantedItemForEmeralds(Items.DIAMOND_PICKAXE, 13, 3, 30, 0.2F)}
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.BUTCHER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.CHICKEN, 14, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.PORKCHOP, 7, 16, 2),
                            new VillagerTrades.EmeraldForItems(Items.RABBIT, 4, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(Items.RABBIT_STEW, 1, 1, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.COAL, 15, 16, 2),
                            new VillagerTrades.ItemsForEmeralds(Items.COOKED_PORKCHOP, 1, 5, 16, 5),
                            new VillagerTrades.ItemsForEmeralds(Items.COOKED_CHICKEN, 1, 8, 16, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.MUTTON, 7, 16, 20), new VillagerTrades.EmeraldForItems(Items.BEEF, 10, 16, 20)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.DRIED_KELP_BLOCK, 10, 12, 30)},
                        5,
                        new VillagerTrades.ItemListing[]{new VillagerTrades.EmeraldForItems(Items.SWEET_BERRIES, 10, 12, 30)}
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.LEATHERWORKER,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.LEATHER, 6, 16, 2),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_LEGGINGS, 3),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_CHESTPLATE, 7)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.FLINT, 26, 12, 10),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HELMET, 5, 12, 5),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_BOOTS, 4, 12, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.RABBIT_HIDE, 9, 12, 20),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_CHESTPLATE, 7)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.TURTLE_SCUTE, 4, 12, 30),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HORSE_ARMOR, 6, 12, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(new ItemStack(Items.SADDLE), 6, 1, 12, 30, 0.2F),
                            new VillagerTrades.DyedArmorForEmeralds(Items.LEATHER_HELMET, 5, 12, 30)
                        }
                    )
                )
            );
            p_335271_.put(
                VillagerProfession.MASON,
                toIntMap(
                    ImmutableMap.of(
                        1,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.CLAY_BALL, 10, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.BRICK, 1, 10, 16, 1)
                        },
                        2,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Blocks.STONE, 20, 16, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CHISELED_STONE_BRICKS, 1, 4, 16, 5)
                        },
                        3,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Blocks.GRANITE, 16, 16, 20),
                            new VillagerTrades.EmeraldForItems(Blocks.ANDESITE, 16, 16, 20),
                            new VillagerTrades.EmeraldForItems(Blocks.DIORITE, 16, 16, 20),
                            new VillagerTrades.ItemsForEmeralds(Blocks.DRIPSTONE_BLOCK, 1, 4, 16, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_ANDESITE, 1, 4, 16, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_DIORITE, 1, 4, 16, 10),
                            new VillagerTrades.ItemsForEmeralds(Blocks.POLISHED_GRANITE, 1, 4, 16, 10)
                        },
                        4,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.EmeraldForItems(Items.QUARTZ, 12, 12, 30),
                            new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.RED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PINK_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIME_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.ORANGE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.WHITE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLUE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GRAY_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BLACK_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.RED_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PINK_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.MAGENTA_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.LIME_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.GREEN_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.CYAN_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.PURPLE_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.YELLOW_GLAZED_TERRACOTTA, 1, 1, 12, 15),
                            new VillagerTrades.ItemsForEmeralds(Blocks.BROWN_GLAZED_TERRACOTTA, 1, 1, 12, 15)
                        },
                        5,
                        new VillagerTrades.ItemListing[]{
                            new VillagerTrades.ItemsForEmeralds(Blocks.QUARTZ_PILLAR, 1, 1, 12, 30),
                            new VillagerTrades.ItemsForEmeralds(Blocks.QUARTZ_BLOCK, 1, 1, 12, 30)
                        }
                    )
                )
            );
        }
    );
    public static final Int2ObjectMap<VillagerTrades.ItemListing[]> WANDERING_TRADER_TRADES = toIntMap(
        ImmutableMap.of(
            1,
            new VillagerTrades.ItemListing[]{
                new VillagerTrades.ItemsForEmeralds(Items.SEA_PICKLE, 2, 1, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.SLIME_BALL, 4, 1, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.GLOWSTONE, 2, 1, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.NAUTILUS_SHELL, 5, 1, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.FERN, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.SUGAR_CANE, 1, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN, 1, 1, 4, 1),
                new VillagerTrades.ItemsForEmeralds(Items.KELP, 3, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.CACTUS, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.DANDELION, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.POPPY, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BLUE_ORCHID, 1, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.ALLIUM, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.AZURE_BLUET, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.RED_TULIP, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.ORANGE_TULIP, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.WHITE_TULIP, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PINK_TULIP, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.OXEYE_DAISY, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.CORNFLOWER, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.LILY_OF_THE_VALLEY, 1, 1, 7, 1),
                new VillagerTrades.ItemsForEmeralds(Items.WHEAT_SEEDS, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BEETROOT_SEEDS, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN_SEEDS, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.MELON_SEEDS, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.ACACIA_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BIRCH_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.DARK_OAK_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.JUNGLE_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.OAK_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.SPRUCE_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.CHERRY_SAPLING, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.MANGROVE_PROPAGULE, 5, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.RED_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.WHITE_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BLUE_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PINK_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BLACK_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.GREEN_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.YELLOW_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.GRAY_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PURPLE_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.LIME_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.ORANGE_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BROWN_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.CYAN_DYE, 1, 3, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BRAIN_CORAL_BLOCK, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BUBBLE_CORAL_BLOCK, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.FIRE_CORAL_BLOCK, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.HORN_CORAL_BLOCK, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.TUBE_CORAL_BLOCK, 3, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.VINE, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BROWN_MUSHROOM, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.RED_MUSHROOM, 1, 1, 12, 1),
                new VillagerTrades.ItemsForEmeralds(Items.LILY_PAD, 1, 2, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.SMALL_DRIPLEAF, 1, 2, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.SAND, 1, 8, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.RED_SAND, 1, 4, 6, 1),
                new VillagerTrades.ItemsForEmeralds(Items.POINTED_DRIPSTONE, 1, 2, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.ROOTED_DIRT, 1, 2, 5, 1),
                new VillagerTrades.ItemsForEmeralds(Items.MOSS_BLOCK, 1, 2, 5, 1)
            },
            2,
            new VillagerTrades.ItemListing[]{
                new VillagerTrades.ItemsForEmeralds(Items.TROPICAL_FISH_BUCKET, 5, 1, 4, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PUFFERFISH_BUCKET, 5, 1, 4, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PACKED_ICE, 3, 1, 6, 1),
                new VillagerTrades.ItemsForEmeralds(Items.BLUE_ICE, 6, 1, 6, 1),
                new VillagerTrades.ItemsForEmeralds(Items.GUNPOWDER, 1, 1, 8, 1),
                new VillagerTrades.ItemsForEmeralds(Items.PODZOL, 3, 3, 6, 1)
            }
        )
    );
    private static final VillagerTrades.TreasureMapForEmeralds DESERT_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_DESERT_VILLAGE_MAPS, "filled_map.village_desert", MapDecorationTypes.DESERT_VILLAGE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds SAVANNA_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_SAVANNA_VILLAGE_MAPS, "filled_map.village_savanna", MapDecorationTypes.SAVANNA_VILLAGE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds PLAINS_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_PLAINS_VILLAGE_MAPS, "filled_map.village_plains", MapDecorationTypes.PLAINS_VILLAGE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds TAIGA_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_TAIGA_VILLAGE_MAPS, "filled_map.village_taiga", MapDecorationTypes.TAIGA_VILLAGE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds SNOWY_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_SNOWY_VILLAGE_MAPS, "filled_map.village_snowy", MapDecorationTypes.SNOWY_VILLAGE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds JUNGLE_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_JUNGLE_EXPLORER_MAPS, "filled_map.explorer_jungle", MapDecorationTypes.JUNGLE_TEMPLE, 12, 5
    );
    private static final VillagerTrades.TreasureMapForEmeralds SWAMP_MAP = new VillagerTrades.TreasureMapForEmeralds(
        8, StructureTags.ON_SWAMP_EXPLORER_MAPS, "filled_map.explorer_swamp", MapDecorationTypes.SWAMP_HUT, 12, 5
    );
    public static final Map<VillagerProfession, Int2ObjectMap<VillagerTrades.ItemListing[]>> EXPERIMENTAL_TRADES = Map.of(
        VillagerProfession.LIBRARIAN,
        toIntMap(
            ImmutableMap.<Integer, VillagerTrades.ItemListing[]>builder()
                .put(
                    1,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2),
                        commonBooks(1),
                        new VillagerTrades.ItemsForEmeralds(Blocks.BOOKSHELF, 9, 1, 12, 1)
                    }
                )
                .put(
                    2,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.BOOK, 4, 12, 10), commonBooks(5), new VillagerTrades.ItemsForEmeralds(Items.LANTERN, 1, 1, 5)
                    }
                )
                .put(
                    3,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.INK_SAC, 5, 12, 20),
                        commonBooks(10),
                        new VillagerTrades.ItemsForEmeralds(Items.GLASS, 1, 4, 10)
                    }
                )
                .put(
                    4,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.WRITABLE_BOOK, 2, 12, 30),
                        new VillagerTrades.ItemsForEmeralds(Items.CLOCK, 5, 1, 15),
                        new VillagerTrades.ItemsForEmeralds(Items.COMPASS, 4, 1, 15)
                    }
                )
                .put(5, new VillagerTrades.ItemListing[]{specialBooks(), new VillagerTrades.ItemsForEmeralds(Items.NAME_TAG, 20, 1, 30)})
                .build()
        ),
        VillagerProfession.ARMORER,
        toIntMap(
            ImmutableMap.<Integer, VillagerTrades.ItemListing[]>builder()
                .put(
                    1,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.COAL, 15, 12, 2), new VillagerTrades.EmeraldForItems(Items.IRON_INGOT, 5, 12, 2)
                    }
                )
                .put(
                    2,
                    new VillagerTrades.ItemListing[]{
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.IRON_BOOTS, 4, 1, 12, 5, 0.05F),
                            VillagerType.DESERT,
                            VillagerType.PLAINS,
                            VillagerType.SAVANNA,
                            VillagerType.SNOW,
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_BOOTS, 4, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.IRON_HELMET, 5, 1, 12, 5, 0.05F),
                            VillagerType.DESERT,
                            VillagerType.PLAINS,
                            VillagerType.SAVANNA,
                            VillagerType.SNOW,
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_HELMET, 5, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.IRON_LEGGINGS, 7, 1, 12, 5, 0.05F),
                            VillagerType.DESERT,
                            VillagerType.PLAINS,
                            VillagerType.SAVANNA,
                            VillagerType.SNOW,
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_LEGGINGS, 7, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.IRON_CHESTPLATE, 9, 1, 12, 5, 0.05F),
                            VillagerType.DESERT,
                            VillagerType.PLAINS,
                            VillagerType.SAVANNA,
                            VillagerType.SNOW,
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(Items.CHAINMAIL_CHESTPLATE, 9, 1, 12, 5, 0.05F), VillagerType.JUNGLE, VillagerType.SWAMP
                        )
                    }
                )
                .put(
                    3,
                    new VillagerTrades.ItemListing[]{
                        new VillagerTrades.EmeraldForItems(Items.LAVA_BUCKET, 1, 12, 20),
                        new VillagerTrades.ItemsForEmeralds(Items.SHIELD, 5, 1, 12, 10, 0.05F),
                        new VillagerTrades.ItemsForEmeralds(Items.BELL, 36, 1, 12, 10, 0.2F)
                    }
                )
                .put(
                    4,
                    new VillagerTrades.ItemListing[]{
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_BOOTS_4
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_HELMET_4
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_LEGGINGS_4
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_CHESTPLATE_4
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_BOOTS_4
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_HELMET_4
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_LEGGINGS_4
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_CHESTPLATE_4
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_BOOTS, 2, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_BOOTS_4
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_HELMET, 3, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_HELMET_4
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_LEGGINGS, 5, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_LEGGINGS_4
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_CHESTPLATE, 7, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_CHESTPLATE_4
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_BOOTS_4
                            ),
                            VillagerType.SNOW
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.IRON_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_HELMET_4
                            ),
                            VillagerType.SNOW
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_BOOTS_4
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_HELMET_4
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_LEGGINGS_4
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_CHESTPLATE_4
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_BOOTS, 8, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_BOOTS_4
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_HELMET, 9, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_HELMET_4
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_LEGGINGS, 11, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_LEGGINGS_4
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_CHESTPLATE, 13, 1, 3, 15, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_CHESTPLATE_4
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_BOOTS, 1, 4, Items.DIAMOND_LEGGINGS, 1, 3, 15, 0.05F), VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_LEGGINGS, 1, 4, Items.DIAMOND_CHESTPLATE, 1, 3, 15, 0.05F),
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_HELMET, 1, 4, Items.DIAMOND_BOOTS, 1, 3, 15, 0.05F), VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(Items.DIAMOND_CHESTPLATE, 1, 2, Items.DIAMOND_HELMET, 1, 3, 15, 0.05F),
                            VillagerType.TAIGA
                        )
                    }
                )
                .put(
                    5,
                    new VillagerTrades.ItemListing[]{
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                4,
                                16,
                                Items.DIAMOND_CHESTPLATE,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_CHESTPLATE_5
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                3,
                                16,
                                Items.DIAMOND_LEGGINGS,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_DESERT_ARMORER_LEGGINGS_5
                            ),
                            VillagerType.DESERT
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                3,
                                16,
                                Items.DIAMOND_LEGGINGS,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_LEGGINGS_5
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND, 2, 12, Items.DIAMOND_BOOTS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_PLAINS_ARMORER_BOOTS_5
                            ),
                            VillagerType.PLAINS
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND, 2, 6, Items.DIAMOND_HELMET, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_HELMET_5
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                3,
                                8,
                                Items.DIAMOND_CHESTPLATE,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_SAVANNA_ARMORER_CHESTPLATE_5
                            ),
                            VillagerType.SAVANNA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND, 2, 12, Items.DIAMOND_BOOTS, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_BOOTS_5
                            ),
                            VillagerType.SNOW
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND, 3, 12, Items.DIAMOND_HELMET, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SNOW_ARMORER_HELMET_5
                            ),
                            VillagerType.SNOW
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_HELMET, 9, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_HELMET_5
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_BOOTS, 8, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_JUNGLE_ARMORER_BOOTS_5
                            ),
                            VillagerType.JUNGLE
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_HELMET, 9, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_HELMET_5
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsForEmeralds(
                                Items.CHAINMAIL_BOOTS, 8, 1, 3, 30, 0.05F, TradeRebalanceEnchantmentProviders.TRADES_SWAMP_ARMORER_BOOTS_5
                            ),
                            VillagerType.SWAMP
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                4,
                                18,
                                Items.DIAMOND_CHESTPLATE,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_TAIGA_ARMORER_CHESTPLATE_5
                            ),
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.ItemsAndEmeraldsToItems(
                                Items.DIAMOND,
                                3,
                                18,
                                Items.DIAMOND_LEGGINGS,
                                1,
                                3,
                                30,
                                0.05F,
                                TradeRebalanceEnchantmentProviders.TRADES_TAIGA_ARMORER_LEGGINGS_5
                            ),
                            VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.EmeraldForItems(Items.DIAMOND_BLOCK, 1, 12, 30, 42), VillagerType.TAIGA
                        ),
                        VillagerTrades.TypeSpecificTrade.oneTradeInBiomes(
                            new VillagerTrades.EmeraldForItems(Items.IRON_BLOCK, 1, 12, 30, 4),
                            VillagerType.DESERT,
                            VillagerType.JUNGLE,
                            VillagerType.PLAINS,
                            VillagerType.SAVANNA,
                            VillagerType.SNOW,
                            VillagerType.SWAMP
                        )
                    }
                )
                .build()
        ),
        VillagerProfession.CARTOGRAPHER,
        toIntMap(
            ImmutableMap.of(
                1,
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.EmeraldForItems(Items.PAPER, 24, 16, 2), new VillagerTrades.ItemsForEmeralds(Items.MAP, 7, 1, 1)
                },
                2,
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.EmeraldForItems(Items.GLASS_PANE, 11, 16, 10),
                    new VillagerTrades.TypeSpecificTrade(
                        ImmutableMap.<VillagerType, VillagerTrades.ItemListing>builder()
                            .put(VillagerType.DESERT, SAVANNA_MAP)
                            .put(VillagerType.SAVANNA, PLAINS_MAP)
                            .put(VillagerType.PLAINS, TAIGA_MAP)
                            .put(VillagerType.TAIGA, SNOWY_MAP)
                            .put(VillagerType.SNOW, PLAINS_MAP)
                            .put(VillagerType.JUNGLE, SAVANNA_MAP)
                            .put(VillagerType.SWAMP, SNOWY_MAP)
                            .build()
                    ),
                    new VillagerTrades.TypeSpecificTrade(
                        ImmutableMap.<VillagerType, VillagerTrades.ItemListing>builder()
                            .put(VillagerType.DESERT, PLAINS_MAP)
                            .put(VillagerType.SAVANNA, DESERT_MAP)
                            .put(VillagerType.PLAINS, SAVANNA_MAP)
                            .put(VillagerType.TAIGA, PLAINS_MAP)
                            .put(VillagerType.SNOW, TAIGA_MAP)
                            .put(VillagerType.JUNGLE, DESERT_MAP)
                            .put(VillagerType.SWAMP, TAIGA_MAP)
                            .build()
                    ),
                    new VillagerTrades.TypeSpecificTrade(
                        ImmutableMap.<VillagerType, VillagerTrades.ItemListing>builder()
                            .put(VillagerType.DESERT, JUNGLE_MAP)
                            .put(VillagerType.SAVANNA, JUNGLE_MAP)
                            .put(VillagerType.PLAINS, new VillagerTrades.FailureItemListing())
                            .put(VillagerType.TAIGA, SWAMP_MAP)
                            .put(VillagerType.SNOW, SWAMP_MAP)
                            .put(VillagerType.JUNGLE, SWAMP_MAP)
                            .put(VillagerType.SWAMP, JUNGLE_MAP)
                            .build()
                    )
                },
                3,
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.EmeraldForItems(Items.COMPASS, 1, 12, 20),
                    new VillagerTrades.TreasureMapForEmeralds(
                        13, StructureTags.ON_OCEAN_EXPLORER_MAPS, "filled_map.monument", MapDecorationTypes.OCEAN_MONUMENT, 12, 10
                    ),
                    new VillagerTrades.TreasureMapForEmeralds(
                        12, StructureTags.ON_TRIAL_CHAMBERS_MAPS, "filled_map.trial_chambers", MapDecorationTypes.TRIAL_CHAMBERS, 12, 10
                    )
                },
                4,
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.ItemsForEmeralds(Items.ITEM_FRAME, 7, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.WHITE_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.BLUE_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.RED_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.PINK_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.GREEN_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.LIME_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.GRAY_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.BLACK_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.PURPLE_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.CYAN_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.BROWN_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.YELLOW_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.ORANGE_BANNER, 3, 1, 15),
                    new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_BANNER, 3, 1, 15)
                },
                5,
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.ItemsForEmeralds(Items.GLOBE_BANNER_PATTERN, 8, 1, 30),
                    new VillagerTrades.TreasureMapForEmeralds(
                        14, StructureTags.ON_WOODLAND_EXPLORER_MAPS, "filled_map.mansion", MapDecorationTypes.WOODLAND_MANSION, 1, 30
                    )
                }
            )
        )
    );
    public static final List<Pair<VillagerTrades.ItemListing[], Integer>> EXPERIMENTAL_WANDERING_TRADER_TRADES = ImmutableList.<Pair<VillagerTrades.ItemListing[], Integer>>builder()
        .add(
            Pair.of(
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.EmeraldForItems(potionCost(Potions.WATER), 1, 1, 1),
                    new VillagerTrades.EmeraldForItems(Items.WATER_BUCKET, 1, 1, 1, 2),
                    new VillagerTrades.EmeraldForItems(Items.MILK_BUCKET, 1, 1, 1, 2),
                    new VillagerTrades.EmeraldForItems(Items.FERMENTED_SPIDER_EYE, 1, 1, 1, 3),
                    new VillagerTrades.EmeraldForItems(Items.BAKED_POTATO, 4, 1, 1),
                    new VillagerTrades.EmeraldForItems(Items.HAY_BLOCK, 1, 1, 1)
                },
                2
            )
        )
        .add(
            Pair.of(
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.ItemsForEmeralds(Items.PACKED_ICE, 1, 1, 6, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BLUE_ICE, 6, 1, 6, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.GUNPOWDER, 1, 4, 2, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PODZOL, 3, 3, 6, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.ACACIA_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.BIRCH_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.DARK_OAK_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.JUNGLE_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.OAK_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.SPRUCE_LOG, 1, 8, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Blocks.CHERRY_LOG, 1, 8, 4, 1),
                    new VillagerTrades.EnchantedItemForEmeralds(Items.IRON_PICKAXE, 1, 1, 1, 0.2F),
                    new VillagerTrades.ItemsForEmeralds(potion(Potions.LONG_INVISIBILITY), 5, 1, 1, 1)
                },
                2
            )
        )
        .add(
            Pair.of(
                new VillagerTrades.ItemListing[]{
                    new VillagerTrades.ItemsForEmeralds(Items.TROPICAL_FISH_BUCKET, 3, 1, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PUFFERFISH_BUCKET, 3, 1, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SEA_PICKLE, 2, 1, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SLIME_BALL, 4, 1, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.GLOWSTONE, 2, 1, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.NAUTILUS_SHELL, 5, 1, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.FERN, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SUGAR_CANE, 1, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN, 1, 1, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.KELP, 3, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.CACTUS, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.DANDELION, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.POPPY, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BLUE_ORCHID, 1, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.ALLIUM, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.AZURE_BLUET, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.RED_TULIP, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.ORANGE_TULIP, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.WHITE_TULIP, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PINK_TULIP, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.OXEYE_DAISY, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.CORNFLOWER, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.LILY_OF_THE_VALLEY, 1, 1, 7, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.WHEAT_SEEDS, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BEETROOT_SEEDS, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PUMPKIN_SEEDS, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.MELON_SEEDS, 1, 1, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.ACACIA_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BIRCH_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.DARK_OAK_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.JUNGLE_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.OAK_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SPRUCE_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.CHERRY_SAPLING, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.MANGROVE_PROPAGULE, 5, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.RED_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.WHITE_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BLUE_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PINK_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BLACK_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.GREEN_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.LIGHT_GRAY_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.MAGENTA_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.YELLOW_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.GRAY_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.PURPLE_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.LIGHT_BLUE_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.LIME_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.ORANGE_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BROWN_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.CYAN_DYE, 1, 3, 12, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BRAIN_CORAL_BLOCK, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BUBBLE_CORAL_BLOCK, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.FIRE_CORAL_BLOCK, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.HORN_CORAL_BLOCK, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.TUBE_CORAL_BLOCK, 3, 1, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.VINE, 1, 3, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.BROWN_MUSHROOM, 1, 3, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.RED_MUSHROOM, 1, 3, 4, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.LILY_PAD, 1, 5, 2, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SMALL_DRIPLEAF, 1, 2, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.SAND, 1, 8, 8, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.RED_SAND, 1, 4, 6, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.POINTED_DRIPSTONE, 1, 2, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.ROOTED_DIRT, 1, 2, 5, 1),
                    new VillagerTrades.ItemsForEmeralds(Items.MOSS_BLOCK, 1, 2, 5, 1)
                },
                5
            )
        )
        .build();

    private static VillagerTrades.ItemListing commonBooks(int p_294584_) {
        return new VillagerTrades.TypeSpecificTrade(
            ImmutableMap.<VillagerType, VillagerTrades.ItemListing>builder()
                .put(VillagerType.DESERT, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_DESERT_COMMON))
                .put(VillagerType.JUNGLE, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_JUNGLE_COMMON))
                .put(VillagerType.PLAINS, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_PLAINS_COMMON))
                .put(VillagerType.SAVANNA, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_SAVANNA_COMMON))
                .put(VillagerType.SNOW, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_SNOW_COMMON))
                .put(VillagerType.SWAMP, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_SWAMP_COMMON))
                .put(VillagerType.TAIGA, new VillagerTrades.EnchantBookForEmeralds(p_294584_, EnchantmentTags.TRADES_TAIGA_COMMON))
                .build()
        );
    }

    private static VillagerTrades.ItemListing specialBooks() {
        return new VillagerTrades.TypeSpecificTrade(
            ImmutableMap.<VillagerType, VillagerTrades.ItemListing>builder()
                .put(VillagerType.DESERT, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_DESERT_SPECIAL))
                .put(VillagerType.JUNGLE, new VillagerTrades.EnchantBookForEmeralds(30, 2, 2, EnchantmentTags.TRADES_JUNGLE_SPECIAL))
                .put(VillagerType.PLAINS, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_PLAINS_SPECIAL))
                .put(VillagerType.SAVANNA, new VillagerTrades.EnchantBookForEmeralds(30, 3, 3, EnchantmentTags.TRADES_SAVANNA_SPECIAL))
                .put(VillagerType.SNOW, new VillagerTrades.EnchantBookForEmeralds(30, EnchantmentTags.TRADES_SNOW_SPECIAL))
                .put(VillagerType.SWAMP, new VillagerTrades.EnchantBookForEmeralds(30, EnchantmentTags.TRADES_SWAMP_SPECIAL))
                .put(VillagerType.TAIGA, new VillagerTrades.EnchantBookForEmeralds(30, 2, 2, EnchantmentTags.TRADES_TAIGA_SPECIAL))
                .build()
        );
    }

    private static Int2ObjectMap<VillagerTrades.ItemListing[]> toIntMap(ImmutableMap<Integer, VillagerTrades.ItemListing[]> p_35631_) {
        return new Int2ObjectOpenHashMap<>(p_35631_);
    }

    private static ItemCost potionCost(Holder<Potion> p_331255_) {
        return new ItemCost(Items.POTION).withComponents(p_330063_ -> p_330063_.expect(DataComponents.POTION_CONTENTS, new PotionContents(p_331255_)));
    }

    private static ItemStack potion(Holder<Potion> p_316745_) {
        return PotionContents.createItemStack(Items.POTION, p_316745_);
    }

    public static class DyedArmorForEmeralds implements VillagerTrades.ItemListing {
        private final Item item;
        private final int value;
        private final int maxUses;
        private final int villagerXp;

        public DyedArmorForEmeralds(Item p_35639_, int p_35640_) {
            this(p_35639_, p_35640_, 12, 1);
        }

        public DyedArmorForEmeralds(Item p_35642_, int p_35643_, int p_35644_, int p_35645_) {
            this.item = p_35642_;
            this.value = p_35643_;
            this.maxUses = p_35644_;
            this.villagerXp = p_35645_;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219679_, RandomSource p_219680_) {
            ItemCost itemcost = new ItemCost(Items.EMERALD, this.value);
            ItemStack itemstack = new ItemStack(this.item);
            if (itemstack.is(ItemTags.DYEABLE)) {
                List<DyeItem> list = Lists.newArrayList();
                list.add(getRandomDye(p_219680_));
                if (p_219680_.nextFloat() > 0.7F) {
                    list.add(getRandomDye(p_219680_));
                }

                if (p_219680_.nextFloat() > 0.8F) {
                    list.add(getRandomDye(p_219680_));
                }

                itemstack = DyedItemColor.applyDyes(itemstack, list);
            }

            return new MerchantOffer(itemcost, itemstack, this.maxUses, this.villagerXp, 0.2F);
        }

        private static DyeItem getRandomDye(RandomSource p_219677_) {
            return DyeItem.byColor(DyeColor.byId(p_219677_.nextInt(16)));
        }
    }

    public static class EmeraldForItems implements VillagerTrades.ItemListing {
        private final ItemCost itemStack;
        private final int maxUses;
        private final int villagerXp;
        private final int emeraldAmount;
        private final float priceMultiplier;

        public EmeraldForItems(ItemLike p_35657_, int p_35658_, int p_35659_, int p_35660_) {
            this(p_35657_, p_35658_, p_35659_, p_35660_, 1);
        }

        public EmeraldForItems(ItemLike p_295166_, int p_294790_, int p_294810_, int p_295575_, int p_294582_) {
            this(new ItemCost(p_295166_.asItem(), p_294790_), p_294810_, p_295575_, p_294582_);
        }

        public EmeraldForItems(ItemCost p_331989_, int p_294800_, int p_295111_, int p_294137_) {
            this.itemStack = p_331989_;
            this.maxUses = p_294800_;
            this.villagerXp = p_295111_;
            this.emeraldAmount = p_294137_;
            this.priceMultiplier = 0.05F;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219682_, RandomSource p_219683_) {
            return new MerchantOffer(this.itemStack, new ItemStack(Items.EMERALD, this.emeraldAmount), this.maxUses, this.villagerXp, this.priceMultiplier);
        }
    }

    public static class EmeraldsForVillagerTypeItem implements VillagerTrades.ItemListing {
        private final Map<VillagerType, Item> trades;
        private final int cost;
        private final int maxUses;
        private final int villagerXp;

        public EmeraldsForVillagerTypeItem(int p_35669_, int p_35670_, int p_35671_, Map<VillagerType, Item> p_35672_) {
            if (false) // Neo: disable this check so that mods can add custom villager types
            BuiltInRegistries.VILLAGER_TYPE.stream().filter(p_35680_ -> !p_35672_.containsKey(p_35680_)).findAny().ifPresent(p_339515_ -> {
                throw new IllegalStateException("Missing trade for villager type: " + BuiltInRegistries.VILLAGER_TYPE.getKey(p_339515_));
            });
            this.trades = p_35672_;
            this.cost = p_35669_;
            this.maxUses = p_35670_;
            this.villagerXp = p_35671_;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity p_219685_, RandomSource p_219686_) {
            if (p_219685_ instanceof VillagerDataHolder villagerdataholder) {
                Item item = this.trades.get(villagerdataholder.getVillagerData().getType());
                if (item == null) return null;  // Neo: add a check for unknown villager types
                ItemCost itemcost = new ItemCost(item, this.cost);
                return new MerchantOffer(itemcost, new ItemStack(Items.EMERALD), this.maxUses, this.villagerXp, 0.05F);
            } else {
                return null;
            }
        }
    }

    public static class EnchantBookForEmeralds implements VillagerTrades.ItemListing {
        private final int villagerXp;
        private final TagKey<Enchantment> tradeableEnchantments;
        private final int minLevel;
        private final int maxLevel;

        public EnchantBookForEmeralds(int p_296300_, TagKey<Enchantment> p_345350_) {
            this(p_296300_, 0, Integer.MAX_VALUE, p_345350_);
        }

        public EnchantBookForEmeralds(int p_296323_, int p_345122_, int p_346010_, TagKey<Enchantment> p_346031_) {
            this.minLevel = p_345122_;
            this.maxLevel = p_346010_;
            this.villagerXp = p_296323_;
            this.tradeableEnchantments = p_346031_;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219688_, RandomSource p_219689_) {
            Optional<Holder<Enchantment>> optional = p_219688_.level()
                .registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getRandomElementOf(this.tradeableEnchantments, p_219689_);
            int i;
            ItemStack itemstack;
            if (!optional.isEmpty()) {
                Holder<Enchantment> holder = optional.get();
                Enchantment enchantment = holder.value();
                int j = Math.max(enchantment.getMinLevel(), this.minLevel);
                int k = Math.min(enchantment.getMaxLevel(), this.maxLevel);
                int l = Mth.nextInt(p_219689_, j, k);
                itemstack = EnchantedBookItem.createForEnchantment(new EnchantmentInstance(holder, l));
                i = 2 + p_219689_.nextInt(5 + l * 10) + 3 * l;
                if (holder.is(EnchantmentTags.DOUBLE_TRADE_PRICE)) {
                    i *= 2;
                }

                if (i > 64) {
                    i = 64;
                }
            } else {
                i = 1;
                itemstack = new ItemStack(Items.BOOK);
            }

            return new MerchantOffer(new ItemCost(Items.EMERALD, i), Optional.of(new ItemCost(Items.BOOK)), itemstack, 12, this.villagerXp, 0.2F);
        }
    }

    public static class EnchantedItemForEmeralds implements VillagerTrades.ItemListing {
        private final ItemStack itemStack;
        private final int baseEmeraldCost;
        private final int maxUses;
        private final int villagerXp;
        private final float priceMultiplier;

        public EnchantedItemForEmeralds(Item p_35693_, int p_35694_, int p_35695_, int p_35696_) {
            this(p_35693_, p_35694_, p_35695_, p_35696_, 0.05F);
        }

        public EnchantedItemForEmeralds(Item p_35698_, int p_35699_, int p_35700_, int p_35701_, float p_35702_) {
            this.itemStack = new ItemStack(p_35698_);
            this.baseEmeraldCost = p_35699_;
            this.maxUses = p_35700_;
            this.villagerXp = p_35701_;
            this.priceMultiplier = p_35702_;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219691_, RandomSource p_219692_) {
            int i = 5 + p_219692_.nextInt(15);
            RegistryAccess registryaccess = p_219691_.level().registryAccess();
            Optional<HolderSet.Named<Enchantment>> optional = registryaccess.registryOrThrow(Registries.ENCHANTMENT)
                .getTag(EnchantmentTags.ON_TRADED_EQUIPMENT);
            ItemStack itemstack = EnchantmentHelper.enchantItem(p_219692_, new ItemStack(this.itemStack.getItem()), i, registryaccess, optional);
            int j = Math.min(this.baseEmeraldCost + i, 64);
            ItemCost itemcost = new ItemCost(Items.EMERALD, j);
            return new MerchantOffer(itemcost, itemstack, this.maxUses, this.villagerXp, this.priceMultiplier);
        }
    }

    public static class FailureItemListing implements VillagerTrades.ItemListing {
        FailureItemListing() {}
        @Override
        public MerchantOffer getOffer(Entity p_302036_, RandomSource p_301986_) {
            return null;
        }
    }

    public interface ItemListing {
        @Nullable
        MerchantOffer getOffer(Entity p_219693_, RandomSource p_219694_);
    }

    public static class ItemsAndEmeraldsToItems implements VillagerTrades.ItemListing {
        private final ItemCost fromItem;
        private final int emeraldCost;
        private final ItemStack toItem;
        private final int maxUses;
        private final int villagerXp;
        private final float priceMultiplier;
        private final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider;

        public ItemsAndEmeraldsToItems(ItemLike p_35717_, int p_35718_, int p_35719_, Item p_35720_, int p_35721_, int p_35722_, int p_35723_, float p_301975_) {
            this(p_35717_, p_35718_, p_35719_, new ItemStack(p_35720_), p_35721_, p_35722_, p_35723_, p_301975_);
        }

        private ItemsAndEmeraldsToItems(
            ItemLike p_35725_, int p_35726_, int p_35728_, ItemStack p_302012_, int p_35729_, int p_35730_, int p_302002_, float p_302039_
        ) {
            this(new ItemCost(p_35725_, p_35726_), p_35728_, p_302012_.copyWithCount(p_35729_), p_35730_, p_302002_, p_302039_, Optional.empty());
        }

        ItemsAndEmeraldsToItems(
            ItemLike p_345985_,
            int p_320882_,
            int p_320089_,
            ItemLike p_345218_,
            int p_320458_,
            int p_345627_,
            int p_345902_,
            float p_319962_,
            ResourceKey<EnchantmentProvider> p_345388_
        ) {
            this(new ItemCost(p_345985_, p_320882_), p_320089_, new ItemStack(p_345218_, p_320458_), p_345627_, p_345902_, p_319962_, Optional.of(p_345388_));
        }

        public ItemsAndEmeraldsToItems(
            ItemCost p_345481_,
            int p_345494_,
            ItemStack p_345223_,
            int p_345894_,
            int p_344894_,
            float p_345457_,
            Optional<ResourceKey<EnchantmentProvider>> p_346401_
        ) {
            this.fromItem = p_345481_;
            this.emeraldCost = p_345494_;
            this.toItem = p_345223_;
            this.maxUses = p_345894_;
            this.villagerXp = p_344894_;
            this.priceMultiplier = p_345457_;
            this.enchantmentProvider = p_346401_;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity p_219696_, RandomSource p_219697_) {
            ItemStack itemstack = this.toItem.copy();
            Level level = p_219696_.level();
            this.enchantmentProvider
                .ifPresent(
                    p_348335_ -> EnchantmentHelper.enchantItemFromProvider(
                            itemstack,
                            level.registryAccess(),
                            (ResourceKey<EnchantmentProvider>)p_348335_,
                            level.getCurrentDifficultyAt(p_219696_.blockPosition()),
                            p_219697_
                        )
                );
            return new MerchantOffer(
                new ItemCost(Items.EMERALD, this.emeraldCost), Optional.of(this.fromItem), itemstack, 0, this.maxUses, this.villagerXp, this.priceMultiplier
            );
        }
    }

    public static class ItemsForEmeralds implements VillagerTrades.ItemListing {
        private final ItemStack itemStack;
        private final int emeraldCost;
        private final int maxUses;
        private final int villagerXp;
        private final float priceMultiplier;
        private final Optional<ResourceKey<EnchantmentProvider>> enchantmentProvider;

        public ItemsForEmeralds(Block p_35765_, int p_35766_, int p_35767_, int p_35768_, int p_35769_) {
            this(new ItemStack(p_35765_), p_35766_, p_35767_, p_35768_, p_35769_);
        }

        public ItemsForEmeralds(Item p_35741_, int p_35742_, int p_35743_, int p_35744_) {
            this(new ItemStack(p_35741_), p_35742_, p_35743_, 12, p_35744_);
        }

        public ItemsForEmeralds(Item p_35746_, int p_35747_, int p_35748_, int p_35749_, int p_35750_) {
            this(new ItemStack(p_35746_), p_35747_, p_35748_, p_35749_, p_35750_);
        }

        public ItemsForEmeralds(ItemStack p_35752_, int p_35753_, int p_35754_, int p_35755_, int p_35756_) {
            this(p_35752_, p_35753_, p_35754_, p_35755_, p_35756_, 0.05F);
        }

        public ItemsForEmeralds(Item p_302016_, int p_302006_, int p_302008_, int p_302010_, int p_301989_, float p_302037_) {
            this(new ItemStack(p_302016_), p_302006_, p_302008_, p_302010_, p_301989_, p_302037_);
        }

        public ItemsForEmeralds(
            Item p_345085_, int p_346208_, int p_345650_, int p_345298_, int p_345652_, float p_346098_, ResourceKey<EnchantmentProvider> p_345759_
        ) {
            this(new ItemStack(p_345085_), p_346208_, p_345650_, p_345298_, p_345652_, p_346098_, Optional.of(p_345759_));
        }

        public ItemsForEmeralds(ItemStack p_35758_, int p_35759_, int p_35760_, int p_35761_, int p_35762_, float p_35763_) {
            this(p_35758_, p_35759_, p_35760_, p_35761_, p_35762_, p_35763_, Optional.empty());
        }

        public ItemsForEmeralds(
            ItemStack p_344989_,
            int p_345411_,
            int p_345770_,
            int p_344818_,
            int p_345507_,
            float p_344802_,
            Optional<ResourceKey<EnchantmentProvider>> p_345806_
        ) {
            this.itemStack = p_344989_;
            this.emeraldCost = p_345411_;
            this.itemStack.setCount(p_345770_);
            this.maxUses = p_344818_;
            this.villagerXp = p_345507_;
            this.priceMultiplier = p_344802_;
            this.enchantmentProvider = p_345806_;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219699_, RandomSource p_219700_) {
            ItemStack itemstack = this.itemStack.copy();
            Level level = p_219699_.level();
            this.enchantmentProvider
                .ifPresent(
                    p_348340_ -> EnchantmentHelper.enchantItemFromProvider(
                            itemstack,
                            level.registryAccess(),
                            (ResourceKey<EnchantmentProvider>)p_348340_,
                            level.getCurrentDifficultyAt(p_219699_.blockPosition()),
                            p_219700_
                        )
                );
            return new MerchantOffer(new ItemCost(Items.EMERALD, this.emeraldCost), itemstack, this.maxUses, this.villagerXp, this.priceMultiplier);
        }
    }

    public static class SuspiciousStewForEmerald implements VillagerTrades.ItemListing {
        private final SuspiciousStewEffects effects;
        private final int xp;
        private final float priceMultiplier;

        public SuspiciousStewForEmerald(Holder<MobEffect> p_316230_, int p_186314_, int p_186315_) {
            this(new SuspiciousStewEffects(List.of(new SuspiciousStewEffects.Entry(p_316230_, p_186314_))), p_186315_, 0.05F);
        }

        public SuspiciousStewForEmerald(SuspiciousStewEffects p_330918_, int p_299043_, float p_298449_) {
            this.effects = p_330918_;
            this.xp = p_299043_;
            this.priceMultiplier = p_298449_;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity p_219702_, RandomSource p_219703_) {
            ItemStack itemstack = new ItemStack(Items.SUSPICIOUS_STEW, 1);
            itemstack.set(DataComponents.SUSPICIOUS_STEW_EFFECTS, this.effects);
            return new MerchantOffer(new ItemCost(Items.EMERALD), itemstack, 12, this.xp, this.priceMultiplier);
        }
    }

    public static class TippedArrowForItemsAndEmeralds implements VillagerTrades.ItemListing {
        private final ItemStack toItem;
        private final int toCount;
        private final int emeraldCost;
        private final int maxUses;
        private final int villagerXp;
        private final Item fromItem;
        private final int fromCount;
        private final float priceMultiplier;

        public TippedArrowForItemsAndEmeralds(Item p_35793_, int p_35794_, Item p_35795_, int p_35796_, int p_35797_, int p_35798_, int p_35799_) {
            this.toItem = new ItemStack(p_35795_);
            this.emeraldCost = p_35797_;
            this.maxUses = p_35798_;
            this.villagerXp = p_35799_;
            this.fromItem = p_35793_;
            this.fromCount = p_35794_;
            this.toCount = p_35796_;
            this.priceMultiplier = 0.05F;
        }

        @Override
        public MerchantOffer getOffer(Entity p_219705_, RandomSource p_219706_) {
            ItemCost itemcost = new ItemCost(Items.EMERALD, this.emeraldCost);
            List<Holder<Potion>> list = BuiltInRegistries.POTION
                .holders()
                .filter(p_340770_ -> !p_340770_.value().getEffects().isEmpty() && p_219705_.level().potionBrewing().isBrewablePotion(p_340770_))
                .collect(Collectors.toList());
            Holder<Potion> holder = Util.getRandom(list, p_219706_);
            ItemStack itemstack = new ItemStack(this.toItem.getItem(), this.toCount);
            itemstack.set(DataComponents.POTION_CONTENTS, new PotionContents(holder));
            return new MerchantOffer(
                itemcost, Optional.of(new ItemCost(this.fromItem, this.fromCount)), itemstack, this.maxUses, this.villagerXp, this.priceMultiplier
            );
        }
    }

    public static class TreasureMapForEmeralds implements VillagerTrades.ItemListing {
        private final int emeraldCost;
        private final TagKey<Structure> destination;
        private final String displayName;
        private final Holder<MapDecorationType> destinationType;
        private final int maxUses;
        private final int villagerXp;

        public TreasureMapForEmeralds(
            int p_207767_, TagKey<Structure> p_207768_, String p_207769_, Holder<MapDecorationType> p_335969_, int p_207771_, int p_207772_
        ) {
            this.emeraldCost = p_207767_;
            this.destination = p_207768_;
            this.displayName = p_207769_;
            this.destinationType = p_335969_;
            this.maxUses = p_207771_;
            this.villagerXp = p_207772_;
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity p_219708_, RandomSource p_219709_) {
            if (!(p_219708_.level() instanceof ServerLevel)) {
                return null;
            } else {
                ServerLevel serverlevel = (ServerLevel)p_219708_.level();
                BlockPos blockpos = serverlevel.findNearestMapStructure(this.destination, p_219708_.blockPosition(), 100, true);
                if (blockpos != null) {
                    ItemStack itemstack = MapItem.create(serverlevel, blockpos.getX(), blockpos.getZ(), (byte)2, true, true);
                    MapItem.renderBiomePreviewMap(serverlevel, itemstack);
                    MapItemSavedData.addTargetDecoration(itemstack, blockpos, "+", this.destinationType);
                    itemstack.set(DataComponents.ITEM_NAME, Component.translatable(this.displayName));
                    return new MerchantOffer(
                        new ItemCost(Items.EMERALD, this.emeraldCost), Optional.of(new ItemCost(Items.COMPASS)), itemstack, this.maxUses, this.villagerXp, 0.2F
                    );
                } else {
                    return null;
                }
            }
        }
    }

    public static record TypeSpecificTrade(Map<VillagerType, VillagerTrades.ItemListing> trades) implements VillagerTrades.ItemListing {
        public static VillagerTrades.TypeSpecificTrade oneTradeInBiomes(VillagerTrades.ItemListing p_302030_, VillagerType... p_301996_) {
            return new VillagerTrades.TypeSpecificTrade(
                Arrays.stream(p_301996_).collect(Collectors.toMap(p_301920_ -> (VillagerType)p_301920_, p_301922_ -> p_302030_))
            );
        }

        @Nullable
        @Override
        public MerchantOffer getOffer(Entity p_296480_, RandomSource p_295729_) {
            if (p_296480_ instanceof VillagerDataHolder villagerdataholder) {
                VillagerType villagertype = villagerdataholder.getVillagerData().getType();
                VillagerTrades.ItemListing villagertrades$itemlisting = this.trades.get(villagertype);
                return villagertrades$itemlisting == null ? null : villagertrades$itemlisting.getOffer(p_296480_, p_295729_);
            } else {
                return null;
            }
        }
    }
}
