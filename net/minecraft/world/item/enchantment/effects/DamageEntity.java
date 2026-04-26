package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.phys.Vec3;

public record DamageEntity(LevelBasedValue minDamage, LevelBasedValue maxDamage, Holder<DamageType> damageType) implements EnchantmentEntityEffect {
    public static final MapCodec<DamageEntity> CODEC = RecordCodecBuilder.mapCodec(
        p_345888_ -> p_345888_.group(
                    LevelBasedValue.CODEC.fieldOf("min_damage").forGetter(DamageEntity::minDamage),
                    LevelBasedValue.CODEC.fieldOf("max_damage").forGetter(DamageEntity::maxDamage),
                    DamageType.CODEC.fieldOf("damage_type").forGetter(DamageEntity::damageType)
                )
                .apply(p_345888_, DamageEntity::new)
    );

    @Override
    public void apply(ServerLevel p_345710_, int p_344734_, EnchantedItemInUse p_345002_, Entity p_345450_, Vec3 p_345442_) {
        float f = Mth.randomBetween(p_345450_.getRandom(), this.minDamage.calculate(p_344734_), this.maxDamage.calculate(p_344734_));
        p_345450_.hurt(new DamageSource(this.damageType, p_345002_.owner()), f);
    }

    @Override
    public MapCodec<DamageEntity> codec() {
        return CODEC;
    }
}
