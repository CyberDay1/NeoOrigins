package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.dto.RealmsServer;
import com.mojang.realmsclient.exception.RealmsServiceException;
import com.mojang.realmsclient.gui.screens.RealmsResetWorldScreen;
import com.mojang.realmsclient.util.WorldGenerationInfo;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class CreateSnapshotRealmTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.snapshot.creating");
    private final long parentId;
    private final WorldGenerationInfo generationInfo;
    private final String name;
    private final String description;
    private final RealmsMainScreen realmsMainScreen;
    @Nullable
    private RealmCreationTask creationTask;
    @Nullable
    private ResettingGeneratedWorldTask generateWorldTask;

    public CreateSnapshotRealmTask(RealmsMainScreen p_305775_, long p_306103_, WorldGenerationInfo p_305955_, String p_306018_, String p_306116_) {
        this.parentId = p_306103_;
        this.generationInfo = p_305955_;
        this.name = p_306018_;
        this.description = p_306116_;
        this.realmsMainScreen = p_305775_;
    }

    @Override
    public void run() {
        RealmsClient realmsclient = RealmsClient.create();

        try {
            RealmsServer realmsserver = realmsclient.createSnapshotRealm(this.parentId);
            this.creationTask = new RealmCreationTask(realmsserver.id, this.name, this.description);
            this.generateWorldTask = new ResettingGeneratedWorldTask(
                this.generationInfo,
                realmsserver.id,
                RealmsResetWorldScreen.CREATE_WORLD_RESET_TASK_TITLE,
                () -> Minecraft.getInstance().execute(() -> RealmsMainScreen.play(realmsserver, this.realmsMainScreen, true))
            );
            if (this.aborted()) {
                return;
            }

            this.creationTask.run();
            if (this.aborted()) {
                return;
            }

            this.generateWorldTask.run();
        } catch (RealmsServiceException realmsserviceexception) {
            LOGGER.error("Couldn't create snapshot world", (Throwable)realmsserviceexception);
            this.error(realmsserviceexception);
        } catch (Exception exception) {
            LOGGER.error("Couldn't create snapshot world", (Throwable)exception);
            this.error(exception);
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }

    @Override
    public void abortTask() {
        super.abortTask();
        if (this.creationTask != null) {
            this.creationTask.abortTask();
        }

        if (this.generateWorldTask != null) {
            this.generateWorldTask.abortTask();
        }
    }
}
