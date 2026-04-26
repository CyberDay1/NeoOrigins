package net.minecraft.server.level.progress;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public interface ChunkProgressListener {
    void updateSpawnPos(ChunkPos p_9617_);

    void onStatusChange(ChunkPos p_9618_, @Nullable ChunkStatus p_330739_);

    void start();

    void stop();

    static int calculateDiameter(int p_320268_) {
        return 2 * p_320268_ + 1;
    }
}
