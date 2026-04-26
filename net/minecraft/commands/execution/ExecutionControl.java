package net.minecraft.commands.execution;

import javax.annotation.Nullable;
import net.minecraft.commands.ExecutionCommandSource;

public interface ExecutionControl<T> {
    void queueNext(EntryAction<T> p_305867_);

    void tracer(@Nullable TraceCallbacks p_305968_);

    @Nullable
    TraceCallbacks tracer();

    Frame currentFrame();

    static <T extends ExecutionCommandSource<T>> ExecutionControl<T> create(final ExecutionContext<T> p_309708_, final Frame p_309584_) {
        return new ExecutionControl<T>() {
            @Override
            public void queueNext(EntryAction<T> p_309579_) {
                p_309708_.queueNext(new CommandQueueEntry<>(p_309584_, p_309579_));
            }

            @Override
            public void tracer(@Nullable TraceCallbacks p_309633_) {
                p_309708_.tracer(p_309633_);
            }

            @Nullable
            @Override
            public TraceCallbacks tracer() {
                return p_309708_.tracer();
            }

            @Override
            public Frame currentFrame() {
                return p_309584_;
            }
        };
    }
}
