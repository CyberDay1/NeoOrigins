package net.minecraft.world.level.chunk.storage;

import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Dynamic;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;

public class SimpleRegionStorage implements AutoCloseable {
    private final IOWorker worker;
    private final DataFixer fixerUpper;
    private final DataFixTypes dataFixType;

    public SimpleRegionStorage(RegionStorageInfo p_326109_, Path p_321582_, DataFixer p_321815_, boolean p_321788_, DataFixTypes p_321522_) {
        this.fixerUpper = p_321815_;
        this.dataFixType = p_321522_;
        this.worker = new IOWorker(p_326109_, p_321582_, p_321788_);
    }

    public CompletableFuture<Optional<CompoundTag>> read(ChunkPos p_321653_) {
        return this.worker.loadAsync(p_321653_);
    }

    public CompletableFuture<Void> write(ChunkPos p_321715_, @Nullable CompoundTag p_321816_) {
        return this.worker.store(p_321715_, p_321816_);
    }

    public CompoundTag upgradeChunkTag(CompoundTag p_321601_, int p_321496_) {
        int i = NbtUtils.getDataVersion(p_321601_, p_321496_);
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, p_321601_, i);
    }

    public Dynamic<Tag> upgradeChunkTag(Dynamic<Tag> p_321643_, int p_321759_) {
        return this.dataFixType.updateToCurrentVersion(this.fixerUpper, p_321643_, p_321759_);
    }

    public CompletableFuture<Void> synchronize(boolean p_321682_) {
        return this.worker.synchronize(p_321682_);
    }

    @Override
    public void close() throws IOException {
        this.worker.close();
    }

    public RegionStorageInfo storageInfo() {
        return this.worker.storageInfo();
    }
}
