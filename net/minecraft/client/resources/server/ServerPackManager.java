package net.minecraft.client.resources.server;

import com.google.common.hash.HashCode;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.packs.DownloadQueue;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ServerPackManager {
    private final PackDownloader downloader;
    final PackLoadFeedback packLoadFeedback;
    private final PackReloadConfig reloadConfig;
    private final Runnable updateRequest;
    private ServerPackManager.PackPromptStatus packPromptStatus;
    final List<ServerPackManager.ServerPackData> packs = new ArrayList<>();

    public ServerPackManager(
        PackDownloader p_314651_, PackLoadFeedback p_314441_, PackReloadConfig p_314471_, Runnable p_314453_, ServerPackManager.PackPromptStatus p_314476_
    ) {
        this.downloader = p_314651_;
        this.packLoadFeedback = p_314441_;
        this.reloadConfig = p_314471_;
        this.updateRequest = p_314453_;
        this.packPromptStatus = p_314476_;
    }

    void registerForUpdate() {
        this.updateRequest.run();
    }

    private void markExistingPacksAsRemoved(UUID p_314529_) {
        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (serverpackmanager$serverpackdata.id.equals(p_314529_)) {
                serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.SERVER_REPLACED);
            }
        }
    }

    public void pushPack(UUID p_314641_, URL p_314493_, @Nullable HashCode p_314571_) {
        if (this.packPromptStatus == ServerPackManager.PackPromptStatus.DECLINED) {
            this.packLoadFeedback.reportFinalResult(p_314641_, PackLoadFeedback.FinalResult.DECLINED);
        } else {
            this.pushNewPack(p_314641_, new ServerPackManager.ServerPackData(p_314641_, p_314493_, p_314571_));
        }
    }

    public void pushLocalPack(UUID p_314448_, Path p_314520_) {
        if (this.packPromptStatus == ServerPackManager.PackPromptStatus.DECLINED) {
            this.packLoadFeedback.reportFinalResult(p_314448_, PackLoadFeedback.FinalResult.DECLINED);
        } else {
            URL url;
            try {
                url = p_314520_.toUri().toURL();
            } catch (MalformedURLException malformedurlexception) {
                throw new IllegalStateException("Can't convert path to URL " + p_314520_, malformedurlexception);
            }

            ServerPackManager.ServerPackData serverpackmanager$serverpackdata = new ServerPackManager.ServerPackData(p_314448_, url, null);
            serverpackmanager$serverpackdata.downloadStatus = ServerPackManager.PackDownloadStatus.DONE;
            serverpackmanager$serverpackdata.path = p_314520_;
            this.pushNewPack(p_314448_, serverpackmanager$serverpackdata);
        }
    }

    private void pushNewPack(UUID p_314541_, ServerPackManager.ServerPackData p_314473_) {
        this.markExistingPacksAsRemoved(p_314541_);
        this.packs.add(p_314473_);
        if (this.packPromptStatus == ServerPackManager.PackPromptStatus.ALLOWED) {
            this.acceptPack(p_314473_);
        }

        this.registerForUpdate();
    }

    private void acceptPack(ServerPackManager.ServerPackData p_314599_) {
        this.packLoadFeedback.reportUpdate(p_314599_.id, PackLoadFeedback.Update.ACCEPTED);
        p_314599_.promptAccepted = true;
    }

    @Nullable
    private ServerPackManager.ServerPackData findPackInfo(UUID p_314517_) {
        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (!serverpackmanager$serverpackdata.isRemoved() && serverpackmanager$serverpackdata.id.equals(p_314517_)) {
                return serverpackmanager$serverpackdata;
            }
        }

        return null;
    }

    public void popPack(UUID p_314547_) {
        ServerPackManager.ServerPackData serverpackmanager$serverpackdata = this.findPackInfo(p_314547_);
        if (serverpackmanager$serverpackdata != null) {
            serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.SERVER_REMOVED);
            this.registerForUpdate();
        }
    }

    public void popAll() {
        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.SERVER_REMOVED);
        }

        this.registerForUpdate();
    }

    public void allowServerPacks() {
        this.packPromptStatus = ServerPackManager.PackPromptStatus.ALLOWED;

        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (!serverpackmanager$serverpackdata.promptAccepted && !serverpackmanager$serverpackdata.isRemoved()) {
                this.acceptPack(serverpackmanager$serverpackdata);
            }
        }

        this.registerForUpdate();
    }

    public void rejectServerPacks() {
        this.packPromptStatus = ServerPackManager.PackPromptStatus.DECLINED;

        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (!serverpackmanager$serverpackdata.promptAccepted) {
                serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.DECLINED);
            }
        }

        this.registerForUpdate();
    }

    public void resetPromptStatus() {
        this.packPromptStatus = ServerPackManager.PackPromptStatus.PENDING;
    }

    public void tick() {
        boolean flag = this.updateDownloads();
        if (!flag) {
            this.triggerReloadIfNeeded();
        }

        this.cleanupRemovedPacks();
    }

    private void cleanupRemovedPacks() {
        this.packs.removeIf(p_314901_ -> {
            if (p_314901_.activationStatus != ServerPackManager.ActivationStatus.INACTIVE) {
                return false;
            } else if (p_314901_.removalReason != null) {
                PackLoadFeedback.FinalResult packloadfeedback$finalresult = p_314901_.removalReason.serverResponse;
                if (packloadfeedback$finalresult != null) {
                    this.packLoadFeedback.reportFinalResult(p_314901_.id, packloadfeedback$finalresult);
                }

                return true;
            } else {
                return false;
            }
        });
    }

    private void onDownload(Collection<ServerPackManager.ServerPackData> p_314562_, DownloadQueue.BatchResult p_314549_) {
        if (!p_314549_.failed().isEmpty()) {
            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
                if (serverpackmanager$serverpackdata.activationStatus != ServerPackManager.ActivationStatus.ACTIVE) {
                    if (p_314549_.failed().contains(serverpackmanager$serverpackdata.id)) {
                        serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.DOWNLOAD_FAILED);
                    } else {
                        serverpackmanager$serverpackdata.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.DISCARDED);
                    }
                }
            }
        }

        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata1 : p_314562_) {
            Path path = p_314549_.downloaded().get(serverpackmanager$serverpackdata1.id);
            if (path != null) {
                serverpackmanager$serverpackdata1.downloadStatus = ServerPackManager.PackDownloadStatus.DONE;
                serverpackmanager$serverpackdata1.path = path;
                if (!serverpackmanager$serverpackdata1.isRemoved()) {
                    this.packLoadFeedback.reportUpdate(serverpackmanager$serverpackdata1.id, PackLoadFeedback.Update.DOWNLOADED);
                }
            }
        }

        this.registerForUpdate();
    }

    private boolean updateDownloads() {
        List<ServerPackManager.ServerPackData> list = new ArrayList<>();
        boolean flag = false;

        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (!serverpackmanager$serverpackdata.isRemoved() && serverpackmanager$serverpackdata.promptAccepted) {
                if (serverpackmanager$serverpackdata.downloadStatus != ServerPackManager.PackDownloadStatus.DONE) {
                    flag = true;
                }

                if (serverpackmanager$serverpackdata.downloadStatus == ServerPackManager.PackDownloadStatus.REQUESTED) {
                    serverpackmanager$serverpackdata.downloadStatus = ServerPackManager.PackDownloadStatus.PENDING;
                    list.add(serverpackmanager$serverpackdata);
                }
            }
        }

        if (!list.isEmpty()) {
            Map<UUID, DownloadQueue.DownloadRequest> map = new HashMap<>();

            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata1 : list) {
                map.put(
                    serverpackmanager$serverpackdata1.id,
                    new DownloadQueue.DownloadRequest(serverpackmanager$serverpackdata1.url, serverpackmanager$serverpackdata1.hash)
                );
            }

            this.downloader.download(map, p_314466_ -> this.onDownload(list, p_314466_));
        }

        return flag;
    }

    private void triggerReloadIfNeeded() {
        boolean flag = false;
        final List<ServerPackManager.ServerPackData> list = new ArrayList<>();
        final List<ServerPackManager.ServerPackData> list1 = new ArrayList<>();

        for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata : this.packs) {
            if (serverpackmanager$serverpackdata.activationStatus == ServerPackManager.ActivationStatus.PENDING) {
                return;
            }

            boolean flag1 = serverpackmanager$serverpackdata.promptAccepted
                && serverpackmanager$serverpackdata.downloadStatus == ServerPackManager.PackDownloadStatus.DONE
                && !serverpackmanager$serverpackdata.isRemoved();
            if (flag1 && serverpackmanager$serverpackdata.activationStatus == ServerPackManager.ActivationStatus.INACTIVE) {
                list.add(serverpackmanager$serverpackdata);
                flag = true;
            }

            if (serverpackmanager$serverpackdata.activationStatus == ServerPackManager.ActivationStatus.ACTIVE) {
                if (!flag1) {
                    flag = true;
                    list1.add(serverpackmanager$serverpackdata);
                } else {
                    list.add(serverpackmanager$serverpackdata);
                }
            }
        }

        if (flag) {
            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata1 : list) {
                if (serverpackmanager$serverpackdata1.activationStatus != ServerPackManager.ActivationStatus.ACTIVE) {
                    serverpackmanager$serverpackdata1.activationStatus = ServerPackManager.ActivationStatus.PENDING;
                }
            }

            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata2 : list1) {
                serverpackmanager$serverpackdata2.activationStatus = ServerPackManager.ActivationStatus.PENDING;
            }

            this.reloadConfig
                .scheduleReload(
                    new PackReloadConfig.Callbacks() {
                        @Override
                        public void onSuccess() {
                            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata3 : list) {
                                serverpackmanager$serverpackdata3.activationStatus = ServerPackManager.ActivationStatus.ACTIVE;
                                if (serverpackmanager$serverpackdata3.removalReason == null) {
                                    ServerPackManager.this.packLoadFeedback
                                        .reportFinalResult(serverpackmanager$serverpackdata3.id, PackLoadFeedback.FinalResult.APPLIED);
                                }
                            }

                            for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata4 : list1) {
                                serverpackmanager$serverpackdata4.activationStatus = ServerPackManager.ActivationStatus.INACTIVE;
                            }

                            ServerPackManager.this.registerForUpdate();
                        }

                        @Override
                        public void onFailure(boolean p_314558_) {
                            if (!p_314558_) {
                                list.clear();

                                for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata3 : ServerPackManager.this.packs) {
                                    switch (serverpackmanager$serverpackdata3.activationStatus) {
                                        case INACTIVE:
                                            serverpackmanager$serverpackdata3.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.DISCARDED);
                                            break;
                                        case PENDING:
                                            serverpackmanager$serverpackdata3.activationStatus = ServerPackManager.ActivationStatus.INACTIVE;
                                            serverpackmanager$serverpackdata3.setRemovalReasonIfNotSet(ServerPackManager.RemovalReason.ACTIVATION_FAILED);
                                            break;
                                        case ACTIVE:
                                            list.add(serverpackmanager$serverpackdata3);
                                    }
                                }

                                ServerPackManager.this.registerForUpdate();
                            } else {
                                for (ServerPackManager.ServerPackData serverpackmanager$serverpackdata4 : ServerPackManager.this.packs) {
                                    if (serverpackmanager$serverpackdata4.activationStatus == ServerPackManager.ActivationStatus.PENDING) {
                                        serverpackmanager$serverpackdata4.activationStatus = ServerPackManager.ActivationStatus.INACTIVE;
                                    }
                                }
                            }
                        }

                        @Override
                        public List<PackReloadConfig.IdAndPath> packsToLoad() {
                            return list.stream().map(p_314577_ -> new PackReloadConfig.IdAndPath(p_314577_.id, p_314577_.path)).toList();
                        }
                    }
                );
        }
    }

    @OnlyIn(Dist.CLIENT)
    static enum ActivationStatus {
        INACTIVE,
        PENDING,
        ACTIVE;
    }

    @OnlyIn(Dist.CLIENT)
    static enum PackDownloadStatus {
        REQUESTED,
        PENDING,
        DONE;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum PackPromptStatus {
        PENDING,
        ALLOWED,
        DECLINED;
    }

    @OnlyIn(Dist.CLIENT)
    static enum RemovalReason {
        DOWNLOAD_FAILED(PackLoadFeedback.FinalResult.DOWNLOAD_FAILED),
        ACTIVATION_FAILED(PackLoadFeedback.FinalResult.ACTIVATION_FAILED),
        DECLINED(PackLoadFeedback.FinalResult.DECLINED),
        DISCARDED(PackLoadFeedback.FinalResult.DISCARDED),
        SERVER_REMOVED(null),
        SERVER_REPLACED(null);

        @Nullable
        final PackLoadFeedback.FinalResult serverResponse;

        private RemovalReason(@Nullable PackLoadFeedback.FinalResult p_314987_) {
            this.serverResponse = p_314987_;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class ServerPackData {
        final UUID id;
        final URL url;
        @Nullable
        final HashCode hash;
        @Nullable
        Path path;
        @Nullable
        ServerPackManager.RemovalReason removalReason;
        ServerPackManager.PackDownloadStatus downloadStatus = ServerPackManager.PackDownloadStatus.REQUESTED;
        ServerPackManager.ActivationStatus activationStatus = ServerPackManager.ActivationStatus.INACTIVE;
        boolean promptAccepted;

        ServerPackData(UUID p_314450_, URL p_314542_, @Nullable HashCode p_314445_) {
            this.id = p_314450_;
            this.url = p_314542_;
            this.hash = p_314445_;
        }

        public void setRemovalReasonIfNotSet(ServerPackManager.RemovalReason p_314504_) {
            if (this.removalReason == null) {
                this.removalReason = p_314504_;
            }
        }

        public boolean isRemoved() {
            return this.removalReason != null;
        }
    }
}
