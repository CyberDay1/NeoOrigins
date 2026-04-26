package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;

public record ItemFireworkExplosionPredicate(ItemFireworkExplosionPredicate.FireworkPredicate predicate)
    implements SingleComponentItemPredicate<FireworkExplosion> {
    public static final Codec<ItemFireworkExplosionPredicate> CODEC = ItemFireworkExplosionPredicate.FireworkPredicate.CODEC
        .xmap(ItemFireworkExplosionPredicate::new, ItemFireworkExplosionPredicate::predicate);

    @Override
    public DataComponentType<FireworkExplosion> componentType() {
        return DataComponents.FIREWORK_EXPLOSION;
    }

    public boolean matches(ItemStack p_341180_, FireworkExplosion p_340885_) {
        return this.predicate.test(p_340885_);
    }

    public static record FireworkPredicate(Optional<FireworkExplosion.Shape> shape, Optional<Boolean> twinkle, Optional<Boolean> trail)
        implements Predicate<FireworkExplosion> {
        public static final Codec<ItemFireworkExplosionPredicate.FireworkPredicate> CODEC = RecordCodecBuilder.create(
            p_340949_ -> p_340949_.group(
                        FireworkExplosion.Shape.CODEC.optionalFieldOf("shape").forGetter(ItemFireworkExplosionPredicate.FireworkPredicate::shape),
                        Codec.BOOL.optionalFieldOf("has_twinkle").forGetter(ItemFireworkExplosionPredicate.FireworkPredicate::twinkle),
                        Codec.BOOL.optionalFieldOf("has_trail").forGetter(ItemFireworkExplosionPredicate.FireworkPredicate::trail)
                    )
                    .apply(p_340949_, ItemFireworkExplosionPredicate.FireworkPredicate::new)
        );

        public boolean test(FireworkExplosion p_340895_) {
            if (this.shape.isPresent() && this.shape.get() != p_340895_.shape()) {
                return false;
            } else {
                return this.twinkle.isPresent() && this.twinkle.get() != p_340895_.hasTwinkle()
                    ? false
                    : !this.trail.isPresent() || this.trail.get() == p_340895_.hasTrail();
            }
        }
    }
}
