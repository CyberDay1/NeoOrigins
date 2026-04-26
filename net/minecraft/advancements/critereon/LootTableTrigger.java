package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;

public class LootTableTrigger extends SimpleCriterionTrigger<LootTableTrigger.TriggerInstance> {
    @Override
    public Codec<LootTableTrigger.TriggerInstance> codec() {
        return LootTableTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_54598_, ResourceKey<LootTable> p_335591_) {
        this.trigger(p_54598_, p_335160_ -> p_335160_.matches(p_335591_));
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceKey<LootTable> lootTable)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<LootTableTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337380_ -> p_337380_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(LootTableTrigger.TriggerInstance::player),
                        ResourceKey.codec(Registries.LOOT_TABLE).fieldOf("loot_table").forGetter(LootTableTrigger.TriggerInstance::lootTable)
                    )
                    .apply(p_337380_, LootTableTrigger.TriggerInstance::new)
        );

        public static Criterion<LootTableTrigger.TriggerInstance> lootTableUsed(ResourceKey<LootTable> p_335395_) {
            return CriteriaTriggers.GENERATE_LOOT.createCriterion(new LootTableTrigger.TriggerInstance(Optional.empty(), p_335395_));
        }

        public boolean matches(ResourceKey<LootTable> p_335509_) {
            return this.lootTable == p_335509_;
        }
    }
}
