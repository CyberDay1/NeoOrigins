package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;

public class PlayerHurtEntityTrigger extends SimpleCriterionTrigger<PlayerHurtEntityTrigger.TriggerInstance> {
    @Override
    public Codec<PlayerHurtEntityTrigger.TriggerInstance> codec() {
        return PlayerHurtEntityTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_60113_, Entity p_60114_, DamageSource p_60115_, float p_60116_, float p_60117_, boolean p_60118_) {
        LootContext lootcontext = EntityPredicate.createContext(p_60113_, p_60114_);
        this.trigger(p_60113_, p_60126_ -> p_60126_.matches(p_60113_, lootcontext, p_60115_, p_60116_, p_60117_, p_60118_));
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<DamagePredicate> damage, Optional<ContextAwarePredicate> entity)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<PlayerHurtEntityTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337386_ -> p_337386_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(PlayerHurtEntityTrigger.TriggerInstance::player),
                        DamagePredicate.CODEC.optionalFieldOf("damage").forGetter(PlayerHurtEntityTrigger.TriggerInstance::damage),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(PlayerHurtEntityTrigger.TriggerInstance::entity)
                    )
                    .apply(p_337386_, PlayerHurtEntityTrigger.TriggerInstance::new)
        );

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity() {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntityWithDamage(Optional<DamagePredicate> p_300880_) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), p_300880_, Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntityWithDamage(DamagePredicate.Builder p_300924_) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.of(p_300924_.build()), Optional.empty()));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(Optional<EntityPredicate> p_298252_) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.empty(), EntityPredicate.wrap(p_298252_)));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(
            Optional<DamagePredicate> p_301287_, Optional<EntityPredicate> p_300951_
        ) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), p_301287_, EntityPredicate.wrap(p_300951_)));
        }

        public static Criterion<PlayerHurtEntityTrigger.TriggerInstance> playerHurtEntity(
            DamagePredicate.Builder p_301281_, Optional<EntityPredicate> p_298362_
        ) {
            return CriteriaTriggers.PLAYER_HURT_ENTITY
                .createCriterion(new PlayerHurtEntityTrigger.TriggerInstance(Optional.empty(), Optional.of(p_301281_.build()), EntityPredicate.wrap(p_298362_)));
        }

        public boolean matches(ServerPlayer p_60143_, LootContext p_60144_, DamageSource p_60145_, float p_60146_, float p_60147_, boolean p_60148_) {
            return this.damage.isPresent() && !this.damage.get().matches(p_60143_, p_60145_, p_60146_, p_60147_, p_60148_)
                ? false
                : !this.entity.isPresent() || this.entity.get().matches(p_60144_);
        }

        @Override
        public void validate(CriterionValidator p_312140_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_312140_);
            p_312140_.validateEntity(this.entity, ".entity");
        }
    }
}
