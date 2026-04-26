package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.phys.Vec3;

public class FallAfterExplosionTrigger extends SimpleCriterionTrigger<FallAfterExplosionTrigger.TriggerInstance> {
    @Override
    public Codec<FallAfterExplosionTrigger.TriggerInstance> codec() {
        return FallAfterExplosionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_335648_, Vec3 p_336086_, @Nullable Entity p_336120_) {
        Vec3 vec3 = p_335648_.position();
        LootContext lootcontext = p_336120_ != null ? EntityPredicate.createContext(p_335648_, p_336120_) : null;
        this.trigger(p_335648_, p_335739_ -> p_335739_.matches(p_335648_.serverLevel(), p_336086_, vec3, lootcontext));
    }

    public static record TriggerInstance(
        Optional<ContextAwarePredicate> player,
        Optional<LocationPredicate> startPosition,
        Optional<DistancePredicate> distance,
        Optional<ContextAwarePredicate> cause
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<FallAfterExplosionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337362_ -> p_337362_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FallAfterExplosionTrigger.TriggerInstance::player),
                        LocationPredicate.CODEC.optionalFieldOf("start_position").forGetter(FallAfterExplosionTrigger.TriggerInstance::startPosition),
                        DistancePredicate.CODEC.optionalFieldOf("distance").forGetter(FallAfterExplosionTrigger.TriggerInstance::distance),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("cause").forGetter(FallAfterExplosionTrigger.TriggerInstance::cause)
                    )
                    .apply(p_337362_, FallAfterExplosionTrigger.TriggerInstance::new)
        );

        public static Criterion<FallAfterExplosionTrigger.TriggerInstance> fallAfterExplosion(DistancePredicate p_335666_, EntityPredicate.Builder p_335823_) {
            return CriteriaTriggers.FALL_AFTER_EXPLOSION
                .createCriterion(
                    new FallAfterExplosionTrigger.TriggerInstance(
                        Optional.empty(), Optional.empty(), Optional.of(p_335666_), Optional.of(EntityPredicate.wrap(p_335823_))
                    )
                );
        }

        @Override
        public void validate(CriterionValidator p_335649_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_335649_);
            p_335649_.validateEntity(this.cause(), ".cause");
        }

        public boolean matches(ServerLevel p_335990_, Vec3 p_335477_, Vec3 p_336025_, @Nullable LootContext p_335849_) {
            if (this.startPosition.isPresent() && !this.startPosition.get().matches(p_335990_, p_335477_.x, p_335477_.y, p_335477_.z)) {
                return false;
            } else {
                return this.distance.isPresent() && !this.distance.get().matches(p_335477_.x, p_335477_.y, p_335477_.z, p_336025_.x, p_336025_.y, p_336025_.z)
                    ? false
                    : !this.cause.isPresent() || p_335849_ != null && this.cause.get().matches(p_335849_);
            }
        }
    }
}
