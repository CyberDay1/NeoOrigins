package net.minecraft.advancements.critereon;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

public class CriterionValidator {
    private final ProblemReporter reporter;
    private final HolderGetter.Provider lootData;

    public CriterionValidator(ProblemReporter p_311918_, HolderGetter.Provider p_335486_) {
        this.reporter = p_311918_;
        this.lootData = p_335486_;
    }

    public void validateEntity(Optional<ContextAwarePredicate> p_312159_, String p_312438_) {
        p_312159_.ifPresent(p_311858_ -> this.validateEntity(p_311858_, p_312438_));
    }

    public void validateEntities(List<ContextAwarePredicate> p_311847_, String p_312485_) {
        this.validate(p_311847_, LootContextParamSets.ADVANCEMENT_ENTITY, p_312485_);
    }

    public void validateEntity(ContextAwarePredicate p_311775_, String p_311914_) {
        this.validate(p_311775_, LootContextParamSets.ADVANCEMENT_ENTITY, p_311914_);
    }

    public void validate(ContextAwarePredicate p_312318_, LootContextParamSet p_312478_, String p_312401_) {
        p_312318_.validate(new ValidationContext(this.reporter.forChild(p_312401_), p_312478_, this.lootData));
    }

    public void validate(List<ContextAwarePredicate> p_312108_, LootContextParamSet p_312752_, String p_312670_) {
        for (int i = 0; i < p_312108_.size(); i++) {
            ContextAwarePredicate contextawarepredicate = p_312108_.get(i);
            contextawarepredicate.validate(new ValidationContext(this.reporter.forChild(p_312670_ + "[" + i + "]"), p_312752_, this.lootData));
        }
    }
}
