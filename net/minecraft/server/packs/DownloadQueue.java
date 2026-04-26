package net.minecraft.server.packs;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.eventlog.JsonEventLog;
import net.minecraft.util.thread.ProcessorMailbox;
import org.slf4j.Logger;

public class DownloadQueue implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_KEPT_PACKS = 20;
    private final Path cacheDir;
    private final JsonEventLog<DownloadQueue.LogEntry> eventLog;
    private final ProcessorMailbox<Runnable> tasks = ProcessorMailbox.create(Util.nonCriticalIoPool(), "download-queue");

    public DownloadQueue(Path p_314439_) throws IOException {
        this.cacheDir = p_314439_;
        FileUtil.createDirectoriesSafe(p_314439_);
        this.eventLog = JsonEventLog.open(DownloadQueue.LogEntry.CODEC, p_314439_.resolve("log.json"));
        DownloadCacheCleaner.vacuumCacheDir(p_314439_, 20);
    }

    private DownloadQueue.BatchResult runDownload(DownloadQueue.BatchConfig p_314482_, Map<UUID, DownloadQueue.DownloadRequest> p_314452_) {
        DownloadQueue.BatchResult downloadqueue$batchresult = new DownloadQueue.BatchResult();
        p_314452_.forEach(
            (p_314645_, p_314573_) -> {
                Path path = this.cacheDir.resolve(p_314645_.toString());
                Path path1 = null;

                try {
                    path1 = HttpUtil.downloadFile(
                        path, p_314573_.url, p_314482_.headers, p_314482_.hashFunction, p_314573_.hash, p_314482_.maxSize, p_314482_.proxy, p_314482_.listener
                    );
                    downloadqueue$batchresult.downloaded.put(p_314645_, path1);
                } catch (Exception exception1) {
                    LOGGER.error("Failed to download {}", p_314573_.url, exception1);
                    downloadqueue$batchresult.failed.add(p_314645_);
                }

                try {
                    this.eventLog
                        .write(
                            new DownloadQueue.LogEntry(
                                p_314645_,
                                p_314573_.url.toString(),
                                Instant.now(),
                                Optional.ofNullable(p_314573_.hash).map(HashCode::toString),
                                path1 != null ? this.getFileInfo(path1) : Either.left("download_failed")
                            )
                        );
                } catch (Exception exception) {
                    LOGGER.error("Failed to log download of {}", p_314573_.url, exception);
                }
            }
        );
        return downloadqueue$batchresult;
    }

    private Either<String, DownloadQueue.FileInfoEntry> getFileInfo(Path p_314601_) {
        try {
            long i = Files.size(p_314601_);
            Path path = this.cacheDir.relativize(p_314601_);
            return Either.right(new DownloadQueue.FileInfoEntry(path.toString(), i));
        } catch (IOException ioexception) {
            LOGGER.error("Failed to get file size of {}", p_314601_, ioexception);
            return Either.left("no_access");
        }
    }

    public CompletableFuture<DownloadQueue.BatchResult> downloadBatch(DownloadQueue.BatchConfig p_314536_, Map<UUID, DownloadQueue.DownloadRequest> p_314654_) {
        return CompletableFuture.supplyAsync(() -> this.runDownload(p_314536_, p_314654_), this.tasks::tell);
    }

    @Override
    public void close() throws IOException {
        this.tasks.close();
        this.eventLog.close();
    }

    public static record BatchConfig(
        HashFunction hashFunction, int maxSize, Map<String, String> headers, Proxy proxy, HttpUtil.DownloadProgressListener listener
    ) {
    }

    public static record BatchResult(Map<UUID, Path> downloaded, Set<UUID> failed) {
        public BatchResult() {
            this(new HashMap<>(), new HashSet<>());
        }
    }

    public static record DownloadRequest(URL url, @Nullable HashCode hash) {
    }

    static record FileInfoEntry(String name, long size) {
        public static final Codec<DownloadQueue.FileInfoEntry> CODEC = RecordCodecBuilder.create(
            p_314458_ -> p_314458_.group(
                        Codec.STRING.fieldOf("name").forGetter(DownloadQueue.FileInfoEntry::name),
                        Codec.LONG.fieldOf("size").forGetter(DownloadQueue.FileInfoEntry::size)
                    )
                    .apply(p_314458_, DownloadQueue.FileInfoEntry::new)
        );
    }

    static record LogEntry(UUID id, String url, Instant time, Optional<String> hash, Either<String, DownloadQueue.FileInfoEntry> errorOrFileInfo) {
        public static final Codec<DownloadQueue.LogEntry> CODEC = RecordCodecBuilder.create(
            p_314637_ -> p_314637_.group(
                        UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(DownloadQueue.LogEntry::id),
                        Codec.STRING.fieldOf("url").forGetter(DownloadQueue.LogEntry::url),
                        ExtraCodecs.INSTANT_ISO8601.fieldOf("time").forGetter(DownloadQueue.LogEntry::time),
                        Codec.STRING.optionalFieldOf("hash").forGetter(DownloadQueue.LogEntry::hash),
                        Codec.mapEither(Codec.STRING.fieldOf("error"), DownloadQueue.FileInfoEntry.CODEC.fieldOf("file"))
                            .forGetter(DownloadQueue.LogEntry::errorOrFileInfo)
                    )
                    .apply(p_314637_, DownloadQueue.LogEntry::new)
        );
    }
}
