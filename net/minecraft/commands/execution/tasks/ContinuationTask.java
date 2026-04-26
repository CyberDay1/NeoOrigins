package net.minecraft.commands.execution.tasks;

import java.util.List;
import net.minecraft.commands.execution.CommandQueueEntry;
import net.minecraft.commands.execution.EntryAction;
import net.minecraft.commands.execution.ExecutionContext;
import net.minecraft.commands.execution.Frame;

public class ContinuationTask<T, P> implements EntryAction<T> {
    private final ContinuationTask.TaskProvider<T, P> taskFactory;
    private final List<P> arguments;
    private final CommandQueueEntry<T> selfEntry;
    private int index;

    private ContinuationTask(ContinuationTask.TaskProvider<T, P> p_306200_, List<P> p_306026_, Frame p_309650_) {
        this.taskFactory = p_306200_;
        this.arguments = p_306026_;
        this.selfEntry = new CommandQueueEntry<>(p_309650_, this);
    }

    @Override
    public void execute(ExecutionContext<T> p_306022_, Frame p_309626_) {
        P p = this.arguments.get(this.index);
        p_306022_.queueNext(this.taskFactory.create(p_309626_, p));
        if (++this.index < this.arguments.size()) {
            p_306022_.queueNext(this.selfEntry);
        }
    }

    public static <T, P> void schedule(ExecutionContext<T> p_306309_, Frame p_309712_, List<P> p_306081_, ContinuationTask.TaskProvider<T, P> p_305883_) {
        int i = p_306081_.size();
        switch (i) {
            case 0:
                break;
            case 1:
                p_306309_.queueNext(p_305883_.create(p_309712_, p_306081_.get(0)));
                break;
            case 2:
                p_306309_.queueNext(p_305883_.create(p_309712_, p_306081_.get(0)));
                p_306309_.queueNext(p_305883_.create(p_309712_, p_306081_.get(1)));
                break;
            default:
                p_306309_.queueNext((new ContinuationTask<>(p_305883_, p_306081_, p_309712_)).selfEntry);
        }
    }

    @FunctionalInterface
    public interface TaskProvider<T, P> {
        CommandQueueEntry<T> create(Frame p_309619_, P p_305901_);
    }
}
