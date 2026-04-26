package net.minecraft.world.item.enchantment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public class ItemEnchantments implements TooltipProvider {
    public static final ItemEnchantments EMPTY = new ItemEnchantments(new Object2IntOpenHashMap<>(), true);
    private static final Codec<Integer> LEVEL_CODEC = Codec.intRange(0, 255);
    private static final Codec<Object2IntOpenHashMap<Holder<Enchantment>>> LEVELS_CODEC = Codec.unboundedMap(Enchantment.CODEC, LEVEL_CODEC)
        .xmap(Object2IntOpenHashMap::new, Function.identity());
    private static final Codec<ItemEnchantments> FULL_CODEC = RecordCodecBuilder.create(
        p_337961_ -> p_337961_.group(
                    LEVELS_CODEC.fieldOf("levels").forGetter(p_340785_ -> p_340785_.enchantments),
                    Codec.BOOL.optionalFieldOf("show_in_tooltip", Boolean.valueOf(true)).forGetter(p_331891_ -> p_331891_.showInTooltip)
                )
                .apply(p_337961_, ItemEnchantments::new)
    );
    public static final Codec<ItemEnchantments> CODEC = Codec.withAlternative(FULL_CODEC, LEVELS_CODEC, p_340783_ -> new ItemEnchantments(p_340783_, true));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemEnchantments> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.map(Object2IntOpenHashMap::new, Enchantment.STREAM_CODEC, ByteBufCodecs.VAR_INT),
        p_340784_ -> p_340784_.enchantments,
        ByteBufCodecs.BOOL,
        p_330450_ -> p_330450_.showInTooltip,
        ItemEnchantments::new
    );
    final Object2IntOpenHashMap<Holder<Enchantment>> enchantments;
    final boolean showInTooltip;

    ItemEnchantments(Object2IntOpenHashMap<Holder<Enchantment>> p_341287_, boolean p_330219_) {
        this.enchantments = p_341287_;
        this.showInTooltip = p_330219_;

        for (Entry<Holder<Enchantment>> entry : p_341287_.object2IntEntrySet()) {
            int i = entry.getIntValue();
            if (i < 0 || i > 255) {
                throw new IllegalArgumentException("Enchantment " + entry.getKey() + " has invalid level " + i);
            }
        }
    }

    public int getLevel(Holder<Enchantment> p_345693_) {
        return this.enchantments.getInt(p_345693_);
    }

    @Override
    public void addToTooltip(Item.TooltipContext p_341290_, Consumer<Component> p_331119_, TooltipFlag p_330400_) {
        if (this.showInTooltip) {
            HolderLookup.Provider holderlookup$provider = p_341290_.registries();
            HolderSet<Enchantment> holderset = getTagOrEmpty(holderlookup$provider, Registries.ENCHANTMENT, EnchantmentTags.TOOLTIP_ORDER);

            for (Holder<Enchantment> holder : holderset) {
                int i = this.enchantments.getInt(holder);
                if (i > 0) {
                    p_331119_.accept(Enchantment.getFullname(holder, i));
                }
            }

            for (Entry<Holder<Enchantment>> entry : this.enchantments.object2IntEntrySet()) {
                Holder<Enchantment> holder1 = entry.getKey();
                if (!holderset.contains(holder1)) {
                    p_331119_.accept(Enchantment.getFullname(entry.getKey(), entry.getIntValue()));
                }
            }
        }
    }

    private static <T> HolderSet<T> getTagOrEmpty(@Nullable HolderLookup.Provider p_341186_, ResourceKey<Registry<T>> p_341113_, TagKey<T> p_341409_) {
        if (p_341186_ != null) {
            Optional<HolderSet.Named<T>> optional = p_341186_.lookupOrThrow(p_341113_).get(p_341409_);
            if (optional.isPresent()) {
                return optional.get();
            }
        }

        return HolderSet.direct();
    }

    public ItemEnchantments withTooltip(boolean p_335616_) {
        return new ItemEnchantments(this.enchantments, p_335616_);
    }

    public Set<Holder<Enchantment>> keySet() {
        return Collections.unmodifiableSet(this.enchantments.keySet());
    }

    public Set<Entry<Holder<Enchantment>>> entrySet() {
        return Collections.unmodifiableSet(this.enchantments.object2IntEntrySet());
    }

    public int size() {
        return this.enchantments.size();
    }

    public boolean isEmpty() {
        return this.enchantments.isEmpty();
    }

    @Override
    public boolean equals(Object p_331697_) {
        if (this == p_331697_) {
            return true;
        } else {
            return !(p_331697_ instanceof ItemEnchantments itemenchantments)
                ? false
                : this.showInTooltip == itemenchantments.showInTooltip && this.enchantments.equals(itemenchantments.enchantments);
        }
    }

    @Override
    public int hashCode() {
        int i = this.enchantments.hashCode();
        return 31 * i + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public String toString() {
        return "ItemEnchantments{enchantments=" + this.enchantments + ", showInTooltip=" + this.showInTooltip + "}";
    }

    public static class Mutable {
        private final Object2IntOpenHashMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        private final boolean showInTooltip;

        public Mutable(ItemEnchantments p_330722_) {
            this.enchantments.putAll(p_330722_.enchantments);
            this.showInTooltip = p_330722_.showInTooltip;
        }

        public void set(Holder<Enchantment> p_344861_, int p_330832_) {
            if (p_330832_ <= 0) {
                this.enchantments.removeInt(p_344861_);
            } else {
                this.enchantments.put(p_344861_, Math.min(p_330832_, 255));
            }
        }

        public void upgrade(Holder<Enchantment> p_346263_, int p_331153_) {
            if (p_331153_ > 0) {
                this.enchantments.merge(p_346263_, Math.min(p_331153_, 255), Integer::max);
            }
        }

        public void removeIf(Predicate<Holder<Enchantment>> p_332079_) {
            this.enchantments.keySet().removeIf(p_332079_);
        }

        public int getLevel(Holder<Enchantment> p_345430_) {
            return this.enchantments.getOrDefault(p_345430_, 0);
        }

        public Set<Holder<Enchantment>> keySet() {
            return this.enchantments.keySet();
        }

        public ItemEnchantments toImmutable() {
            return new ItemEnchantments(this.enchantments, this.showInTooltip);
        }
    }
}
