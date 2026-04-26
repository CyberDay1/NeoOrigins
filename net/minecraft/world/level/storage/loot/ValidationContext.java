package net.minecraft.world.level.storage.loot;

import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.HolderGetter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSet;

public class ValidationContext {
    private final ProblemReporter reporter;
    private final LootContextParamSet params;
    private final Optional<HolderGetter.Provider> resolver;
    private final Set<ResourceKey<?>> visitedElements;

    public ValidationContext(ProblemReporter p_311875_, LootContextParamSet p_279485_, HolderGetter.Provider p_335890_) {
        this(p_311875_, p_279485_, Optional.of(p_335890_), Set.of());
    }

    public ValidationContext(ProblemReporter p_352229_, LootContextParamSet p_352267_) {
        this(p_352229_, p_352267_, Optional.empty(), Set.of());
    }

    private ValidationContext(
        ProblemReporter p_312319_, LootContextParamSet p_279447_, Optional<HolderGetter.Provider> p_352342_, Set<ResourceKey<?>> p_311760_
    ) {
        this.reporter = p_312319_;
        this.params = p_279447_;
        this.resolver = p_352342_;
        this.visitedElements = p_311760_;
    }

    public ValidationContext forChild(String p_79366_) {
        return new ValidationContext(this.reporter.forChild(p_79366_), this.params, this.resolver, this.visitedElements);
    }

    public ValidationContext enterElement(String p_279180_, ResourceKey<?> p_335771_) {
        Set<ResourceKey<?>> set = ImmutableSet.<ResourceKey<?>>builder().addAll(this.visitedElements).add(p_335771_).build();
        return new ValidationContext(this.reporter.forChild(p_279180_), this.params, this.resolver, set);
    }

    public boolean hasVisitedElement(ResourceKey<?> p_335626_) {
        return this.visitedElements.contains(p_335626_);
    }

    public void reportProblem(String p_79358_) {
        this.reporter.report(p_79358_);
    }

    public void validateUser(LootContextUser p_79354_) {
        this.params.validateUser(this, p_79354_);
    }

    public HolderGetter.Provider resolver() {
        return this.resolver.orElseThrow(() -> new UnsupportedOperationException("References not allowed"));
    }

    public boolean allowsReferences() {
        return this.resolver.isPresent();
    }

    public ValidationContext setParams(LootContextParamSet p_79356_) {
        return new ValidationContext(this.reporter, p_79356_, this.resolver, this.visitedElements);
    }

    public ProblemReporter reporter() {
        return this.reporter;
    }
}
