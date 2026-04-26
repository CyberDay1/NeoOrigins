package net.minecraft.world.item.enchantment;

import com.mojang.datafixers.util.Function4;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public record TargetedConditionalEffect<T>(EnchantmentTarget enchanted, EnchantmentTarget affected, T effect, Optional<LootItemCondition> requirements) {
    public static <S> Codec<TargetedConditionalEffect<S>> codec(Codec<S> p_344749_, LootContextParamSet p_345115_) {
        return RecordCodecBuilder.create(
            p_346355_ -> p_346355_.group(
                        EnchantmentTarget.CODEC.fieldOf("enchanted").forGetter(TargetedConditionalEffect::enchanted),
                        EnchantmentTarget.CODEC.fieldOf("affected").forGetter(TargetedConditionalEffect::affected),
                        p_344749_.fieldOf("effect").forGetter((Function<TargetedConditionalEffect<S>, S>)(TargetedConditionalEffect::effect)),
                        ConditionalEffect.conditionCodec(p_345115_).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
                    )
                    .apply(
                        p_346355_,
                        (Function4<EnchantmentTarget, EnchantmentTarget, S, Optional<LootItemCondition>, TargetedConditionalEffect<S>>)(TargetedConditionalEffect::new)
                    )
        );
    }

    public static <S> Codec<TargetedConditionalEffect<S>> equipmentDropsCodec(Codec<S> p_345181_, LootContextParamSet p_345924_) {
        return RecordCodecBuilder.create(
            p_346174_ -> p_346174_.group(
                        EnchantmentTarget.CODEC
                            .validate(
                                p_345851_ -> p_345851_ != EnchantmentTarget.DAMAGING_ENTITY
                                        ? DataResult.success(p_345851_)
                                        : DataResult.error(() -> "enchanted must be attacker or victim")
                            )
                            .fieldOf("enchanted")
                            .forGetter(TargetedConditionalEffect::enchanted),
                        p_345181_.fieldOf("effect").forGetter((Function<TargetedConditionalEffect<S>, S>)(TargetedConditionalEffect::effect)),
                        ConditionalEffect.conditionCodec(p_345924_).optionalFieldOf("requirements").forGetter(TargetedConditionalEffect::requirements)
                    )
                    .apply(
                        p_346174_,
                        (p_345692_, p_345215_, p_346096_) -> new TargetedConditionalEffect<>(p_345692_, EnchantmentTarget.VICTIM, p_345215_, p_346096_)
                    )
        );
    }

    public boolean matches(LootContext p_346180_) {
        return this.requirements.isEmpty() ? true : this.requirements.get().test(p_346180_);
    }
}
