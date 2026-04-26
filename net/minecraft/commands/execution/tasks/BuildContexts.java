package net.minecraft.commands.execution.tasks;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.brigadier.RedirectModifier;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.brigadier.context.ContextChain.Stage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.ChainModifiers;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.CustomCommandExecutor;
import net.minecraft.commands.execution.CustomModifierExecutor;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.ExecutionControl;
import net.minecraft.commands.execution.Frame;
import net.minecraft.commands.execution.TraceCallbacks;
import net.minecraft.commands.execution.UnboundEntryAction;
import net.minecraft.network.chat.Component;

public class BuildContexts<T extends ExecutionCommandSource<T>> {
    @VisibleForTesting
    public static final DynamicCommandExceptionType ERROR_FORK_LIMIT_REACHED = new DynamicCommandExceptionType(
        p_306063_ -> Component.translatableEscape("command.forkLimit", p_306063_)
    );
    private final String commandInput;
    private final ContextChain<T> command;

    public BuildContexts(String p_306157_, ContextChain<T> p_305974_) {
        this.commandInput = p_306157_;
        this.command = p_305974_;
    }

    protected void execute(T p_309567_, List<T> p_306303_, ExecutionContext<T> p_305977_, Frame p_309603_, ChainModifiers p_309659_) {
        ContextChain<T> contextchain = this.command;
        ChainModifiers chainmodifiers = p_309659_;
        List<T> list = p_306303_;
        if (contextchain.getStage() != Stage.EXECUTE) {
            p_305977_.profiler().push(() -> "prepare " + this.commandInput);

            try {
                for (int i = p_305977_.forkLimit(); contextchain.getStage() != Stage.EXECUTE; contextchain = contextchain.nextStage()) {
                    CommandContext<T> commandcontext = contextchain.getTopContext();
                    if (commandcontext.isForked()) {
                        chainmodifiers = chainmodifiers.setForked();
                    }

                    RedirectModifier<T> redirectmodifier = commandcontext.getRedirectModifier();
                    if (redirectmodifier instanceof CustomModifierExecutor<?>) {
                        CustomModifierExecutor<T> custommodifierexecutor = (CustomModifierExecutor<T>) redirectmodifier;
                        custommodifierexecutor.apply(p_309567_, list, contextchain, chainmodifiers, ExecutionControl.create(p_305977_, p_309603_));
                        return;
                    }

                    if (redirectmodifier != null) {
                        p_305977_.incrementCost();
                        boolean flag = chainmodifiers.isForked();
                        List<T> list1 = new ObjectArrayList<>();

                        for (T t : list) {
                            try {
                                Collection<T> collection = ContextChain.runModifier(commandcontext, t, (p_309424_, p_309425_, p_309426_) -> {
                                }, flag);
                                if (list1.size() + collection.size() >= i) {
                                    p_309567_.handleError(ERROR_FORK_LIMIT_REACHED.create(i), flag, p_305977_.tracer());
                                    return;
                                }

                                list1.addAll(collection);
                            } catch (CommandSyntaxException commandsyntaxexception) {
                                t.handleError(commandsyntaxexception, flag, p_305977_.tracer());
                                if (!flag) {
                                    return;
                                }
                            }
                        }

                        list = list1;
                    }
                }
            } finally {
                p_305977_.profiler().pop();
            }
        }

        if (list.isEmpty()) {
            if (chainmodifiers.isReturn()) {
                p_305977_.queueNext(new CommandQueueEntry<T>(p_309603_, FallthroughTask.instance()));
            }
        } else {
            CommandContext<T> commandcontext1 = contextchain.getTopContext();
            com.mojang.brigadier.Command<T> command = commandcontext1.getCommand();
            if (command instanceof CustomCommandExecutor<?>) {
                CustomCommandExecutor<T> customcommandexecutor = (CustomCommandExecutor<T>) command;
                ExecutionControl<T> executioncontrol = ExecutionControl.create(p_305977_, p_309603_);

                for (T t2 : list) {
                    customcommandexecutor.run(t2, contextchain, chainmodifiers, executioncontrol);
                }
            } else {
                if (chainmodifiers.isReturn()) {
                    T t1 = list.get(0);
                    t1 = t1.withCallback(CommandResultCallback.chain(t1.callback(), p_309603_.returnValueConsumer()));
                    list = List.of(t1);
                }

                ExecuteCommand<T> executecommand = new ExecuteCommand<>(this.commandInput, chainmodifiers, commandcontext1);
                ContinuationTask.schedule(
                    p_305977_, p_309603_, list, (p_309428_, p_309429_) -> new CommandQueueEntry<>(p_309428_, executecommand.bind(p_309429_))
                );
            }
        }
    }

    protected void traceCommandStart(ExecutionContext<T> p_306237_, Frame p_309625_) {
        TraceCallbacks tracecallbacks = p_306237_.tracer();
        if (tracecallbacks != null) {
            tracecallbacks.onCommand(p_309625_.depth(), this.commandInput);
        }
    }

    @Override
    public String toString() {
        return this.commandInput;
    }

    public static class Continuation<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final ChainModifiers modifiers;
        private final T originalSource;
        private final List<T> sources;

        public Continuation(String p_306217_, ContextChain<T> p_305988_, ChainModifiers p_309702_, T p_309701_, List<T> p_306017_) {
            super(p_306217_, p_305988_);
            this.originalSource = p_309701_;
            this.sources = p_306017_;
            this.modifiers = p_309702_;
        }

        @Override
        public void execute(ExecutionContext<T> p_305882_, Frame p_309577_) {
            this.execute(this.originalSource, this.sources, p_305882_, p_309577_, this.modifiers);
        }
    }

    public static class TopLevel<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements EntryAction<T> {
        private final T source;

        public TopLevel(String p_306314_, ContextChain<T> p_305844_, T p_306134_) {
            super(p_306314_, p_305844_);
            this.source = p_306134_;
        }

        @Override
        public void execute(ExecutionContext<T> p_306114_, Frame p_309583_) {
            this.traceCommandStart(p_306114_, p_309583_);
            this.execute(this.source, List.of(this.source), p_306114_, p_309583_, ChainModifiers.DEFAULT);
        }
    }

    public static class Unbound<T extends ExecutionCommandSource<T>> extends BuildContexts<T> implements UnboundEntryAction<T> {
        public Unbound(String p_305863_, ContextChain<T> p_305842_) {
            super(p_305863_, p_305842_);
        }

        public void execute(T p_306259_, ExecutionContext<T> p_305944_, Frame p_309714_) {
            this.traceCommandStart(p_305944_, p_309714_);
            this.execute(p_306259_, List.of(p_306259_), p_305944_, p_309714_, ChainModifiers.DEFAULT);
        }
    }
}
