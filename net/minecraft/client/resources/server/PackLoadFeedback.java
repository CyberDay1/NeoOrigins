package net.minecraft.client.resources.server;

import java.util.UUID;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface PackLoadFeedback {
    void reportUpdate(UUID p_315007_, PackLoadFeedback.Update p_314979_);

    void reportFinalResult(UUID p_314623_, PackLoadFeedback.FinalResult p_314920_);

    @OnlyIn(Dist.CLIENT)
    public static enum FinalResult {
        DECLINED,
        APPLIED,
        DISCARDED,
        DOWNLOAD_FAILED,
        ACTIVATION_FAILED;
    }

    @OnlyIn(Dist.CLIENT)
    public static enum Update {
        ACCEPTED,
        DOWNLOADED;
    }
}
