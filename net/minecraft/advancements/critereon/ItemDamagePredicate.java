package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public record ItemDamagePredicate(MinMaxBounds.Ints durability, MinMaxBounds.Ints damage) implements SingleComponentItemPredicate<Integer> {
    public static final Codec<ItemDamagePredicate> CODEC = RecordCodecBuilder.create(
        p_337369_ -> p_337369_.group(
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("durability", MinMaxBounds.Ints.ANY).forGetter(ItemDamagePredicate::durability),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("damage", MinMaxBounds.Ints.ANY).forGetter(ItemDamagePredicate::damage)
                )
                .apply(p_337369_, ItemDamagePredicate::new)
    );

    @Override
    public DataComponentType<Integer> componentType() {
        return DataComponents.DAMAGE;
    }

    public boolean matches(ItemStack p_334074_, Integer p_333878_) {
        return !this.durability.matches(p_334074_.getMaxDamage() - p_333878_) ? false : this.damage.matches(p_333878_);
    }

    public static ItemDamagePredicate durability(MinMaxBounds.Ints p_334087_) {
        return new ItemDamagePredicate(p_334087_, MinMaxBounds.Ints.ANY);
    }
}
