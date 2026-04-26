package net.minecraft.advancements.critereon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.Optional;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.Criterion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;

public class RecipeUnlockedTrigger extends SimpleCriterionTrigger<RecipeUnlockedTrigger.TriggerInstance> {
    @Override
    public Codec<RecipeUnlockedTrigger.TriggerInstance> codec() {
        return RecipeUnlockedTrigger.TriggerInstance.CODEC;
    }

    public void trigger(ServerPlayer p_63719_, RecipeHolder<?> p_301160_) {
        this.trigger(p_63719_, p_300666_ -> p_300666_.matches(p_301160_));
    }

    public static Criterion<RecipeUnlockedTrigger.TriggerInstance> unlocked(ResourceLocation p_63729_) {
        return CriteriaTriggers.RECIPE_UNLOCKED.createCriterion(new RecipeUnlockedTrigger.TriggerInstance(Optional.empty(), p_63729_));
    }

    public static record TriggerInstance(Optional<ContextAwarePredicate> player, ResourceLocation recipe) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<RecipeUnlockedTrigger.TriggerInstance> CODEC = RecordCodecBuilder.create(
            p_337392_ -> p_337392_.group(
                        EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(RecipeUnlockedTrigger.TriggerInstance::player),
                        ResourceLocation.CODEC.fieldOf("recipe").forGetter(RecipeUnlockedTrigger.TriggerInstance::recipe)
                    )
                    .apply(p_337392_, RecipeUnlockedTrigger.TriggerInstance::new)
        );

        public boolean matches(RecipeHolder<?> p_301295_) {
            return this.recipe.equals(p_301295_.id());
        }
    }
}
