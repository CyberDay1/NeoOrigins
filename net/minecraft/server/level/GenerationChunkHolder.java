package net.minecraft.server.level;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;
import javax.annotation.Nullable;
import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StaticCache2D;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.status.ChunkStep;

public abstract class GenerationChunkHolder {
    private static final List<ChunkStatus> CHUNK_STATUSES = ChunkStatus.getStatusList();
    private static final ChunkResult<ChunkAccess> NOT_DONE_YET = ChunkResult.error("Not done yet");
    public static final ChunkResult<ChunkAccess> UNLOADED_CHUNK = ChunkResult.error("Unloaded chunk");
    public static final CompletableFuture<ChunkResult<ChunkAccess>> UNLOADED_CHUNK_FUTURE = CompletableFuture.completedFuture(UNLOADED_CHUNK);
    protected final ChunkPos pos;
    @Nullable
    private volatile ChunkStatus highestAllowedStatus;
    private final AtomicReference<ChunkStatus> startedWork = new AtomicReference<>();
    private final AtomicReferenceArray<CompletableFuture<ChunkResult<ChunkAccess>>> futures = new AtomicReferenceArray<>(CHUNK_STATUSES.size());
    private final AtomicReference<ChunkGenerationTask> task = new AtomicReference<>();
    private final AtomicInteger generationRefCount = new AtomicInteger();
    public net.minecraft.world.level.chunk.LevelChunk currentlyLoading; // Forge: Used to bypass future chain when loading chunks.

    public GenerationChunkHolder(ChunkPos p_347689_) {
        this.pos = p_347689_;
    }

    public CompletableFuture<ChunkResult<ChunkAccess>> scheduleChunkGenerationTask(ChunkStatus p_347666_, ChunkMap p_347681_) {
        if (this.isStatusDisallowed(p_347666_)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.getOrCreateFuture(p_347666_);
            if (completablefuture.isDone()) {
                return completablefuture;
            } else {
                ChunkGenerationTask chunkgenerationtask = this.task.get();
                if (chunkgenerationtask == null || p_347666_.isAfter(chunkgenerationtask.targetStatus)) {
                    this.rescheduleChunkTask(p_347681_, p_347666_);
                }

                return completablefuture;
            }
        }
    }

    CompletableFuture<ChunkResult<ChunkAccess>> applyStep(ChunkStep p_347491_, GeneratingChunkMap p_347490_, StaticCache2D<GenerationChunkHolder> p_347657_) {
        if (this.isStatusDisallowed(p_347491_.targetStatus())) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            return this.acquireStatusBump(p_347491_.targetStatus()) ? p_347490_.applyStep(this, p_347491_, p_347657_).handle((p_347506_, p_347622_) -> {
                if (p_347622_ != null) {
                    CrashReport crashreport = CrashReport.forThrowable(p_347622_, "Exception chunk generation/loading");
                    MinecraftServer.setFatalException(new ReportedException(crashreport));
                } else {
                    this.completeFuture(p_347491_.targetStatus(), p_347506_);
                }

                return ChunkResult.of(p_347506_);
            }) : this.getOrCreateFuture(p_347491_.targetStatus());
        }
    }

    protected void updateHighestAllowedStatus(ChunkMap p_347458_) {
        ChunkStatus chunkstatus = this.highestAllowedStatus;
        ChunkStatus chunkstatus1 = ChunkLevel.generationStatus(this.getTicketLevel());
        this.highestAllowedStatus = chunkstatus1;
        boolean flag = chunkstatus != null && (chunkstatus1 == null || chunkstatus1.isBefore(chunkstatus));
        if (flag) {
            this.failAndClearPendingFuturesBetween(chunkstatus1, chunkstatus);
            if (this.task.get() != null) {
                this.rescheduleChunkTask(p_347458_, this.findHighestStatusWithPendingFuture(chunkstatus1));
            }
        }
    }

    public void replaceProtoChunk(ImposterProtoChunk p_347538_) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = CompletableFuture.completedFuture(ChunkResult.of(p_347538_));

        for (int i = 0; i < this.futures.length() - 1; i++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = this.futures.get(i);
            Objects.requireNonNull(completablefuture1);
            ChunkAccess chunkaccess = completablefuture1.getNow(NOT_DONE_YET).orElse(null);
            if (!(chunkaccess instanceof ProtoChunk)) {
                throw new IllegalStateException("Trying to replace a ProtoChunk, but found " + chunkaccess);
            }

            if (!this.futures.compareAndSet(i, completablefuture1, completablefuture)) {
                throw new IllegalStateException("Future changed by other thread while trying to replace it");
            }
        }
    }

    void removeTask(ChunkGenerationTask p_347509_) {
        this.task.compareAndSet(p_347509_, null);
    }

    private void rescheduleChunkTask(ChunkMap p_347690_, @Nullable ChunkStatus p_347571_) {
        ChunkGenerationTask chunkgenerationtask;
        if (p_347571_ != null) {
            chunkgenerationtask = p_347690_.scheduleGenerationTask(p_347571_, this.getPos());
        } else {
            chunkgenerationtask = null;
        }

        ChunkGenerationTask chunkgenerationtask1 = this.task.getAndSet(chunkgenerationtask);
        if (chunkgenerationtask1 != null) {
            chunkgenerationtask1.markForCancellation();
        }
    }

    private CompletableFuture<ChunkResult<ChunkAccess>> getOrCreateFuture(ChunkStatus p_347537_) {
        if (this.isStatusDisallowed(p_347537_)) {
            return UNLOADED_CHUNK_FUTURE;
        } else {
            int i = p_347537_.getIndex();
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(i);

            while (completablefuture == null) {
                CompletableFuture<ChunkResult<ChunkAccess>> completablefuture1 = new CompletableFuture<>();
                completablefuture = this.futures.compareAndExchange(i, null, completablefuture1);
                if (completablefuture == null) {
                    if (this.isStatusDisallowed(p_347537_)) {
                        this.failAndClearPendingFuture(i, completablefuture1);
                        return UNLOADED_CHUNK_FUTURE;
                    }

                    return completablefuture1;
                }
            }

            return completablefuture;
        }
    }

    private void failAndClearPendingFuturesBetween(@Nullable ChunkStatus p_347514_, ChunkStatus p_347559_) {
        int i = p_347514_ == null ? 0 : p_347514_.getIndex() + 1;
        int j = p_347559_.getIndex();

        for (int k = i; k <= j; k++) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(k);
            if (completablefuture != null) {
                this.failAndClearPendingFuture(k, completablefuture);
            }
        }
    }

    private void failAndClearPendingFuture(int p_347608_, CompletableFuture<ChunkResult<ChunkAccess>> p_347723_) {
        if (p_347723_.complete(UNLOADED_CHUNK) && !this.futures.compareAndSet(p_347608_, p_347723_, null)) {
            throw new IllegalStateException("Nothing else should replace the future here");
        }
    }

    private void completeFuture(ChunkStatus p_347482_, ChunkAccess p_347557_) {
        ChunkResult<ChunkAccess> chunkresult = ChunkResult.of(p_347557_);
        int i = p_347482_.getIndex();

        while (true) {
            CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(i);
            if (completablefuture == null) {
                if (this.futures.compareAndSet(i, null, CompletableFuture.completedFuture(chunkresult))) {
                    return;
                }
            } else {
                if (completablefuture.complete(chunkresult)) {
                    return;
                }

                if (completablefuture.getNow(NOT_DONE_YET).isSuccess()) {
                    throw new IllegalStateException("Trying to complete a future but found it to be completed successfully already");
                }

                Thread.yield();
            }
        }
    }

    @Nullable
    private ChunkStatus findHighestStatusWithPendingFuture(@Nullable ChunkStatus p_347692_) {
        if (p_347692_ == null) {
            return null;
        } else {
            ChunkStatus chunkstatus = p_347692_;

            for (ChunkStatus chunkstatus1 = this.startedWork.get();
                chunkstatus1 == null || chunkstatus.isAfter(chunkstatus1);
                chunkstatus = chunkstatus.getParent()
            ) {
                if (this.futures.get(chunkstatus.getIndex()) != null) {
                    return chunkstatus;
                }

                if (chunkstatus == ChunkStatus.EMPTY) {
                    break;
                }
            }

            return null;
        }
    }

    private boolean acquireStatusBump(ChunkStatus p_347706_) {
        ChunkStatus chunkstatus = p_347706_ == ChunkStatus.EMPTY ? null : p_347706_.getParent();
        ChunkStatus chunkstatus1 = this.startedWork.compareAndExchange(chunkstatus, p_347706_);
        if (chunkstatus1 == chunkstatus) {
            return true;
        } else if (chunkstatus1 != null && !p_347706_.isAfter(chunkstatus1)) {
            return false;
        } else {
            throw new IllegalStateException("Unexpected last startedWork status: " + chunkstatus1 + " while trying to start: " + p_347706_);
        }
    }

    private boolean isStatusDisallowed(ChunkStatus p_347619_) {
        ChunkStatus chunkstatus = this.highestAllowedStatus;
        return chunkstatus == null || p_347619_.isAfter(chunkstatus);
    }

    public void increaseGenerationRefCount() {
        this.generationRefCount.incrementAndGet();
    }

    public void decreaseGenerationRefCount() {
        int i = this.generationRefCount.decrementAndGet();
        if (i < 0) {
            throw new IllegalStateException("More releases than claims. Count: " + i);
        }
    }

    public int getGenerationRefCount() {
        return this.generationRefCount.get();
    }

    @Nullable
    public ChunkAccess getChunkIfPresentUnchecked(ChunkStatus p_347539_) {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(p_347539_.getIndex());
        return completablefuture == null ? null : completablefuture.getNow(NOT_DONE_YET).orElse(null);
    }

    @Nullable
    public ChunkAccess getChunkIfPresent(ChunkStatus p_347694_) {
        return this.isStatusDisallowed(p_347694_) ? null : this.getChunkIfPresentUnchecked(p_347694_);
    }

    @Nullable
    public ChunkAccess getLatestChunk() {
        ChunkStatus chunkstatus = this.startedWork.get();
        if (chunkstatus == null) {
            return null;
        } else {
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);
            return chunkaccess != null ? chunkaccess : this.getChunkIfPresentUnchecked(chunkstatus.getParent());
        }
    }

    @Nullable
    public ChunkStatus getPersistedStatus() {
        CompletableFuture<ChunkResult<ChunkAccess>> completablefuture = this.futures.get(ChunkStatus.EMPTY.getIndex());
        ChunkAccess chunkaccess = completablefuture == null ? null : completablefuture.getNow(NOT_DONE_YET).orElse(null);
        return chunkaccess == null ? null : chunkaccess.getPersistedStatus();
    }

    public ChunkPos getPos() {
        return this.pos;
    }

    public FullChunkStatus getFullStatus() {
        return ChunkLevel.fullStatus(this.getTicketLevel());
    }

    public abstract int getTicketLevel();

    public abstract int getQueueLevel();

    @VisibleForDebug
    public List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> getAllFutures() {
        List<Pair<ChunkStatus, CompletableFuture<ChunkResult<ChunkAccess>>>> list = new ArrayList<>();

        for (int i = 0; i < CHUNK_STATUSES.size(); i++) {
            list.add(Pair.of(CHUNK_STATUSES.get(i), this.futures.get(i)));
        }

        return list;
    }

    @Nullable
    @VisibleForDebug
    public ChunkStatus getLatestStatus() {
        for (int i = CHUNK_STATUSES.size() - 1; i >= 0; i--) {
            ChunkStatus chunkstatus = CHUNK_STATUSES.get(i);
            ChunkAccess chunkaccess = this.getChunkIfPresentUnchecked(chunkstatus);
            if (chunkaccess != null) {
                return chunkstatus;
            }
        }

        return null;
    }
}
