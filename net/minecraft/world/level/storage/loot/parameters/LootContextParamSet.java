package net.minecraft.world.level.storage.loot.parameters;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Set;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.LootContextUser;
import net.minecraft.world.level.storage.loot.ValidationContext;

public class LootContextParamSet {
    private final Set<LootContextParam<?>> required;
    private final Set<LootContextParam<?>> all;

    LootContextParamSet(Set<LootContextParam<?>> p_81388_, Set<LootContextParam<?>> p_81389_) {
        this.required = ImmutableSet.copyOf(p_81388_);
        this.all = ImmutableSet.copyOf(Sets.union(p_81388_, p_81389_));
    }

    public boolean isAllowed(LootContextParam<?> p_165476_) {
        return this.all.contains(p_165476_);
    }

    public Set<LootContextParam<?>> getRequired() {
        return this.required;
    }

    public Set<LootContextParam<?>> getAllowed() {
        return this.all;
    }

    @Override
    public String toString() {
        return "["
            + Joiner.on(", ").join(this.all.stream().map(p_339580_ -> (this.required.contains(p_339580_) ? "!" : "") + p_339580_.getName()).iterator())
            + "]";
    }

    public void validateUser(ValidationContext p_81396_, LootContextUser p_81397_) {
        this.validateUser(p_81396_.reporter(), p_81397_);
    }

    public void validateUser(ProblemReporter p_345868_, LootContextUser p_346074_) {
        Set<LootContextParam<?>> set = p_346074_.getReferencedContextParams();
        Set<LootContextParam<?>> set1 = Sets.difference(set, this.all);
        if (!set1.isEmpty()) {
            p_345868_.report("Parameters " + set1 + " are not provided in this context");
        }
    }

    public static LootContextParamSet.Builder builder() {
        return new LootContextParamSet.Builder();
    }

    public static class Builder {
        private final Set<LootContextParam<?>> required = Sets.newIdentityHashSet();
        private final Set<LootContextParam<?>> optional = Sets.newIdentityHashSet();

        public LootContextParamSet.Builder required(LootContextParam<?> p_81407_) {
            if (this.optional.contains(p_81407_)) {
                throw new IllegalArgumentException("Parameter " + p_81407_.getName() + " is already optional");
            } else {
                this.required.add(p_81407_);
                return this;
            }
        }

        public LootContextParamSet.Builder optional(LootContextParam<?> p_81409_) {
            if (this.required.contains(p_81409_)) {
                throw new IllegalArgumentException("Parameter " + p_81409_.getName() + " is already required");
            } else {
                this.optional.add(p_81409_);
                return this;
            }
        }

        public LootContextParamSet build() {
            return new LootContextParamSet(this.required, this.optional);
        }
    }
}
