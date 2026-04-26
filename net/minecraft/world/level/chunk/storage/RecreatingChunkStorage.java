package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingChunkStorage extends ChunkStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingChunkStorage(
        RegionStorageInfo p_325929_, Path p_321613_, RegionStorageInfo p_326497_, Path p_321731_, DataFixer p_321644_, boolean p_321789_
    ) {
        super(p_325929_, p_321613_, p_321644_, p_321789_);
        this.writeFolder = p_321731_;
        this.writeWorker = new IOWorker(p_326497_, p_321731_, p_321789_);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos p_321778_, CompoundTag p_321767_) {
        this.handleLegacyStructureIndex(p_321778_);
        return this.writeWorker.store(p_321778_, p_321767_);
    }

    @Override
    public void close() throws IOException {
        super.close();
        this.writeWorker.close();
        if (this.writeFolder.toFile().exists()) {
            FileUtils.deleteDirectory(this.writeFolder.toFile());
        }
    }
}
