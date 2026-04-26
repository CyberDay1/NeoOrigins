package net.minecraft.commands.execution;

import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Collection;
import java.util.List;

public interface CustomModifierExecutor<T> {
    void apply(T p_309576_, List<T> p_305872_, ContextChain<T> p_306034_, ChainModifiers p_309561_, ExecutionControl<T> p_305771_);

    public interface ModifierAdapter<T> extends RedirectModifier<T>, CustomModifierExecutor<T> {
        @Override
        default Collection<T> apply(CommandContext<T> p_306058_) throws CommandSyntaxException {
            throw new UnsupportedOperationException("This function should not run");
        }
    }
}
