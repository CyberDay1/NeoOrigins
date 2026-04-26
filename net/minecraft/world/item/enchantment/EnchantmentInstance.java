package net.minecraft.world.item.enchantment;

import net.minecraft.core.Holder;
import net.minecraft.util.random.WeightedEntry;

public class EnchantmentInstance extends WeightedEntry.IntrusiveBase {
    public final Holder<Enchantment> enchantment;
    public final int level;

    public EnchantmentInstance(Holder<Enchantment> p_345467_, int p_44951_) {
        super(p_345467_.value().getWeight());
        this.enchantment = p_345467_;
        this.level = p_44951_;
    }
}
