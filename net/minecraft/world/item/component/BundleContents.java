package net.minecraft.world.item.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
import org.apache.commons.lang3.math.Fraction;

public final class BundleContents implements TooltipComponent {
    public static final BundleContents EMPTY = new BundleContents(List.of());
    public static final Codec<BundleContents> CODEC = ItemStack.CODEC.listOf().xmap(BundleContents::new, p_331551_ -> p_331551_.items);
    public static final StreamCodec<RegistryFriendlyByteBuf, BundleContents> STREAM_CODEC = ItemStack.STREAM_CODEC
        .apply(ByteBufCodecs.list())
        .map(BundleContents::new, p_331649_ -> p_331649_.items);
    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);
    private static final int NO_STACK_INDEX = -1;
    final List<ItemStack> items;
    final Fraction weight;

    BundleContents(List<ItemStack> p_331277_, Fraction p_339622_) {
        this.items = p_331277_;
        this.weight = p_339622_;
    }

    public BundleContents(List<ItemStack> p_331417_) {
        this(p_331417_, computeContentWeight(p_331417_));
    }

    private static Fraction computeContentWeight(List<ItemStack> p_331148_) {
        Fraction fraction = Fraction.ZERO;

        for (ItemStack itemstack : p_331148_) {
            fraction = fraction.add(getWeight(itemstack).multiplyBy(Fraction.getFraction(itemstack.getCount(), 1)));
        }

        return fraction;
    }

    static Fraction getWeight(ItemStack p_332084_) {
        BundleContents bundlecontents = p_332084_.get(DataComponents.BUNDLE_CONTENTS);
        if (bundlecontents != null) {
            return BUNDLE_IN_BUNDLE_WEIGHT.add(bundlecontents.weight());
        } else {
            List<BeehiveBlockEntity.Occupant> list = p_332084_.getOrDefault(DataComponents.BEES, List.of());
            return !list.isEmpty() ? Fraction.ONE : Fraction.getFraction(1, p_332084_.getMaxStackSize());
        }
    }

    public ItemStack getItemUnsafe(int p_330802_) {
        return this.items.get(p_330802_);
    }

    public Stream<ItemStack> itemCopyStream() {
        return this.items.stream().map(ItemStack::copy);
    }

    public Iterable<ItemStack> items() {
        return this.items;
    }

    public Iterable<ItemStack> itemsCopy() {
        return Lists.transform(this.items, ItemStack::copy);
    }

    public int size() {
        return this.items.size();
    }

    public Fraction weight() {
        return this.weight;
    }

    public boolean isEmpty() {
        return this.items.isEmpty();
    }

    @Override
    public boolean equals(Object p_331898_) {
        if (this == p_331898_) {
            return true;
        } else {
            return !(p_331898_ instanceof BundleContents bundlecontents)
                ? false
                : this.weight.equals(bundlecontents.weight) && ItemStack.listMatches(this.items, bundlecontents.items);
        }
    }

    @Override
    public int hashCode() {
        return ItemStack.hashStackList(this.items);
    }

    @Override
    public String toString() {
        return "BundleContents" + this.items;
    }

    public static class Mutable {
        private final List<ItemStack> items;
        private Fraction weight;

        public Mutable(BundleContents p_332039_) {
            this.items = new ArrayList<>(p_332039_.items);
            this.weight = p_332039_.weight;
        }

        public BundleContents.Mutable clearItems() {
            this.items.clear();
            this.weight = Fraction.ZERO;
            return this;
        }

        private int findStackIndex(ItemStack p_331941_) {
            if (!p_331941_.isStackable()) {
                return -1;
            } else {
                for (int i = 0; i < this.items.size(); i++) {
                    if (ItemStack.isSameItemSameComponents(this.items.get(i), p_331941_)) {
                        return i;
                    }
                }

                return -1;
            }
        }

        private int getMaxAmountToAdd(ItemStack p_330527_) {
            Fraction fraction = Fraction.ONE.subtract(this.weight);
            return Math.max(fraction.divideBy(BundleContents.getWeight(p_330527_)).intValue(), 0);
        }

        public int tryInsert(ItemStack p_331789_) {
            if (!p_331789_.isEmpty() && p_331789_.canFitInsideContainerItems()) { // Neo: stack-aware placeability check
                int i = Math.min(p_331789_.getCount(), this.getMaxAmountToAdd(p_331789_));
                if (i == 0) {
                    return 0;
                } else {
                    this.weight = this.weight.add(BundleContents.getWeight(p_331789_).multiplyBy(Fraction.getFraction(i, 1)));
                    int j = this.findStackIndex(p_331789_);
                    if (j != -1) {
                        ItemStack itemstack = this.items.remove(j);
                        ItemStack itemstack1 = itemstack.copyWithCount(itemstack.getCount() + i);
                        p_331789_.shrink(i);
                        this.items.add(0, itemstack1);
                    } else {
                        this.items.add(0, p_331789_.split(i));
                    }

                    return i;
                }
            } else {
                return 0;
            }
        }

        public int tryTransfer(Slot p_330834_, Player p_331924_) {
            ItemStack itemstack = p_330834_.getItem();
            int i = this.getMaxAmountToAdd(itemstack);
            return this.tryInsert(p_330834_.safeTake(itemstack.getCount(), i, p_331924_));
        }

        @Nullable
        public ItemStack removeOne() {
            if (this.items.isEmpty()) {
                return null;
            } else {
                ItemStack itemstack = this.items.remove(0).copy();
                this.weight = this.weight.subtract(BundleContents.getWeight(itemstack).multiplyBy(Fraction.getFraction(itemstack.getCount(), 1)));
                return itemstack;
            }
        }

        public Fraction weight() {
            return this.weight;
        }

        public BundleContents toImmutable() {
            return new BundleContents(List.copyOf(this.items), this.weight);
        }
    }
}
