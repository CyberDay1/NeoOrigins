package net.minecraft.commands.execution;

@FunctionalInterface
public interface EntryAction<T> {
    void execute(ExecutionContext<T> p_306306_, Frame p_309678_);
}
