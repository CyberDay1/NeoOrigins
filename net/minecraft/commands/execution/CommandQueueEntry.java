package net.minecraft.commands.execution;

public record CommandQueueEntry<T>(Frame frame, EntryAction<T> action) {
    public void execute(ExecutionContext<T> p_306246_) {
        this.action.execute(p_306246_, this.frame);
    }
}
