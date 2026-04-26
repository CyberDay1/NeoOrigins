package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import it.unimi.dsi.fastutil.objects.Object2IntMap.Entry;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public record EnchantmentPredicate(Optional<HolderSet<Enchantment>> enchantments, MinMaxBounds.Ints level) {
    public static final Codec<EnchantmentPredicate> CODEC = RecordCodecBuilder.create(
        p_344145_ -> p_344145_.group(
                    RegistryCodecs.homogeneousList(Registries.ENCHANTMENT).optionalFieldOf("enchantments").forGetter(EnchantmentPredicate::enchantments),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("levels", MinMaxBounds.Ints.ANY).forGetter(EnchantmentPredicate::level)
                )
                .apply(p_344145_, EnchantmentPredicate::new)
    );

    public EnchantmentPredicate(Holder<Enchantment> p_345021_, MinMaxBounds.Ints p_345012_) {
        this(Optional.of(HolderSet.direct(p_345021_)), p_345012_);
    }

    public EnchantmentPredicate(HolderSet<Enchantment> p_346051_, MinMaxBounds.Ints p_30472_) {
        this(Optional.of(p_346051_), p_30472_);
    }

    public boolean containedIn(ItemEnchantments p_330878_) {
        if (this.enchantments.isPresent()) {
            for (Holder<Enchantment> holder : this.enchantments.get()) {
                if (this.matchesEnchantment(p_330878_, holder)) {
                    return true;
                }
            }

            return false;
        } else if (this.level != MinMaxBounds.Ints.ANY) {
            for (Entry<Holder<Enchantment>> entry : p_330878_.entrySet()) {
                if (this.level.matches(entry.getIntValue())) {
                    return true;
                }
            }

            return false;
        } else {
            return !p_330878_.isEmpty();
        }
    }

    private boolean matchesEnchantment(ItemEnchantments p_346384_, Holder<Enchantment> p_345318_) {
        int i = p_346384_.getLevel(p_345318_);
        if (i == 0) {
            return false;
        } else {
            return this.level == MinMaxBounds.Ints.ANY ? true : this.level.matches(i);
        }
    }
}
