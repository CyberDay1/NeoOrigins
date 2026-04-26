package net.minecraft.client.multiplayer;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class LevelLoadStatusManager {
    private final LocalPlayer player;
    private final ClientLevel level;
    private final LevelRenderer levelRenderer;
    private LevelLoadStatusManager.Status status = LevelLoadStatusManager.Status.WAITING_FOR_SERVER;

    public LevelLoadStatusManager(LocalPlayer p_304970_, ClientLevel p_304700_, LevelRenderer p_304864_) {
        this.player = p_304970_;
        this.level = p_304700_;
        this.levelRenderer = p_304864_;
    }

    public void tick() {
        switch (this.status) {
            case WAITING_FOR_PLAYER_CHUNK:
                BlockPos blockpos = this.player.blockPosition();
                boolean flag = this.level.isOutsideBuildHeight(blockpos.getY());
                if (flag || this.levelRenderer.isSectionCompiled(blockpos) || this.player.isSpectator() || !this.player.isAlive()) {
                    this.status = LevelLoadStatusManager.Status.LEVEL_READY;
                }
            case WAITING_FOR_SERVER:
            case LEVEL_READY:
        }
    }

    public boolean levelReady() {
        return this.status == LevelLoadStatusManager.Status.LEVEL_READY;
    }

    public void loadingPacketsReceived() {
        if (this.status == LevelLoadStatusManager.Status.WAITING_FOR_SERVER) {
            this.status = LevelLoadStatusManager.Status.WAITING_FOR_PLAYER_CHUNK;
        }
    }

    @OnlyIn(Dist.CLIENT)
    static enum Status {
        WAITING_FOR_SERVER,
        WAITING_FOR_PLAYER_CHUNK,
        LEVEL_READY;
    }
}
