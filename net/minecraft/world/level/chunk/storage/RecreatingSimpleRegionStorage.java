package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import org.apache.commons.io.FileUtils;

public class RecreatingSimpleRegionStorage extends SimpleRegionStorage {
    private final IOWorker writeWorker;
    private final Path writeFolder;

    public RecreatingSimpleRegionStorage(
        RegionStorageInfo p_326344_,
        Path p_321669_,
        RegionStorageInfo p_326265_,
        Path p_321677_,
        DataFixer p_321544_,
        boolean p_321823_,
        DataFixTypes p_321736_
    ) {
        super(p_326344_, p_321669_, p_321544_, p_321823_, p_321736_);
        this.writeFolder = p_321677_;
        this.writeWorker = new IOWorker(p_326265_, p_321677_, p_321823_);
    }

    @Override
    public CompletableFuture<Void> write(ChunkPos p_321592_, @Nullable CompoundTag p_321676_) {
        return this.writeWorker.store(p_321592_, p_321676_);
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
