package net.minecraft.commands.execution;

@FunctionalInterface
public interface UnboundEntryAction<T> {
    void execute(T p_305930_, ExecutionContext<T> p_306291_, Frame p_309692_);

    default EntryAction<T> bind(T p_306075_) {
        return (p_309422_, p_309423_) -> this.execute(p_306075_, p_309422_, p_309423_);
    }
}
