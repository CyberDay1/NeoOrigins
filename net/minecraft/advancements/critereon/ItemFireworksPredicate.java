package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

public record ItemFireworksPredicate(
    Optional<CollectionPredicate<FireworkExplosion, ItemFireworkExplosionPredicate.FireworkPredicate>> explosions, MinMaxBounds.Ints flightDuration
) implements SingleComponentItemPredicate<Fireworks> {
    public static final Codec<ItemFireworksPredicate> CODEC = RecordCodecBuilder.create(
        p_341006_ -> p_341006_.group(
                    CollectionPredicate.<FireworkExplosion, ItemFireworkExplosionPredicate.FireworkPredicate>codec(
                            ItemFireworkExplosionPredicate.FireworkPredicate.CODEC
                        )
                        .optionalFieldOf("explosions")
                        .forGetter(ItemFireworksPredicate::explosions),
                    MinMaxBounds.Ints.CODEC.optionalFieldOf("flight_duration", MinMaxBounds.Ints.ANY).forGetter(ItemFireworksPredicate::flightDuration)
                )
                .apply(p_341006_, ItemFireworksPredicate::new)
    );

    @Override
    public DataComponentType<Fireworks> componentType() {
        return DataComponents.FIREWORKS;
    }

    public boolean matches(ItemStack p_341262_, Fireworks p_341415_) {
        return this.explosions.isPresent() && !this.explosions.get().test(p_341415_.explosions())
            ? false
            : this.flightDuration.matches(p_341415_.flightDuration());
    }
}
