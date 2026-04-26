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

public class KilledTrigger extends SimpleCriterionTrigger<KilledTrigger.TriggerInstance> {
    @Override
    public Codec<KilledTrigger.TriggerInstance> codec() {
        return KilledTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_48105_, Entity p_48106_, DamageSource p_48107_) {
        LootContext lootcontext = EntityPredicate.createContext(p_48105_, p_48106_);
        this.trigger(p_48105_, p_48112_ -> p_48112_.matches(p_48105_, lootcontext, p_48107_));
    }

    public static record TriggerInstance(
        Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> entityPredicate, Optional<DamageSourcePredicate> killingBlow
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<KilledTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337374_ -> p_337374_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(KilledTrigger.TriggerInstance::player),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(KilledTrigger.TriggerInstance::entityPredicate),
                        DamageSourcePredicate.CODEC.optionalFieldOf("killing_blow").forGetter(KilledTrigger.TriggerInstance::killingBlow)
                    )
                    .apply(p_337374_, KilledTrigger.TriggerInstance::new)
        );

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(Optional<EntityPredicate> p_298999_) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_298999_), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(EntityPredicate.Builder p_48137_) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_48137_)), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity() {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(
            Optional<EntityPredicate> p_300920_, Optional<DamageSourcePredicate> p_300985_
        ) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_300920_), p_300985_));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(EntityPredicate.Builder p_300879_, Optional<DamageSourcePredicate> p_298956_) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_300879_)), p_298956_));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(Optional<EntityPredicate> p_299297_, DamageSourcePredicate.Builder p_301016_) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_299297_), Optional.of(p_301016_.build())));
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntity(EntityPredicate.Builder p_152106_, DamageSourcePredicate.Builder p_301319_) {
            return CriteriaTriggers.PLAYER_KILLED_ENTITY
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_152106_)), Optional.of(p_301319_.build()))
                );
        }

        public static Criterion<KilledTrigger.TriggerInstance> playerKilledEntityNearSculkCatalyst() {
            return CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(Optional<EntityPredicate> p_298209_) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_298209_), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(EntityPredicate.Builder p_298389_) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_298389_)), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer() {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(
            Optional<EntityPredicate> p_298878_, Optional<DamageSourcePredicate> p_300913_
        ) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_298878_), p_300913_));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(EntityPredicate.Builder p_152119_, Optional<DamageSourcePredicate> p_301181_) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_152119_)), p_301181_));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(Optional<EntityPredicate> p_301135_, DamageSourcePredicate.Builder p_301143_) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(new KilledTrigger.TriggerInstance(Optional.empty(), EntityPredicate.wrap(p_301135_), Optional.of(p_301143_.build())));
        }

        public static Criterion<KilledTrigger.TriggerInstance> entityKilledPlayer(EntityPredicate.Builder p_301108_, DamageSourcePredicate.Builder p_298860_) {
            return CriteriaTriggers.ENTITY_KILLED_PLAYER
                .createCriterion(
                    new KilledTrigger.TriggerInstance(Optional.empty(), Optional.of(EntityPredicate.wrap(p_301108_)), Optional.of(p_298860_.build()))
                );
        }

        public boolean matches(ServerPlayer p_48131_, LootContext p_48132_, DamageSource p_48133_) {
            return this.killingBlow.isPresent() && !this.killingBlow.get().matches(p_48131_, p_48133_)
                ? false
                : this.entityPredicate.isEmpty() || this.entityPredicate.get().matches(p_48132_);
        }

        @Override
        public void validate(CriterionValidator p_311935_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_311935_);
            p_311935_.validateEntity(this.entityPredicate, ".entity");
        }
    }
}
