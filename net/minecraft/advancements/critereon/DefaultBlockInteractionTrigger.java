package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

public class DefaultBlockInteractionTrigger extends SimpleCriterionTrigger<DefaultBlockInteractionTrigger.TriggerInstance> {
    @Override
    public Codec<DefaultBlockInteractionTrigger.TriggerInstance> codec() {
        return DefaultBlockInteractionTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_320398_, BlockPos p_320593_) {
        ServerLevel serverlevel = p_320398_.serverLevel();
        BlockState blockstate = serverlevel.getBlockState(p_320593_);
        LootParams lootparams = new LootParams.Builder(serverlevel)
            .withParameter(LootContextParams.ORIGIN, p_320593_.getCenter())
            .withParameter(LootContextParams.THIS_ENTITY, p_320398_)
            .withParameter(LootContextParams.BLOCK_STATE, blockstate)
            .create(LootContextParamSets.BLOCK_USE);
        LootContext lootcontext = new LootContext.Builder(lootparams).create(Optional.empty());
        this.trigger(p_320398_, p_320795_ -> p_320795_.matches(lootcontext));
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, Optional<ContextAwarePredicate> location)
        implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<DefaultBlockInteractionTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337352_ -> p_337352_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::player),
                        ContextAwarePredicate.CODEC.optionalFieldOf("location").forGetter(DefaultBlockInteractionTrigger.TriggerInstance::location)
                    )
                    .apply(p_337352_, DefaultBlockInteractionTrigger.TriggerInstance::new)
        );

        public boolean matches(LootContext p_320120_) {
            return this.location.isEmpty() || this.location.get().matches(p_320120_);
        }

        @Override
        public void validate(CriterionValidator p_320246_) {
            SimpleCriterionTrigger.SimpleInstance.super.validate(p_320246_);
            this.location.ifPresent(p_319986_ -> p_320246_.validate(p_319986_, LootContextParamSets.BLOCK_USE, ".location"));
        }
    }
}
