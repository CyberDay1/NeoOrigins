package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public abstract class ItemEnchantmentsPredicate implements SingleComponentItemPredicate<ItemEnchantments> {
    private final List<EnchantmentPredicate> enchantments;

    protected ItemEnchantmentsPredicate(List<EnchantmentPredicate> p_333910_) {
        this.enchantments = p_333910_;
    }

    public static <T extends ItemEnchantmentsPredicate> Codec<T> codec(Function<List<EnchantmentPredicate>, T> p_333954_) {
        return EnchantmentPredicate.CODEC.listOf().xmap(p_333954_, ItemEnchantmentsPredicate::enchantments);
    }

    protected List<EnchantmentPredicate> enchantments() {
        return this.enchantments;
    }

    public boolean matches(ItemStack p_333811_, ItemEnchantments p_334059_) {
        for (EnchantmentPredicate enchantmentpredicate : this.enchantments) {
            if (!enchantmentpredicate.containedIn(p_334059_)) {
                return false;
            }
        }

        return true;
    }

    public static ItemEnchantmentsPredicate.Enchantments enchantments(List<EnchantmentPredicate> p_333715_) {
        return new ItemEnchantmentsPredicate.Enchantments(p_333715_);
    }

    public static ItemEnchantmentsPredicate.StoredEnchantments storedEnchantments(List<EnchantmentPredicate> p_333793_) {
        return new ItemEnchantmentsPredicate.StoredEnchantments(p_333793_);
    }

    public static class Enchantments extends ItemEnchantmentsPredicate {
        public static final Codec<ItemEnchantmentsPredicate.Enchantments> CODEC = codec(ItemEnchantmentsPredicate.Enchantments::new);

        protected Enchantments(List<EnchantmentPredicate> p_333967_) {
            super(p_333967_);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.ENCHANTMENTS;
        }

        // Neo: use IItemExtension#getAllEnchantments for enchantments when testing this predicate.
        @Override
        public boolean matches(ItemStack p_333958_) {
            var lookup = net.neoforged.neoforge.common.CommonHooks.resolveLookup(net.minecraft.core.registries.Registries.ENCHANTMENT);
            if (lookup != null) {
                return matches(p_333958_, p_333958_.getAllEnchantments(lookup));
            }
            return super.matches(p_333958_);
        }
    }

    public static class StoredEnchantments extends ItemEnchantmentsPredicate {
        public static final Codec<ItemEnchantmentsPredicate.StoredEnchantments> CODEC = codec(ItemEnchantmentsPredicate.StoredEnchantments::new);

        protected StoredEnchantments(List<EnchantmentPredicate> p_334002_) {
            super(p_334002_);
        }

        @Override
        public DataComponentType<ItemEnchantments> componentType() {
            return DataComponents.STORED_ENCHANTMENTS;
        }
    }
}
