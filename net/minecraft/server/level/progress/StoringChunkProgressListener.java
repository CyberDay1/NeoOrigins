package net.minecraft.server.level.progress;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public class StoringChunkProgressListener implements ChunkProgressListener {
    private final LoggerChunkProgressListener delegate;
    private final Long2ObjectOpenHashMap<ChunkStatus> statuses = new Long2ObjectOpenHashMap<>();
    private ChunkPos spawnPos = new ChunkPos(0, 0);
    private final int fullDiameter;
    private final int radius;
    private final int diameter;
    private boolean started;

    private StoringChunkProgressListener(LoggerChunkProgressListener p_320931_, int p_9661_, int p_320510_, int p_320086_) {
        this.delegate = p_320931_;
        this.fullDiameter = p_9661_;
        this.radius = p_320510_;
        this.diameter = p_320086_;
    }

    public static StoringChunkProgressListener createFromGameruleRadius(int p_320244_) {
        return p_320244_ > 0 ? create(p_320244_ + 1) : createCompleted();
    }

    public static StoringChunkProgressListener create(int p_320911_) {
        LoggerChunkProgressListener loggerchunkprogresslistener = LoggerChunkProgressListener.create(p_320911_);
        int i = ChunkProgressListener.calculateDiameter(p_320911_);
        int j = p_320911_ + ChunkLevel.RADIUS_AROUND_FULL_CHUNK;
        int k = ChunkProgressListener.calculateDiameter(j);
        return new StoringChunkProgressListener(loggerchunkprogresslistener, i, j, k);
    }

    public static StoringChunkProgressListener createCompleted() {
        return new StoringChunkProgressListener(LoggerChunkProgressListener.createCompleted(), 0, 0, 0);
    }

    @Override
    public void updateSpawnPos(ChunkPos p_9667_) {
        if (this.started) {
            this.delegate.updateSpawnPos(p_9667_);
            this.spawnPos = p_9667_;
        }
    }

    @Override
    public void onStatusChange(ChunkPos p_9669_, @Nullable ChunkStatus p_331945_) {
        if (this.started) {
            this.delegate.onStatusChange(p_9669_, p_331945_);
            if (p_331945_ == null) {
                this.statuses.remove(p_9669_.toLong());
            } else {
                this.statuses.put(p_9669_.toLong(), p_331945_);
            }
        }
    }

    @Override
    public void start() {
        this.started = true;
        this.statuses.clear();
        this.delegate.start();
    }

    @Override
    public void stop() {
        this.started = false;
        this.delegate.stop();
    }

    public int getFullDiameter() {
        return this.fullDiameter;
    }

    public int getDiameter() {
        return this.diameter;
    }

    public int getProgress() {
        return this.delegate.getProgress();
    }

    @Nullable
    public ChunkStatus getStatus(int p_9664_, int p_9665_) {
        return this.statuses.get(ChunkPos.asLong(p_9664_ + this.spawnPos.x - this.radius, p_9665_ + this.spawnPos.z - this.radius));
    }
}
