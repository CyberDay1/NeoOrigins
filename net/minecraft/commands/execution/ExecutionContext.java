package net.minecraft.commands.execution;

import com.google.common.collect.Queues;
import com.mojang.brigadier.context.ContextChain;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.ExecutionCommandSource;
import net.minecraft.commands.execution.tasks.BuildContexts;
import net.minecraft.commands.execution.tasks.CallFunction;
import net.minecraft.commands.functions.InstantiatedFunction;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

public class ExecutionContext<T> implements AutoCloseable {
    private static final int MAX_QUEUE_DEPTH = 10000000;
    private static final Logger LOGGER = LogUtils.getLogger();
    private final int commandLimit;
    private final int forkLimit;
    private final ProfilerFiller profiler;
    @Nullable
    private TraceCallbacks tracer;
    private int commandQuota;
    private boolean queueOverflow;
    private final Deque<CommandQueueEntry<T>> commandQueue = Queues.newArrayDeque();
    private final List<CommandQueueEntry<T>> newTopCommands = new ObjectArrayList<>();
    private int currentFrameDepth;

    public ExecutionContext(int p_306012_, int p_306180_, ProfilerFiller p_306142_) {
        this.commandLimit = p_306012_;
        this.forkLimit = p_306180_;
        this.profiler = p_306142_;
        this.commandQuota = p_306012_;
    }

    private static <T extends ExecutionCommandSource<T>> Frame createTopFrame(ExecutionContext<T> p_309594_, CommandResultCallback p_309689_) {
        if (p_309594_.currentFrameDepth == 0) {
            return new Frame(0, p_309689_, p_309594_.commandQueue::clear);
        } else {
            int i = p_309594_.currentFrameDepth + 1;
            return new Frame(i, p_309689_, p_309594_.frameControlForDepth(i));
        }
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialFunctionCall(
        ExecutionContext<T> p_309686_, InstantiatedFunction<T> p_306145_, T p_309607_, CommandResultCallback p_309681_
    ) {
        p_309686_.queueNext(
            new CommandQueueEntry<>(createTopFrame(p_309686_, p_309681_), new CallFunction<>(p_306145_, p_309607_.callback(), false).bind(p_309607_))
        );
    }

    public static <T extends ExecutionCommandSource<T>> void queueInitialCommandExecution(
        ExecutionContext<T> p_306236_, String p_305860_, ContextChain<T> p_305784_, T p_306195_, CommandResultCallback p_309622_
    ) {
        p_306236_.queueNext(new CommandQueueEntry<>(createTopFrame(p_306236_, p_309622_), new BuildContexts.TopLevel<>(p_305860_, p_305784_, p_306195_)));
    }

    private void handleQueueOverflow() {
        this.queueOverflow = true;
        this.newTopCommands.clear();
        this.commandQueue.clear();
    }

    public void queueNext(CommandQueueEntry<T> p_305869_) {
        if (this.newTopCommands.size() + this.commandQueue.size() > 10000000) {
            this.handleQueueOverflow();
        }

        if (!this.queueOverflow) {
            this.newTopCommands.add(p_305869_);
        }
    }

    public void discardAtDepthOrHigher(int p_306290_) {
        while (!this.commandQueue.isEmpty() && this.commandQueue.peek().frame().depth() >= p_306290_) {
            this.commandQueue.removeFirst();
        }
    }

    public Frame.FrameControl frameControlForDepth(int p_309645_) {
        return () -> this.discardAtDepthOrHigher(p_309645_);
    }

    public void runCommandQueue() {
        this.pushNewCommands();

        while (true) {
            if (this.commandQuota <= 0) {
                LOGGER.info("Command execution stopped due to limit (executed {} commands)", this.commandLimit);
                break;
            }

            CommandQueueEntry<T> commandqueueentry = this.commandQueue.pollFirst();
            if (commandqueueentry == null) {
                return;
            }

            this.currentFrameDepth = commandqueueentry.frame().depth();
            commandqueueentry.execute(this);
            if (this.queueOverflow) {
                LOGGER.error("Command execution stopped due to command queue overflow (max {})", 10000000);
                break;
            }

            this.pushNewCommands();
        }

        this.currentFrameDepth = 0;
    }

    private void pushNewCommands() {
        for (int i = this.newTopCommands.size() - 1; i >= 0; i--) {
            this.commandQueue.addFirst(this.newTopCommands.get(i));
        }

        this.newTopCommands.clear();
    }

    public void tracer(@Nullable TraceCallbacks p_305950_) {
        this.tracer = p_305950_;
    }

    @Nullable
    public TraceCallbacks tracer() {
        return this.tracer;
    }

    public ProfilerFiller profiler() {
        return this.profiler;
    }

    public int forkLimit() {
        return this.forkLimit;
    }

    public void incrementCost() {
        this.commandQuota--;
    }

    @Override
    public void close() {
        if (this.tracer != null) {
            this.tracer.close();
        }
    }
}
