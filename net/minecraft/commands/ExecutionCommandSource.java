package net.minecraft.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.ResultConsumer;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandExceptionType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import javax.annotation.Nullable;
import net.minecraft.commands.execution.TraceCallbacks;

public interface ExecutionCommandSource<T extends ExecutionCommandSource<T>> {
    boolean hasPermission(int p_306071_);

    T withCallback(CommandResultCallback p_309572_);

    CommandResultCallback callback();

    default T clearCallbacks() {
        return this.withCallback(CommandResultCallback.EMPTY);
    }

    CommandDispatcher<T> dispatcher();

    void handleError(CommandExceptionType p_307509_, Message p_307413_, boolean p_307299_, @Nullable TraceCallbacks p_307535_);

    boolean isSilent();

    default void handleError(CommandSyntaxException p_307419_, boolean p_307222_, @Nullable TraceCallbacks p_307604_) {
        this.handleError(p_307419_.getType(), p_307419_.getRawMessage(), p_307222_, p_307604_);
    }

    static <T extends ExecutionCommandSource<T>> ResultConsumer<T> resultConsumer() {
        return (p_309418_, p_309419_, p_309420_) -> p_309418_.getSource().callback().onResult(p_309419_, p_309420_);
    }
}
