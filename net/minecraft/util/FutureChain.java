package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.slf4j.Logger;

public class FutureChain implements TaskChainer, AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private CompletableFuture<?> head = CompletableFuture.completedFuture(null);
    private final Executor executor;
    private volatile boolean closed;

    public FutureChain(Executor p_242395_) {
        this.executor = p_242395_;
    }

    @Override
    public <T> void append(CompletableFuture<T> p_307286_, Consumer<T> p_307337_) {
        this.head = this.head.<T, Object>thenCombine(p_307286_, (p_307163_, p_307164_) -> p_307164_).thenAcceptAsync(p_307166_ -> {
            if (!this.closed) {
                p_307337_.accept((T)p_307166_);
            }
        }, this.executor).exceptionally(p_242215_ -> {
            if (p_242215_ instanceof CompletionException completionexception) {
                p_242215_ = completionexception.getCause();
            }

            if (p_242215_ instanceof CancellationException cancellationexception) {
                throw cancellationexception;
            } else {
                LOGGER.error("Chain link failed, continuing to next one", p_242215_);
                return null;
            }
        });
    }

    @Override
    public void close() {
        this.closed = true;
    }
}
