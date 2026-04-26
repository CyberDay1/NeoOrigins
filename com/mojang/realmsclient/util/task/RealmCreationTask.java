package com.mojang.realmsclient.util.task;

import com.mojang.logging.LogUtils;
import com.mojang.realmsclient.client.RealmsClient;
import com.mojang.realmsclient.exception.RealmsServiceException;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;

@OnlyIn(Dist.CLIENT)
public class RealmCreationTask extends LongRunningTask {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Component TITLE = Component.translatable("mco.create.world.wait");
    private final String name;
    private final String motd;
    private final long realmId;

    public RealmCreationTask(long p_320686_, String p_319849_, String p_320750_) {
        this.realmId = p_320686_;
        this.name = p_319849_;
        this.motd = p_320750_;
    }

    @Override
    public void run() {
        RealmsClient realmsclient = RealmsClient.create();

        try {
            realmsclient.initializeRealm(this.realmId, this.name, this.motd);
        } catch (RealmsServiceException realmsserviceexception) {
            LOGGER.error("Couldn't create world", (Throwable)realmsserviceexception);
            this.error(realmsserviceexception);
        } catch (Exception exception) {
            LOGGER.error("Could not create world", (Throwable)exception);
            this.error(exception);
        }
    }

    @Override
    public Component getTitle() {
        return TITLE;
    }
}
