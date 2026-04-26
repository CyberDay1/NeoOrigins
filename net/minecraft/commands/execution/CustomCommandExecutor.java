package net.minecraft.commands.execution;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;

public interface CustomCommandExecutor<T> {
    void run(T p_306241_, ContextChain<T> p_305832_, ChainModifiers p_309612_, ExecutionControl<T> p_306256_);

    public interface CommandAdapter<T> extends Command<T>, CustomCommandExecutor<T> {
        @Override
        default int run(CommandContext<T> p_306336_) throws CommandSyntaxException {
            throw new UnsupportedOperationException("This function should not run");
        }
    }

    public abstract static class WithErrorHandling<T extends ExecutionCommandSource<T>> implements CustomCommandExecutor<T> {
        public final void run(T p_306339_, ContextChain<T> p_306289_, ChainModifiers p_309578_, ExecutionControl<T> p_306027_) {
            try {
                this.runGuarded(p_306339_, p_306289_, p_309578_, p_306027_);
            } catch (CommandSyntaxException commandsyntaxexception) {
                this.onError(commandsyntaxexception, p_306339_, p_309578_, p_306027_.tracer());
                p_306339_.callback().onFailure();
            }
        }

        protected void onError(CommandSyntaxException p_306165_, T p_306155_, ChainModifiers p_309597_, @Nullable TraceCallbacks p_307254_) {
            p_306155_.handleError(p_306165_, p_309597_.isForked(), p_307254_);
        }

        protected abstract void runGuarded(T p_306117_, ContextChain<T> p_306121_, ChainModifiers p_309631_, ExecutionControl<T> p_305981_) throws CommandSyntaxException;
    }
}
