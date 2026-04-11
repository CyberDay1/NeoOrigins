package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.service.MinionTracker;
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
        // Save-recovery safety net: if a player entered the NaN/Infinity
        // health state from the pre-1.3.1 ModifyDamagePower overflow bug
        // and we didn't catch it at login (e.g. it corrupted mid-session
        // from some other path), repair it here before any power tick
        // can read getHealth() and propagate the NaN further.
        repairCorruptedVitals(sp);

        CompatTickScheduler.tick(sp);
        MinionTracker.tick(sp);
        ActiveOriginService.forEach(sp, holder -> holder.onTick(sp));
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        // Repair the damage/NBT state BEFORE any handlers below read
        // vitals — pre-1.3.1 saves where a Feline (or any origin with
        // a misconfigured ModifyDamagePower) ran /kill have NaN
        // Health / AbsorptionAmount persisted to the player .dat file,
        // which prevents them from ever dying and effectively bricks
        // the world. This runs once on every login as a no-op for
        // healthy players.
        repairCorruptedVitals(sp);

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) { needsOrigin = true; break; }
        }
        ActiveOriginService.forEach(sp, holder -> holder.onLogin(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
        if (needsOrigin) NeoOriginsNetwork.openSelectionScreen(sp, false);
    }

    /**
     * Clamp any non-finite Health/AbsorptionAmount back to sane values.
     * Introduced in 1.3.1 as a save-recovery path for players whose
     * worlds were bricked by the pre-1.3.1 ModifyDamagePower overflow
     * bug (Float.MAX_VALUE * multiplier &gt; 1.0 → +Infinity → NaN
     * health via vanilla's absorption math → never dies).
     */
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
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
        NeoOriginsNetwork.syncToPlayer(sp);
    }
}
