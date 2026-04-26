package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.storage.loot.LootContext;

public class BredAnimalsTrigger extends SimpleCriterionTrigger<BredAnimalsTrigger.TriggerInstance> {
    @Override
    public Codec<BredAnimalsTrigger.TriggerInstance> codec() {
        return BredAnimalsTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_147279_, Animal p_147280_, Animal p_147281_, @Nullable AgeableMob p_147282_) {
        LootContext lootcontext = EntityPredicate.createContext(p_147279_, p_147280_);
        LootContext lootcontext1 = EntityPredicate.createContext(p_147279_, p_147281_);
        LootContext lootcontext2 = p_147282_ != null ? EntityPredicate.createContext(p_147279_, p_147282_) : null;
        this.trigger(p_147279_, p_18653_ -> p_18653_.matches(lootcontext, lootcontext1, lootcontext2));
    }

    public static record TriggerInstance(
        Optional<ContextAwarePredicate> player,
        Optional<ContextAwarePredicate> parent,
        Optional<ContextAwarePredicate> partner,
        Optional<ContextAwarePredicate> child
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<BredAnimalsTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337343_ -> p_337343_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(BredAnimalsTrigger.TriggerInstance::player),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("parent").forGetter(BredAnimalsTrigger.TriggerInstance::parent),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("partner").forGetter(BredAnimalsTrigger.TriggerInstance::partner),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("child").forGetter(BredAnimalsTrigger.TriggerInstance::child)
                    )
                    .apply(p_337343_, BredAnimalsTrigger.TriggerInstance::new)
        );

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals() {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(EntityPredicate.Builder p_301285_) {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(
                    new BredAnimalsTrigger.TriggerInstance(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(EntityPredicate.wrap(p_301285_)))
                );
        }

        public static Criterion<BredAnimalsTrigger.TriggerInstance> bredAnimals(
            Optional<EntityPredicate> p_301067_, Optional<EntityPredicate> p_301159_, Optional<EntityPredicate> p_301056_
        ) {
            return CriteriaTriggers.BRED_ANIMALS
                .createCriterion(
                    new BredAnimalsTrigger.TriggerInstance(
                        Optional.empty(), EntityPredicate.wrap(p_301067_), EntityPredicate.wrap(p_301159_), EntityPredicate.wrap(p_301056_)
                    )
                );
        }

        public boolean matches(LootContext p_18676_, LootContext p_18677_, @Nullable LootContext p_18678_) {
            return !this.child.isPresent() || p_18678_ != null && this.child.get().matches(p_18678_)
                ? matches(this.parent, p_18676_) && matches(this.partner, p_18677_) || matches(this.parent, p_18677_) && matches(this.partner, p_18676_)
                : false;
        }

        private static boolean matches(Optional<ContextAwarePredicate> p_298925_, LootContext p_298317_) {
            return p_298925_.isEmpty() || p_298925_.get().matches(p_298317_);
        }

        @Override
        public void validate(CriterionValidator p_312663_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_312663_);
            p_312663_.validateEntity(this.parent, ".parent");
            p_312663_.validateEntity(this.partner, ".partner");
            p_312663_.validateEntity(this.child, ".child");
        }
    }
}
