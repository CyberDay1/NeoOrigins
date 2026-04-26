package net.minecraft.commands.execution.tasks;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;

public class ExecuteCommand<T extends ExecutionCommandSource<T>> implements UnboundEntryAction<T> {
    private final String commandInput;
    private final ChainModifiers modifiers;
    private final CommandContext<T> executionContext;

    public ExecuteCommand(String p_306273_, ChainModifiers p_309556_, CommandContext<T> p_305919_) {
        this.commandInput = p_306273_;
        this.modifiers = p_309556_;
        this.executionContext = p_305919_;
    }

    public void execute(T p_305870_, ExecutionContext<T> p_306198_, Frame p_309548_) {
        p_306198_.profiler().push(() -> "execute " + this.commandInput);

        try {
            p_306198_.incrementCost();
            int i = ContextChain.runExecutable(this.executionContext, p_305870_, ExecutionCommandSource.resultConsumer(), this.modifiers.isForked());
            TraceCallbacks tracecallbacks = p_306198_.tracer();
            if (tracecallbacks != null) {
                tracecallbacks.onReturn(p_309548_.depth(), this.commandInput, i);
            }
        } catch (CommandSyntaxException commandsyntaxexception) {
            p_305870_.handleError(commandsyntaxexception, this.modifiers.isForked(), p_306198_.tracer());
        } finally {
            p_306198_.profiler().pop();
        }
    }
}
