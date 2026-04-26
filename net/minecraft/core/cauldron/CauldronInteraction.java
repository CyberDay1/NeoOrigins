package net.minecraft.core.cauldron;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

public interface CauldronInteraction {
    Map<String, CauldronInteraction.InteractionMap> INTERACTIONS = new Object2ObjectArrayMap<>();
    Codec<CauldronInteraction.InteractionMap> CODEC = Codec.stringResolver(CauldronInteraction.InteractionMap::name, INTERACTIONS::get);
    CauldronInteraction.InteractionMap EMPTY = newInteractionMap("empty");
    CauldronInteraction.InteractionMap WATER = newInteractionMap("water");
    CauldronInteraction.InteractionMap LAVA = newInteractionMap("lava");
    CauldronInteraction.InteractionMap POWDER_SNOW = newInteractionMap("powder_snow");
    CauldronInteraction FILL_WATER = (p_315877_, p_315878_, p_315879_, p_315880_, p_315881_, p_315882_) -> emptyBucket(
            p_315878_,
            p_315879_,
            p_315880_,
            p_315881_,
            p_315882_,
            Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, Integer.valueOf(3)),
            SoundEvents.BUCKET_EMPTY
        );
    CauldronInteraction FILL_LAVA = (p_315853_, p_315854_, p_315855_, p_315856_, p_315857_, p_315858_) -> emptyBucket(
            p_315854_, p_315855_, p_315856_, p_315857_, p_315858_, Blocks.LAVA_CAULDRON.defaultBlockState(), SoundEvents.BUCKET_EMPTY_LAVA
        );
    CauldronInteraction FILL_POWDER_SNOW = (p_315871_, p_315872_, p_315873_, p_315874_, p_315875_, p_315876_) -> emptyBucket(
            p_315872_,
            p_315873_,
            p_315874_,
            p_315875_,
            p_315876_,
            Blocks.POWDER_SNOW_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, Integer.valueOf(3)),
            SoundEvents.BUCKET_EMPTY_POWDER_SNOW
        );
    CauldronInteraction SHULKER_BOX = (p_346995_, p_346996_, p_346997_, p_346998_, p_346999_, p_347000_) -> {
        Block block = Block.byItem(p_347000_.getItem());
        if (!(block instanceof ShulkerBoxBlock)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else {
            if (!p_346996_.isClientSide) {
                ItemStack itemstack = p_347000_.transmuteCopy(Blocks.SHULKER_BOX, 1);
                p_346998_.setItemInHand(p_346999_, ItemUtils.createFilledResult(p_347000_, p_346998_, itemstack, false));
                p_346998_.awardStat(Stats.CLEAN_SHULKER_BOX);
                LayeredCauldronBlock.lowerFillLevel(p_346995_, p_346996_, p_346997_);
            }

            return ItemInteractionResult.sidedSuccess(p_346996_.isClientSide);
        }
    };
    CauldronInteraction BANNER = (p_347001_, p_347002_, p_347003_, p_347004_, p_347005_, p_347006_) -> {
        BannerPatternLayers bannerpatternlayers = p_347006_.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        if (bannerpatternlayers.layers().isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else {
            if (!p_347002_.isClientSide) {
                ItemStack itemstack = p_347006_.copyWithCount(1);
                itemstack.set(DataComponents.BANNER_PATTERNS, bannerpatternlayers.removeLast());
                p_347004_.setItemInHand(p_347005_, ItemUtils.createFilledResult(p_347006_, p_347004_, itemstack, false));
                p_347004_.awardStat(Stats.CLEAN_BANNER);
                LayeredCauldronBlock.lowerFillLevel(p_347001_, p_347002_, p_347003_);
            }

            return ItemInteractionResult.sidedSuccess(p_347002_.isClientSide);
        }
    };
    CauldronInteraction DYED_ITEM = (p_329837_, p_329838_, p_329839_, p_329840_, p_329841_, p_329842_) -> {
        if (!p_329842_.is(ItemTags.DYEABLE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else if (!p_329842_.has(DataComponents.DYED_COLOR)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else {
            if (!p_329838_.isClientSide) {
                p_329842_.remove(DataComponents.DYED_COLOR);
                p_329840_.awardStat(Stats.CLEAN_ARMOR);
                LayeredCauldronBlock.lowerFillLevel(p_329837_, p_329838_, p_329839_);
            }

            return ItemInteractionResult.sidedSuccess(p_329838_.isClientSide);
        }
    };

    static CauldronInteraction.InteractionMap newInteractionMap(String p_304841_) {
        Object2ObjectOpenHashMap<Item, CauldronInteraction> object2objectopenhashmap = new Object2ObjectOpenHashMap<>();
        object2objectopenhashmap.defaultReturnValue(
            (p_315883_, p_315884_, p_315885_, p_315886_, p_315887_, p_315888_) -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        );
        CauldronInteraction.InteractionMap cauldroninteraction$interactionmap = new CauldronInteraction.InteractionMap(p_304841_, object2objectopenhashmap);
        INTERACTIONS.put(p_304841_, cauldroninteraction$interactionmap);
        return cauldroninteraction$interactionmap;
    }

    ItemInteractionResult interact(BlockState p_175711_, Level p_175712_, BlockPos p_175713_, Player p_175714_, InteractionHand p_175715_, ItemStack p_175716_);

    static void bootStrap() {
        Map<Item, CauldronInteraction> map = EMPTY.map();
        addDefaultInteractions(map);
        map.put(Items.POTION, (p_329825_, p_329826_, p_329827_, p_329828_, p_329829_, p_329830_) -> {
            PotionContents potioncontents = p_329830_.get(DataComponents.POTION_CONTENTS);
            if (potioncontents != null && potioncontents.is(Potions.WATER)) {
                if (!p_329826_.isClientSide) {
                    Item item = p_329830_.getItem();
                    p_329828_.setItemInHand(p_329829_, ItemUtils.createFilledResult(p_329830_, p_329828_, new ItemStack(Items.GLASS_BOTTLE)));
                    p_329828_.awardStat(Stats.USE_CAULDRON);
                    p_329828_.awardStat(Stats.ITEM_USED.get(item));
                    p_329826_.setBlockAndUpdate(p_329827_, Blocks.WATER_CAULDRON.defaultBlockState());
                    p_329826_.playSound(null, p_329827_, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                    p_329826_.gameEvent(null, GameEvent.FLUID_PLACE, p_329827_);
                }

                return ItemInteractionResult.sidedSuccess(p_329826_.isClientSide);
            } else {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
        });
        Map<Item, CauldronInteraction> map1 = WATER.map();
        addDefaultInteractions(map1);
        map1.put(
            Items.BUCKET,
            (p_315865_, p_315866_, p_315867_, p_315868_, p_315869_, p_315870_) -> fillBucket(
                    p_315865_,
                    p_315866_,
                    p_315867_,
                    p_315868_,
                    p_315869_,
                    p_315870_,
                    new ItemStack(Items.WATER_BUCKET),
                    p_175660_ -> p_175660_.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    SoundEvents.BUCKET_FILL
                )
        );
        map1.put(
            Items.GLASS_BOTTLE,
            (p_329819_, p_329820_, p_329821_, p_329822_, p_329823_, p_329824_) -> {
                if (!p_329820_.isClientSide) {
                    Item item = p_329824_.getItem();
                    p_329822_.setItemInHand(
                        p_329823_, ItemUtils.createFilledResult(p_329824_, p_329822_, PotionContents.createItemStack(Items.POTION, Potions.WATER))
                    );
                    p_329822_.awardStat(Stats.USE_CAULDRON);
                    p_329822_.awardStat(Stats.ITEM_USED.get(item));
                    LayeredCauldronBlock.lowerFillLevel(p_329819_, p_329820_, p_329821_);
                    p_329820_.playSound(null, p_329821_, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                    p_329820_.gameEvent(null, GameEvent.FLUID_PICKUP, p_329821_);
                }

                return ItemInteractionResult.sidedSuccess(p_329820_.isClientSide);
            }
        );
        map1.put(Items.POTION, (p_175704_, p_175705_, p_175706_, p_175707_, p_175708_, p_175709_) -> {
            if (p_175704_.getValue(LayeredCauldronBlock.LEVEL) == 3) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            } else {
                PotionContents potioncontents = p_175709_.get(DataComponents.POTION_CONTENTS);
                if (potioncontents != null && potioncontents.is(Potions.WATER)) {
                    if (!p_175705_.isClientSide) {
                        p_175707_.setItemInHand(p_175708_, ItemUtils.createFilledResult(p_175709_, p_175707_, new ItemStack(Items.GLASS_BOTTLE)));
                        p_175707_.awardStat(Stats.USE_CAULDRON);
                        p_175707_.awardStat(Stats.ITEM_USED.get(p_175709_.getItem()));
                        p_175705_.setBlockAndUpdate(p_175706_, p_175704_.cycle(LayeredCauldronBlock.LEVEL));
                        p_175705_.playSound(null, p_175706_, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
                        p_175705_.gameEvent(null, GameEvent.FLUID_PLACE, p_175706_);
                    }

                    return ItemInteractionResult.sidedSuccess(p_175705_.isClientSide);
                } else {
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
            }
        });
        map1.put(Items.LEATHER_BOOTS, DYED_ITEM);
        map1.put(Items.LEATHER_LEGGINGS, DYED_ITEM);
        map1.put(Items.LEATHER_CHESTPLATE, DYED_ITEM);
        map1.put(Items.LEATHER_HELMET, DYED_ITEM);
        map1.put(Items.LEATHER_HORSE_ARMOR, DYED_ITEM);
        map1.put(Items.WOLF_ARMOR, DYED_ITEM);
        map1.put(Items.WHITE_BANNER, BANNER);
        map1.put(Items.GRAY_BANNER, BANNER);
        map1.put(Items.BLACK_BANNER, BANNER);
        map1.put(Items.BLUE_BANNER, BANNER);
        map1.put(Items.BROWN_BANNER, BANNER);
        map1.put(Items.CYAN_BANNER, BANNER);
        map1.put(Items.GREEN_BANNER, BANNER);
        map1.put(Items.LIGHT_BLUE_BANNER, BANNER);
        map1.put(Items.LIGHT_GRAY_BANNER, BANNER);
        map1.put(Items.LIME_BANNER, BANNER);
        map1.put(Items.MAGENTA_BANNER, BANNER);
        map1.put(Items.ORANGE_BANNER, BANNER);
        map1.put(Items.PINK_BANNER, BANNER);
        map1.put(Items.PURPLE_BANNER, BANNER);
        map1.put(Items.RED_BANNER, BANNER);
        map1.put(Items.YELLOW_BANNER, BANNER);
        map1.put(Items.WHITE_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.GRAY_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.BLACK_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.BLUE_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.BROWN_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.CYAN_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.GREEN_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.LIGHT_BLUE_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.LIGHT_GRAY_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.LIME_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.MAGENTA_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.ORANGE_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.PINK_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.PURPLE_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.RED_SHULKER_BOX, SHULKER_BOX);
        map1.put(Items.YELLOW_SHULKER_BOX, SHULKER_BOX);
        Map<Item, CauldronInteraction> map2 = LAVA.map();
        map2.put(
            Items.BUCKET,
            (p_315889_, p_315890_, p_315891_, p_315892_, p_315893_, p_315894_) -> fillBucket(
                    p_315889_,
                    p_315890_,
                    p_315891_,
                    p_315892_,
                    p_315893_,
                    p_315894_,
                    new ItemStack(Items.LAVA_BUCKET),
                    p_175651_ -> true,
                    SoundEvents.BUCKET_FILL_LAVA
                )
        );
        addDefaultInteractions(map2);
        Map<Item, CauldronInteraction> map3 = POWDER_SNOW.map();
        map3.put(
            Items.BUCKET,
            (p_315859_, p_315860_, p_315861_, p_315862_, p_315863_, p_315864_) -> fillBucket(
                    p_315859_,
                    p_315860_,
                    p_315861_,
                    p_315862_,
                    p_315863_,
                    p_315864_,
                    new ItemStack(Items.POWDER_SNOW_BUCKET),
                    p_175627_ -> p_175627_.getValue(LayeredCauldronBlock.LEVEL) == 3,
                    SoundEvents.BUCKET_FILL_POWDER_SNOW
                )
        );
        addDefaultInteractions(map3);
    }

    static void addDefaultInteractions(Map<Item, CauldronInteraction> p_175648_) {
        p_175648_.put(Items.LAVA_BUCKET, FILL_LAVA);
        p_175648_.put(Items.WATER_BUCKET, FILL_WATER);
        p_175648_.put(Items.POWDER_SNOW_BUCKET, FILL_POWDER_SNOW);
    }

    static ItemInteractionResult fillBucket(
        BlockState p_175636_,
        Level p_175637_,
        BlockPos p_175638_,
        Player p_175639_,
        InteractionHand p_175640_,
        ItemStack p_175641_,
        ItemStack p_175642_,
        Predicate<BlockState> p_175643_,
        SoundEvent p_175644_
    ) {
        if (!p_175643_.test(p_175636_)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else {
            if (!p_175637_.isClientSide) {
                Item item = p_175641_.getItem();
                p_175639_.setItemInHand(p_175640_, ItemUtils.createFilledResult(p_175641_, p_175639_, p_175642_));
                p_175639_.awardStat(Stats.USE_CAULDRON);
                p_175639_.awardStat(Stats.ITEM_USED.get(item));
                p_175637_.setBlockAndUpdate(p_175638_, Blocks.CAULDRON.defaultBlockState());
                p_175637_.playSound(null, p_175638_, p_175644_, SoundSource.BLOCKS, 1.0F, 1.0F);
                p_175637_.gameEvent(null, GameEvent.FLUID_PICKUP, p_175638_);
            }

            return ItemInteractionResult.sidedSuccess(p_175637_.isClientSide);
        }
    }

    static ItemInteractionResult emptyBucket(
        Level p_175619_, BlockPos p_175620_, Player p_175621_, InteractionHand p_175622_, ItemStack p_175623_, BlockState p_175624_, SoundEvent p_175625_
    ) {
        if (!p_175619_.isClientSide) {
            Item item = p_175623_.getItem();
            p_175621_.setItemInHand(p_175622_, ItemUtils.createFilledResult(p_175623_, p_175621_, new ItemStack(Items.BUCKET)));
            p_175621_.awardStat(Stats.FILL_CAULDRON);
            p_175621_.awardStat(Stats.ITEM_USED.get(item));
            p_175619_.setBlockAndUpdate(p_175620_, p_175624_);
            p_175619_.playSound(null, p_175620_, p_175625_, SoundSource.BLOCKS, 1.0F, 1.0F);
            p_175619_.gameEvent(null, GameEvent.FLUID_PLACE, p_175620_);
        }

        return ItemInteractionResult.sidedSuccess(p_175619_.isClientSide);
    }

    public static record InteractionMap(String name, Map<Item, CauldronInteraction> map) {
    }
}
