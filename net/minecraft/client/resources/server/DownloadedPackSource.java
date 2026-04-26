package net.minecraft.client.resources.server;

import com.google.common.collect.Lists;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.Unit;
import com.mojang.util.UndashedUuid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.main.GameConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.server.packs.DownloadQueue;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.util.HttpUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class DownloadedPackSource implements AutoCloseable {
    private static final Component SERVER_NAME = Component.translatable("resourcePack.server.name");
    private static final Pattern SHA1 = Pattern.compile("^[a-fA-F0-9]{40}$");
    static final Logger LOGGER = LogUtils.getLogger();
    private static final RepositorySource EMPTY_SOURCE = p_314556_ -> {
    };
    private static final PackSelectionConfig DOWNLOADED_PACK_SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);
    private static final PackLoadFeedback LOG_ONLY_FEEDBACK = new PackLoadFeedback() {
        @Override
        public void reportUpdate(UUID p_314931_, PackLoadFeedback.Update p_314970_) {
            DownloadedPackSource.LOGGER.debug("Downloaded pack {} changed state to {}", p_314931_, p_314970_);
        }

        @Override
        public void reportFinalResult(UUID p_314962_, PackLoadFeedback.FinalResult p_314984_) {
            DownloadedPackSource.LOGGER.debug("Downloaded pack {} finished with state {}", p_314962_, p_314984_);
        }
    };
    final Minecraft minecraft;
    private RepositorySource packSource = EMPTY_SOURCE;
    @Nullable
    private PackReloadConfig.Callbacks pendingReload;
    final ServerPackManager manager;
    private final DownloadQueue downloadQueue;
    private PackSource packType = PackSource.SERVER;
    PackLoadFeedback packFeedback = LOG_ONLY_FEEDBACK;
    private int packIdSerialNumber;

    public DownloadedPackSource(Minecraft p_314574_, Path p_314635_, GameConfig.UserData p_314528_) {
        this.minecraft = p_314574_;

        try {
            this.downloadQueue = new DownloadQueue(p_314635_);
        } catch (IOException ioexception) {
            throw new UncheckedIOException("Failed to open download queue in directory " + p_314635_, ioexception);
        }

        Executor executor = p_314574_::tell;
        this.manager = new ServerPackManager(this.createDownloader(this.downloadQueue, executor, p_314528_.user, p_314528_.proxy), new PackLoadFeedback() {
            @Override
            public void reportUpdate(UUID p_314952_, PackLoadFeedback.Update p_314938_) {
                DownloadedPackSource.this.packFeedback.reportUpdate(p_314952_, p_314938_);
            }

            @Override
            public void reportFinalResult(UUID p_314975_, PackLoadFeedback.FinalResult p_314925_) {
                DownloadedPackSource.this.packFeedback.reportFinalResult(p_314975_, p_314925_);
            }
        }, this.createReloadConfig(), this.createUpdateScheduler(executor), ServerPackManager.PackPromptStatus.PENDING);
    }

    HttpUtil.DownloadProgressListener createDownloadNotifier(final int p_314632_) {
        return new HttpUtil.DownloadProgressListener() {
            private final SystemToast.SystemToastId toastId = new SystemToast.SystemToastId();
            private Component title = Component.empty();
            @Nullable
            private Component message = null;
            private int count;
            private int failCount;
            private OptionalLong totalBytes = OptionalLong.empty();

            private void updateToast() {
                SystemToast.addOrUpdate(DownloadedPackSource.this.minecraft.getToasts(), this.toastId, this.title, this.message);
            }

            private void updateProgress(long p_314935_) {
                if (this.totalBytes.isPresent()) {
                    this.message = Component.translatable("download.pack.progress.percent", p_314935_ * 100L / this.totalBytes.getAsLong());
                } else {
                    this.message = Component.translatable("download.pack.progress.bytes", Unit.humanReadable(p_314935_));
                }

                this.updateToast();
            }

            @Override
            public void requestStart() {
                this.count++;
                this.title = Component.translatable("download.pack.title", this.count, p_314632_);
                this.updateToast();
                DownloadedPackSource.LOGGER.debug("Starting pack {}/{} download", this.count, p_314632_);
            }

            @Override
            public void downloadStart(OptionalLong p_314989_) {
                DownloadedPackSource.LOGGER.debug("File size = {} bytes", p_314989_);
                this.totalBytes = p_314989_;
                this.updateProgress(0L);
            }

            @Override
            public void downloadedBytes(long p_314926_) {
                DownloadedPackSource.LOGGER.debug("Progress for pack {}: {} bytes", this.count, p_314926_);
                this.updateProgress(p_314926_);
            }

            @Override
            public void requestFinished(boolean p_314998_) {
                if (!p_314998_) {
                    DownloadedPackSource.LOGGER.info("Pack {} failed to download", this.count);
                    this.failCount++;
                } else {
                    DownloadedPackSource.LOGGER.debug("Download ended for pack {}", this.count);
                }

                if (this.count == p_314632_) {
                    if (this.failCount > 0) {
                        this.title = Component.translatable("download.pack.failed", this.failCount, p_314632_);
                        this.message = null;
                        this.updateToast();
                    } else {
                        SystemToast.forceHide(DownloadedPackSource.this.minecraft.getToasts(), this.toastId);
                    }
                }
            }
        };
    }

    private PackDownloader createDownloader(final DownloadQueue p_314570_, final Executor p_314421_, final User p_314576_, final Proxy p_314551_) {
        return new PackDownloader() {
            private static final int MAX_PACK_SIZE_BYTES = 262144000;
            private static final HashFunction CACHE_HASHING_FUNCTION = Hashing.sha1();

            private Map<String, String> createDownloadHeaders() {
                WorldVersion worldversion = SharedConstants.getCurrentVersion();
                return Map.of(
                    "X-Minecraft-Username",
                    p_314576_.getName(),
                    "X-Minecraft-UUID",
                    UndashedUuid.toString(p_314576_.getProfileId()),
                    "X-Minecraft-Version",
                    worldversion.getName(),
                    "X-Minecraft-Version-ID",
                    worldversion.getId(),
                    "X-Minecraft-Pack-Format",
                    String.valueOf(worldversion.getPackVersion(PackType.CLIENT_RESOURCES)),
                    "User-Agent",
                    "Minecraft Java/" + worldversion.getName()
                );
            }

            @Override
            public void download(Map<UUID, DownloadQueue.DownloadRequest> p_314939_, Consumer<DownloadQueue.BatchResult> p_314982_) {
                p_314570_.downloadBatch(
                        new DownloadQueue.BatchConfig(
                            CACHE_HASHING_FUNCTION,
                            262144000,
                            this.createDownloadHeaders(),
                            p_314551_,
                            DownloadedPackSource.this.createDownloadNotifier(p_314939_.size())
                        ),
                        p_314939_
                    )
                    .thenAcceptAsync(p_314982_, p_314421_);
            }
        };
    }

    private Runnable createUpdateScheduler(final Executor p_314595_) {
        return new Runnable() {
            private boolean scheduledInMainExecutor;
            private boolean hasUpdates;

            @Override
            public void run() {
                this.hasUpdates = true;
                if (!this.scheduledInMainExecutor) {
                    this.scheduledInMainExecutor = true;
                    p_314595_.execute(this::runAllUpdates);
                }
            }

            private void runAllUpdates() {
                while (this.hasUpdates) {
                    this.hasUpdates = false;
                    DownloadedPackSource.this.manager.tick();
                }

                this.scheduledInMainExecutor = false;
            }
        };
    }

    private PackReloadConfig createReloadConfig() {
        return this::startReload;
    }

    @Nullable
    private List<Pack> loadRequestedPacks(List<PackReloadConfig.IdAndPath> p_314642_) {
        List<Pack> list = new ArrayList<>(p_314642_.size());

        for (PackReloadConfig.IdAndPath packreloadconfig$idandpath : Lists.reverse(p_314642_)) {
            String s = String.format(Locale.ROOT, "server/%08X/%s", this.packIdSerialNumber++, packreloadconfig$idandpath.id());
            Path path = packreloadconfig$idandpath.path();
            PackLocationInfo packlocationinfo = new PackLocationInfo(s, SERVER_NAME, this.packType, Optional.empty());
            Pack.ResourcesSupplier pack$resourcessupplier = new FilePackResources.FileResourcesSupplier(path);
            int i = SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES);
            Pack.Metadata pack$metadata = Pack.readPackMetadata(packlocationinfo, pack$resourcessupplier, i);
            if (pack$metadata == null) {
                LOGGER.warn("Invalid pack metadata in {}, ignoring all", path);
                return null;
            }

            list.add(new Pack(packlocationinfo, pack$resourcessupplier, pack$metadata, DOWNLOADED_PACK_SELECTION));
        }

        return list;
    }

    public RepositorySource createRepositorySource() {
        return p_314437_ -> this.packSource.loadPacks(p_314437_);
    }

    private static RepositorySource configureSource(List<Pack> p_314416_) {
        return p_314416_.isEmpty() ? EMPTY_SOURCE : p_314416_::forEach;
    }

    private void startReload(PackReloadConfig.Callbacks p_314628_) {
        this.pendingReload = p_314628_;
        List<PackReloadConfig.IdAndPath> list = p_314628_.packsToLoad();
        List<Pack> list1 = this.loadRequestedPacks(list);
        if (list1 == null) {
            p_314628_.onFailure(false);
            List<PackReloadConfig.IdAndPath> list2 = p_314628_.packsToLoad();
            list1 = this.loadRequestedPacks(list2);
            if (list1 == null) {
                LOGGER.warn("Double failure in loading server packs");
                list1 = List.of();
            }
        }

        this.packSource = configureSource(list1);
        this.minecraft.reloadResourcePacks();
    }

    public void onRecovery() {
        if (this.pendingReload != null) {
            this.pendingReload.onFailure(false);
            List<Pack> list = this.loadRequestedPacks(this.pendingReload.packsToLoad());
            if (list == null) {
                LOGGER.warn("Double failure in loading server packs");
                list = List.of();
            }

            this.packSource = configureSource(list);
        }
    }

    public void onRecoveryFailure() {
        if (this.pendingReload != null) {
            this.pendingReload.onFailure(true);
            this.pendingReload = null;
            this.packSource = EMPTY_SOURCE;
        }
    }

    public void onReloadSuccess() {
        if (this.pendingReload != null) {
            this.pendingReload.onSuccess();
            this.pendingReload = null;
        }
    }

    @Nullable
    private static HashCode tryParseSha1Hash(@Nullable String p_314590_) {
        return p_314590_ != null && SHA1.matcher(p_314590_).matches() ? HashCode.fromString(p_314590_.toLowerCase(Locale.ROOT)) : null;
    }

    public void pushPack(UUID p_314526_, URL p_314648_, @Nullable String p_314530_) {
        HashCode hashcode = tryParseSha1Hash(p_314530_);
        this.manager.pushPack(p_314526_, p_314648_, hashcode);
    }

    public void pushLocalPack(UUID p_314510_, Path p_314417_) {
        this.manager.pushLocalPack(p_314510_, p_314417_);
    }

    public void popPack(UUID p_314587_) {
        this.manager.popPack(p_314587_);
    }

    public void popAll() {
        this.manager.popAll();
    }

    private static PackLoadFeedback createPackResponseSender(final Connection p_314519_) {
        return new PackLoadFeedback() {
            @Override
            public void reportUpdate(UUID p_314956_, PackLoadFeedback.Update p_314990_) {
                DownloadedPackSource.LOGGER.debug("Pack {} changed status to {}", p_314956_, p_314990_);

                ServerboundResourcePackPacket.Action serverboundresourcepackpacket$action = switch (p_314990_) {
                    case ACCEPTED -> ServerboundResourcePackPacket.Action.ACCEPTED;
                    case DOWNLOADED -> ServerboundResourcePackPacket.Action.DOWNLOADED;
                };
                p_314519_.send(new ServerboundResourcePackPacket(p_314956_, serverboundresourcepackpacket$action));
            }

            @Override
            public void reportFinalResult(UUID p_315010_, PackLoadFeedback.FinalResult p_314963_) {
                DownloadedPackSource.LOGGER.debug("Pack {} changed status to {}", p_315010_, p_314963_);

                ServerboundResourcePackPacket.Action serverboundresourcepackpacket$action = switch (p_314963_) {
                    case APPLIED -> ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED;
                    case DOWNLOAD_FAILED -> ServerboundResourcePackPacket.Action.FAILED_DOWNLOAD;
                    case DECLINED -> ServerboundResourcePackPacket.Action.DECLINED;
                    case DISCARDED -> ServerboundResourcePackPacket.Action.DISCARDED;
                    case ACTIVATION_FAILED -> ServerboundResourcePackPacket.Action.FAILED_RELOAD;
                };
                p_314519_.send(new ServerboundResourcePackPacket(p_315010_, serverboundresourcepackpacket$action));
            }
        };
    }

    public void configureForServerControl(Connection p_314502_, ServerPackManager.PackPromptStatus p_314463_) {
        this.packType = PackSource.SERVER;
        this.packFeedback = createPackResponseSender(p_314502_);
        switch (p_314463_) {
            case ALLOWED:
                this.manager.allowServerPacks();
                break;
            case DECLINED:
                this.manager.rejectServerPacks();
                break;
            case PENDING:
                this.manager.resetPromptStatus();
        }
    }

    public void configureForLocalWorld() {
        this.packType = PackSource.WORLD;
        this.packFeedback = LOG_ONLY_FEEDBACK;
        this.manager.allowServerPacks();
    }

    public void allowServerPacks() {
        this.manager.allowServerPacks();
    }

    public void rejectServerPacks() {
        this.manager.rejectServerPacks();
    }

    public CompletableFuture<Void> waitForPackFeedback(final UUID p_314539_) {
        final CompletableFuture<Void> completablefuture = new CompletableFuture<>();
        final PackLoadFeedback packloadfeedback = this.packFeedback;
        this.packFeedback = new PackLoadFeedback() {
            @Override
            public void reportUpdate(UUID p_314992_, PackLoadFeedback.Update p_314954_) {
                packloadfeedback.reportUpdate(p_314992_, p_314954_);
            }

            @Override
            public void reportFinalResult(UUID p_314964_, PackLoadFeedback.FinalResult p_314957_) {
                if (p_314539_.equals(p_314964_)) {
                    DownloadedPackSource.this.packFeedback = packloadfeedback;
                    if (p_314957_ == PackLoadFeedback.FinalResult.APPLIED) {
                        completablefuture.complete(null);
                    } else {
                        completablefuture.completeExceptionally(new IllegalStateException("Failed to apply pack " + p_314964_ + ", reason: " + p_314957_));
                    }
                }

                packloadfeedback.reportFinalResult(p_314964_, p_314957_);
            }
        };
        return completablefuture;
    }

    public void cleanupAfterDisconnect() {
        this.manager.popAll();
        this.packFeedback = LOG_ONLY_FEEDBACK;
        this.manager.resetPromptStatus();
    }

    @Override
    public void close() throws IOException {
        this.downloadQueue.close();
    }
}
