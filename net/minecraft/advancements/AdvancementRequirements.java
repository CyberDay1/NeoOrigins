package net.minecraft.advancements;

import com.google.common.collect.Sets;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.network.FriendlyByteBuf;

public record AdvancementRequirements(List<List<String>> requirements) {
    public static final Codec<AdvancementRequirements> CODEC = Codec.STRING
        .listOf()
        .listOf()
        .xmap(AdvancementRequirements::new, AdvancementRequirements::requirements);
    public static final AdvancementRequirements EMPTY = new AdvancementRequirements(List.of());

    public AdvancementRequirements(FriendlyByteBuf p_301089_) {
        this(p_301089_.readList(p_319371_ -> p_319371_.readList(FriendlyByteBuf::readUtf)));
    }

    public void write(FriendlyByteBuf p_301190_) {
        p_301190_.writeCollection(this.requirements, (p_319372_, p_319373_) -> p_319372_.writeCollection(p_319373_, FriendlyByteBuf::writeUtf));
    }

    public static AdvancementRequirements allOf(Collection<String> p_301049_) {
        return new AdvancementRequirements(p_301049_.stream().map(List::of).toList());
    }

    public static AdvancementRequirements anyOf(Collection<String> p_301268_) {
        return new AdvancementRequirements(List.of(List.copyOf(p_301268_)));
    }

    public int size() {
        return this.requirements.size();
    }

    public boolean test(Predicate<String> p_301112_) {
        if (this.requirements.isEmpty()) {
            return false;
        } else {
            for (List<String> list : this.requirements) {
                if (!anyMatch(list, p_301112_)) {
                    return false;
                }
            }

            return true;
        }
    }

    public int count(Predicate<String> p_301229_) {
        int i = 0;

        for (List<String> list : this.requirements) {
            if (anyMatch(list, p_301229_)) {
                i++;
            }
        }

        return i;
    }

    private static boolean anyMatch(List<String> p_312163_, Predicate<String> p_301321_) {
        for (String s : p_312163_) {
            if (p_301321_.test(s)) {
                return true;
            }
        }

        return false;
    }

    public DataResult<AdvancementRequirements> validate(Set<String> p_312891_) {
        Set<String> set = new ObjectOpenHashSet<>();

        for (List<String> list : this.requirements) {
            if (list.isEmpty() && p_312891_.isEmpty()) {
                return DataResult.error(() -> "Requirement entry cannot be empty");
            }

            set.addAll(list);
        }

        if (!p_312891_.equals(set)) {
            Set<String> set1 = Sets.difference(p_312891_, set);
            Set<String> set2 = Sets.difference(set, p_312891_);
            return DataResult.error(
                () -> "Advancement completion requirements did not exactly match specified criteria. Missing: " + set1 + ". Unknown: " + set2
            );
        } else {
            return DataResult.success(this);
        }
    }

    public boolean isEmpty() {
        return this.requirements.isEmpty();
    }

    @Override
    public String toString() {
        return this.requirements.toString();
    }

    public Set<String> names() {
        Set<String> set = new ObjectOpenHashSet<>();

        for (List<String> list : this.requirements) {
            set.addAll(list);
        }

        return set;
    }

    public interface Strategy {
        AdvancementRequirements.Strategy AND = AdvancementRequirements::allOf;
        AdvancementRequirements.Strategy OR = AdvancementRequirements::anyOf;

        AdvancementRequirements create(Collection<String> p_301043_);
    }
}
