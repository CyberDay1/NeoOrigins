package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.NeoOriginsConfig.RandomMode;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.MinionTracker;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class PlayerLifecycleEvents {

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        repairCorruptedVitals(sp);

        CompatTickScheduler.tick(sp);
        MinionTracker.tick(sp);
        ActiveOriginService.forEach(sp, holder -> holder.onTick(sp));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        repairCorruptedVitals(sp);

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) { needsOrigin = true; break; }
        }
        ActiveOriginService.forEach(sp, holder -> holder.onLogin(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
        if (needsOrigin) {
            if (NeoOriginsConfig.getRandomMode() == RandomMode.FIRST_JOIN) {
                assignRandomOrigins(sp);
            } else {
                NeoOriginsNetwork.openSelectionScreen(sp, false);
            }
        }
    }

    private static void repairCorruptedVitals(ServerPlayer sp) {
        if (!Float.isFinite(sp.getHealth())) {
            NeoOrigins.LOGGER.warn(
                "Repairing corrupted Health on {} ({}): was {}, resetting to max",
                sp.getName().getString(), sp.getUUID(), sp.getHealth());
            sp.setHealth(sp.getMaxHealth());
        }
        if (!Float.isFinite(sp.getAbsorptionAmount())) {
            NeoOrigins.LOGGER.warn(
                "Repairing corrupted AbsorptionAmount on {} ({}): was {}, resetting to 0",
                sp.getName().getString(), sp.getUUID(), sp.getAbsorptionAmount());
            sp.setAbsorptionAmount(0.0f);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        var uuid = sp.getUUID();
        CompatTickScheduler.clearPlayer(uuid);
        NeoOriginsNetwork.clearDebounce(uuid);
        MinionTracker.clearAll(uuid);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (NeoOriginsConfig.getRandomMode() == RandomMode.EVERY_DEATH) {
            ActiveOriginService.revokeAllPowers(sp);
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.clear();
            assignRandomOrigins(sp);
        } else {
            ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
            NeoOriginsNetwork.syncToPlayer(sp);
        }
    }

    private static void assignRandomOrigins(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        List<String> assigned = new ArrayList<>();

        for (OriginLayer layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            Identifier layerId = layer.id();
            if (data.hasOriginForLayer(layerId)) continue;

            List<Identifier> available = layer.getAvailableOriginIds().stream()
                .filter(OriginDataManager.INSTANCE::hasOrigin)
                .toList();
            if (available.isEmpty()) continue;

            Identifier picked = available.get(sp.getRandom().nextInt(available.size()));
            data.setOrigin(layerId, picked);
            ActiveOriginService.applyOriginPowers(sp, layerId, null, picked);
            assigned.add(picked.toString());
        }

        data.setHadAllOrigins(true);
        NeoOriginsNetwork.syncToPlayer(sp);
        NeoOrigins.LOGGER.info("Randomly assigned origins to {}: {}",
            sp.getName().getString(), String.join(", ", assigned));
    }
}
