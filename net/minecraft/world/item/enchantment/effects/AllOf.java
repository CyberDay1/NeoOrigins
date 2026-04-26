package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.phys.Vec3;

public interface AllOf {
    static <T, A extends T> MapCodec<A> codec(Codec<T> p_346365_, Function<List<T>, A> p_344745_, Function<A, List<T>> p_344875_) {
        return RecordCodecBuilder.mapCodec(p_345790_ -> p_345790_.group(p_346365_.listOf().fieldOf("effects").forGetter(p_344875_)).apply(p_345790_, p_344745_));
    }

    static AllOf.EntityEffects entityEffects(EnchantmentEntityEffect... p_345222_) {
        return new AllOf.EntityEffects(List.of(p_345222_));
    }

    static AllOf.LocationBasedEffects locationBasedEffects(EnchantmentLocationBasedEffect... p_345280_) {
        return new AllOf.LocationBasedEffects(List.of(p_345280_));
    }

    static AllOf.ValueEffects valueEffects(EnchantmentValueEffect... p_346375_) {
        return new AllOf.ValueEffects(List.of(p_346375_));
    }

    public static record EntityEffects(List<EnchantmentEntityEffect> effects) implements EnchantmentEntityEffect {
        public static final MapCodec<AllOf.EntityEffects> CODEC = AllOf.codec(
            EnchantmentEntityEffect.CODEC, AllOf.EntityEffects::new, AllOf.EntityEffects::effects
        );

        @Override
        public void apply(ServerLevel p_346093_, int p_345940_, EnchantedItemInUse p_344929_, Entity p_345319_, Vec3 p_345200_) {
            for (EnchantmentEntityEffect enchantmententityeffect : this.effects) {
                enchantmententityeffect.apply(p_346093_, p_345940_, p_344929_, p_345319_, p_345200_);
            }
        }

        @Override
        public MapCodec<AllOf.EntityEffects> codec() {
            return CODEC;
        }
    }

    public static record LocationBasedEffects(List<EnchantmentLocationBasedEffect> effects) implements EnchantmentLocationBasedEffect {
        public static final MapCodec<AllOf.LocationBasedEffects> CODEC = AllOf.codec(
            EnchantmentLocationBasedEffect.CODEC, AllOf.LocationBasedEffects::new, AllOf.LocationBasedEffects::effects
        );

        @Override
        public void onChangedBlock(ServerLevel p_345329_, int p_345154_, EnchantedItemInUse p_344984_, Entity p_345671_, Vec3 p_344781_, boolean p_345113_) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onChangedBlock(p_345329_, p_345154_, p_344984_, p_345671_, p_344781_, p_345113_);
            }
        }

        @Override
        public void onDeactivated(EnchantedItemInUse p_346024_, Entity p_346234_, Vec3 p_346036_, int p_345698_) {
            for (EnchantmentLocationBasedEffect enchantmentlocationbasedeffect : this.effects) {
                enchantmentlocationbasedeffect.onDeactivated(p_346024_, p_346234_, p_346036_, p_345698_);
            }
        }

        @Override
        public MapCodec<AllOf.LocationBasedEffects> codec() {
            return CODEC;
        }
    }

    public static record ValueEffects(List<EnchantmentValueEffect> effects) implements EnchantmentValueEffect {
        public static final MapCodec<AllOf.ValueEffects> CODEC = AllOf.codec(EnchantmentValueEffect.CODEC, AllOf.ValueEffects::new, AllOf.ValueEffects::effects);

        @Override
        public float process(int p_345324_, RandomSource p_345137_, float p_344866_) {
            for (EnchantmentValueEffect enchantmentvalueeffect : this.effects) {
                p_344866_ = enchantmentvalueeffect.process(p_345324_, p_345137_, p_344866_);
            }

            return p_344866_;
        }

        @Override
        public MapCodec<AllOf.ValueEffects> codec() {
            return CODEC;
        }
    }
}
