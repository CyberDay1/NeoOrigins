package net.minecraft.client.resources.server;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.packs.DownloadQueue;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackDownloader {
    void download(Map<UUID, DownloadQueue.DownloadRequest> p_314430_, Consumer<DownloadQueue.BatchResult> p_314486_);
}
