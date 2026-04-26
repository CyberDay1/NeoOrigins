package net.minecraft.client.color.item;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.core.IdMapper;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.MapItemColor;
import net.minecraft.world.level.FoliageColor;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ItemColors {
    private static final int DEFAULT = -1;
    // Neo: Use the item instance directly as non-Vanilla item ids are not constant
    private final java.util.Map<Item, ItemColor> itemColors = new java.util.IdentityHashMap<>();

    public static ItemColors createDefault(BlockColors p_92684_) {
        ItemColors itemcolors = new ItemColors();
        itemcolors.register(
            (p_329705_, p_329706_) -> p_329706_ > 0 ? -1 : DyedItemColor.getOrDefault(p_329705_, -6265536),
            Items.LEATHER_HELMET,
            Items.LEATHER_CHESTPLATE,
            Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS,
            Items.LEATHER_HORSE_ARMOR
        );
        itemcolors.register((p_329699_, p_329700_) -> p_329700_ != 1 ? -1 : DyedItemColor.getOrDefault(p_329699_, 0), Items.WOLF_ARMOR);
        itemcolors.register((p_92705_, p_92706_) -> GrassColor.get(0.5, 1.0), Blocks.TALL_GRASS, Blocks.LARGE_FERN);
        itemcolors.register((p_329701_, p_329702_) -> {
            if (p_329702_ != 1) {
                return -1;
            } else {
                FireworkExplosion fireworkexplosion = p_329701_.get(DataComponents.FIREWORK_EXPLOSION);
                IntList intlist = fireworkexplosion != null ? fireworkexplosion.colors() : IntList.of();
                int i = intlist.size();
                if (i == 0) {
                    return -7697782;
                } else if (i == 1) {
                    return FastColor.ARGB32.opaque(intlist.getInt(0));
                } else {
                    int j = 0;
                    int k = 0;
                    int l = 0;

                    for (int i1 = 0; i1 < i; i1++) {
                        int j1 = intlist.getInt(i1);
                        j += FastColor.ARGB32.red(j1);
                        k += FastColor.ARGB32.green(j1);
                        l += FastColor.ARGB32.blue(j1);
                    }

                    return FastColor.ARGB32.color(j / i, k / i, l / i);
                }
            }
        }, Items.FIREWORK_STAR);
        itemcolors.register(
            (p_329703_, p_329704_) -> p_329704_ > 0
                    ? -1
                    : FastColor.ARGB32.opaque(p_329703_.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor()),
            Items.POTION,
            Items.SPLASH_POTION,
            Items.LINGERING_POTION,
            Items.TIPPED_ARROW
        );

        for (SpawnEggItem spawneggitem : SpawnEggItem.eggs()) {
            itemcolors.register((p_329708_, p_329709_) -> FastColor.ARGB32.opaque(spawneggitem.getColor(p_329709_)), spawneggitem);
        }

        itemcolors.register(
            (p_92687_, p_92688_) -> {
                BlockState blockstate = ((BlockItem)p_92687_.getItem()).getBlock().defaultBlockState();
                return p_92684_.getColor(blockstate, null, null, p_92688_);
            },
            Blocks.GRASS_BLOCK,
            Blocks.SHORT_GRASS,
            Blocks.FERN,
            Blocks.VINE,
            Blocks.OAK_LEAVES,
            Blocks.SPRUCE_LEAVES,
            Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES,
            Blocks.ACACIA_LEAVES,
            Blocks.DARK_OAK_LEAVES,
            Blocks.LILY_PAD
        );
        itemcolors.register((p_92696_, p_92697_) -> FoliageColor.getMangroveColor(), Blocks.MANGROVE_LEAVES);
        itemcolors.register(
            (p_329710_, p_329711_) -> p_329711_ == 0
                    ? -1
                    : FastColor.ARGB32.opaque(p_329710_.getOrDefault(DataComponents.MAP_COLOR, MapItemColor.DEFAULT).rgb()),
            Items.FILLED_MAP
        );
        net.neoforged.neoforge.client.ClientHooks.onItemColorsInit(itemcolors, p_92684_);
        return itemcolors;
    }

    public int getColor(ItemStack p_92677_, int p_92678_) {
        ItemColor itemcolor = this.itemColors.get(p_92677_.getItem());
        return itemcolor == null ? -1 : itemcolor.getColor(p_92677_, p_92678_);
    }

    /** @deprecated Register via {@link net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item} */
    @Deprecated
    public void register(ItemColor p_92690_, ItemLike... p_92691_) {
        for (ItemLike itemlike : p_92691_) {
            this.itemColors.put(itemlike.asItem(), p_92690_);
        }
    }
}
