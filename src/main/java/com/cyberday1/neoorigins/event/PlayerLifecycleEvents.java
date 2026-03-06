package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class PlayerLifecycleEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CompatTickScheduler.tick(sp);
        ActiveOriginService.forEach(sp, holder -> holder.onTick(sp));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) { needsOrigin = true; break; }
        }
        ActiveOriginService.forEach(sp, holder -> holder.onLogin(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
        if (needsOrigin) NeoOriginsNetwork.openSelectionScreen(sp, false);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
    }
}
