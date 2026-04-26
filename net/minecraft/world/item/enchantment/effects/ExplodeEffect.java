package net.minecraft.world.item.enchantment.effects;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public record ExplodeEffect(
    boolean attributeToUser,
    Optional<Holder<DamageType>> damageType,
    Optional<LevelBasedValue> knockbackMultiplier,
    Optional<HolderSet<Block>> immuneBlocks,
    Vec3 offset,
    LevelBasedValue radius,
    boolean createFire,
    Level.ExplosionInteraction blockInteraction,
    ParticleOptions smallParticle,
    ParticleOptions largeParticle,
    Holder<SoundEvent> sound
) implements EnchantmentEntityEffect {
    public static final MapCodec<ExplodeEffect> CODEC = RecordCodecBuilder.mapCodec(
        p_344933_ -> p_344933_.group(
                    Codec.BOOL.optionalFieldOf("attribute_to_user", Boolean.valueOf(false)).forGetter(ExplodeEffect::attributeToUser),
                    DamageType.CODEC.optionalFieldOf("damage_type").forGetter(ExplodeEffect::damageType),
                    LevelBasedValue.CODEC.optionalFieldOf("knockback_multiplier").forGetter(ExplodeEffect::knockbackMultiplier),
                    RegistryCodecs.homogeneousList(Registries.BLOCK).optionalFieldOf("immune_blocks").forGetter(ExplodeEffect::immuneBlocks),
                    Vec3.CODEC.optionalFieldOf("offset", Vec3.ZERO).forGetter(ExplodeEffect::offset),
                    LevelBasedValue.CODEC.fieldOf("radius").forGetter(ExplodeEffect::radius),
                    Codec.BOOL.optionalFieldOf("create_fire", Boolean.valueOf(false)).forGetter(ExplodeEffect::createFire),
                    Level.ExplosionInteraction.CODEC.fieldOf("block_interaction").forGetter(ExplodeEffect::blockInteraction),
                    ParticleTypes.CODEC.fieldOf("small_particle").forGetter(ExplodeEffect::smallParticle),
                    ParticleTypes.CODEC.fieldOf("large_particle").forGetter(ExplodeEffect::largeParticle),
                    SoundEvent.CODEC.fieldOf("sound").forGetter(ExplodeEffect::sound)
                )
                .apply(p_344933_, ExplodeEffect::new)
    );

    @Override
    public void apply(ServerLevel p_346186_, int p_345966_, EnchantedItemInUse p_346324_, Entity p_345305_, Vec3 p_344916_) {
        Vec3 vec3 = p_344916_.add(this.offset);
        p_346186_.explode(
            this.attributeToUser ? p_345305_ : null,
            this.getDamageSource(p_345305_, vec3),
            new SimpleExplosionDamageCalculator(
                this.blockInteraction != Level.ExplosionInteraction.NONE,
                this.damageType.isPresent(),
                this.knockbackMultiplier.map(p_345018_ -> p_345018_.calculate(p_345966_)),
                this.immuneBlocks
            ),
            vec3.x(),
            vec3.y(),
            vec3.z(),
            Math.max(this.radius.calculate(p_345966_), 0.0F),
            this.createFire,
            this.blockInteraction,
            this.smallParticle,
            this.largeParticle,
            this.sound
        );
    }

    @Nullable
    private DamageSource getDamageSource(Entity p_346246_, Vec3 p_345332_) {
        if (this.damageType.isEmpty()) {
            return null;
        } else {
            return this.attributeToUser ? new DamageSource(this.damageType.get(), p_346246_) : new DamageSource(this.damageType.get(), p_345332_);
        }
    }

    @Override
    public MapCodec<ExplodeEffect> codec() {
        return CODEC;
    }
}
