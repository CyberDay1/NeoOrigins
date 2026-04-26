package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Collection;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class FishingRodHookedTrigger extends SimpleCriterionTrigger<FishingRodHookedTrigger.TriggerInstance> {
    @Override
    public Codec<FishingRodHookedTrigger.TriggerInstance> codec() {
        return FishingRodHookedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_40417_, ItemStack p_40418_, FishingHook p_40419_, Collection<ItemStack> p_40420_) {
        LootContext lootcontext = EntityPredicate.createContext(p_40417_, (Entity)(p_40419_.getHookedIn() != null ? p_40419_.getHookedIn() : p_40419_));
        this.trigger(p_40417_, p_40425_ -> p_40425_.matches(p_40418_, lootcontext, p_40420_));
    }

    public static record TriggerInstance(
        Optional<ContextAwarePredicate> player, Optional<ItemPredicate> rod, Optional<ContextAwarePredicate> entity, Optional<ItemPredicate> item
    ) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<FishingRodHookedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337365_ -> p_337365_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(FishingRodHookedTrigger.TriggerInstance::player),
                        ItemPredicate.CODEC.optionalFieldOf("rod").forGetter(FishingRodHookedTrigger.TriggerInstance::rod),
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("entity").forGetter(FishingRodHookedTrigger.TriggerInstance::entity),
                        ItemPredicate.CODEC.optionalFieldOf("item").forGetter(FishingRodHookedTrigger.TriggerInstance::item)
                    )
                    .apply(p_337365_, FishingRodHookedTrigger.TriggerInstance::new)
        );

        public static Criterion<FishingRodHookedTrigger.TriggerInstance> fishedItem(
            Optional<ItemPredicate> p_298233_, Optional<EntityPredicate> p_298660_, Optional<ItemPredicate> p_298847_
        ) {
            return CriteriaTriggers.FISHING_ROD_HOOKED
                .createCriterion(new FishingRodHookedTrigger.TriggerInstance(Optional.empty(), p_298233_, EntityPredicate.wrap(p_298660_), p_298847_));
        }

        public boolean matches(ItemStack p_40444_, LootContext p_40445_, Collection<ItemStack> p_40446_) {
            if (this.rod.isPresent() && !this.rod.get().test(p_40444_)) {
                return false;
            } else if (this.entity.isPresent() && !this.entity.get().matches(p_40445_)) {
                return false;
            } else {
                if (this.item.isPresent()) {
                    boolean flag = false;
                    Entity entity = p_40445_.getParamOrNull(LootContextParams.THIS_ENTITY);
                    if (entity instanceof ItemEntity itementity && this.item.get().test(itementity.getItem())) {
                        flag = true;
                    }

                    for (ItemStack itemstack : p_40446_) {
                        if (this.item.get().test(itemstack)) {
                            flag = true;
                            break;
                        }
                    }

                    if (!flag) {
                        return false;
                    }
                }

                return true;
            }
        }

        @Override
        public void validate(CriterionValidator p_312572_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_312572_);
            p_312572_.validateEntity(this.entity, ".entity");
        }
    }
}
