package net.minecraft.world.level.storage.loot.entries;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.ValidationContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

public abstract class CompositeEntryBase extends LootPoolEntryContainer {
    protected final List<LootPoolEntryContainer> children;
    private final ComposableEntryContainer composedChildren;

    protected CompositeEntryBase(List<LootPoolEntryContainer> p_299081_, List<LootItemCondition> p_298200_) {
        super(p_298200_);
        this.children = p_299081_;
        this.composedChildren = this.compose(p_299081_);
    }

    @Override
    public void validate(ValidationContext p_79434_) {
        super.validate(p_79434_);
        if (this.children.isEmpty()) {
            p_79434_.reportProblem("Empty children list");
        }

        for (int i = 0; i < this.children.size(); i++) {
            this.children.get(i).validate(p_79434_.forChild(".entry[" + i + "]"));
        }
    }

    protected abstract ComposableEntryContainer compose(List<? extends ComposableEntryContainer> p_298396_);

    @Override
    public final boolean expand(LootContext p_79439_, Consumer<LootPoolEntry> p_79440_) {
        return !this.canRun(p_79439_) ? false : this.composedChildren.expand(p_79439_, p_79440_);
    }

    public static <T extends CompositeEntryBase> MapCodec<T> createCodec(CompositeEntryBase.CompositeEntryConstructor<T> p_299248_) {
        return RecordCodecBuilder.mapCodec(
            p_338125_ -> p_338125_.group(LootPoolEntries.CODEC.listOf().optionalFieldOf("children", List.of()).forGetter(p_299120_ -> p_299120_.children))
                    .and(commonFields(p_338125_).t1())
                    .apply(p_338125_, p_299248_::create)
        );
    }

    @FunctionalInterface
    public interface CompositeEntryConstructor<T extends CompositeEntryBase> {
        T create(List<LootPoolEntryContainer> p_299202_, List<LootItemCondition> p_298937_);
    }
}
