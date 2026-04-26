package net.minecraft.world.level.chunk.storage;

import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.ReportedException;
import net.minecraft.world.level.ChunkPos;

public interface ChunkIOErrorReporter {
    void reportChunkLoadFailure(Throwable p_352423_, RegionStorageInfo p_352249_, ChunkPos p_352119_);

    void reportChunkSaveFailure(Throwable p_352260_, RegionStorageInfo p_352103_, ChunkPos p_352276_);

    static ReportedException createMisplacedChunkReport(ChunkPos p_352158_, ChunkPos p_352311_) {
        CrashReport crashreport = CrashReport.forThrowable(
            new IllegalStateException("Retrieved chunk position " + p_352158_ + " does not match requested " + p_352311_), "Chunk found in invalid location"
        );
        CrashReportCategory crashreportcategory = crashreport.addCategory("Misplaced Chunk");
        crashreportcategory.setDetail("Stored Position", p_352158_::toString);
        return new ReportedException(crashreport);
    }

    default void reportMisplacedChunk(ChunkPos p_352223_, ChunkPos p_352433_, RegionStorageInfo p_352461_) {
        this.reportChunkLoadFailure(createMisplacedChunkReport(p_352223_, p_352433_), p_352461_, p_352433_);
    }
}
