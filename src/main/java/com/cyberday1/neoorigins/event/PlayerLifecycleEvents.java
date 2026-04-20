package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.NeoOriginsConfig.RandomMode;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.compat.CompatPlayerState;
import com.cyberday1.neoorigins.compat.CompatTickScheduler;
import com.cyberday1.neoorigins.data.LayerDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.api.origin.OriginLayer;
import com.cyberday1.neoorigins.api.origin.OriginUpgrade;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import com.cyberday1.neoorigins.power.builtin.ActiveGravityWellPower;
import com.cyberday1.neoorigins.service.MinionTracker;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public class PlayerLifecycleEvents {

    /** Grace period (in ticks) after login to retry the origin check if data wasn't loaded yet. */
    private static final int LOGIN_RETRY_TICKS = 100;
    private static final java.util.Map<java.util.UUID, Integer> pendingOriginCheck = new java.util.HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        repairCorruptedVitals(sp);

        // Retry origin check for players who logged in before data was loaded
        Integer remaining = pendingOriginCheck.get(sp.getUUID());
        if (remaining != null) {
            if (LayerDataManager.INSTANCE.getSortedLayers().isEmpty()) {
                if (remaining <= 0) {
                    pendingOriginCheck.remove(sp.getUUID());
                } else {
                    pendingOriginCheck.put(sp.getUUID(), remaining - 1);
                }
            } else {
                pendingOriginCheck.remove(sp.getUUID());
                checkAndPromptOrigin(sp);
            }
        }

        // Drain deferred re-sync after respawn
        Integer resyncRemaining = pendingResync.get(sp.getUUID());
        if (resyncRemaining != null) {
            if (resyncRemaining <= 0) {
                pendingResync.remove(sp.getUUID());
                NeoOriginsNetwork.syncToPlayer(sp);
            } else {
                pendingResync.put(sp.getUUID(), resyncRemaining - 1);
            }
        }

        CompatTickScheduler.tick(sp);
        MinionTracker.tick(sp);
        ActiveGravityWellPower.tickWells();
        ActiveOriginService.forEach(sp, holder -> holder.onTick(sp));
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.TICK);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        repairCorruptedVitals(sp);

        ActiveOriginService.forEach(sp, holder -> holder.onLogin(sp));
        NeoOriginsNetwork.syncRegistryToPlayer(sp);
        NeoOriginsNetwork.syncToPlayer(sp);

        if (LayerDataManager.INSTANCE.getSortedLayers().isEmpty()) {
            // Data hasn't loaded yet — defer the origin check to tick handler
            pendingOriginCheck.put(sp.getUUID(), LOGIN_RETRY_TICKS);
        } else {
            checkAndPromptOrigin(sp);
        }
    }

    private static void checkAndPromptOrigin(ServerPlayer sp) {
        PlayerOriginData data = sp.getData(OriginAttachments.originData());

        // If the player has any stored origins, they are a returning player — don't
        // force a full re-selection. This covers both the hadAllOrigins flag and legacy
        // players from before the flag was set during manual GUI selection.
        if (data.isHadAllOrigins() || !data.getOrigins().isEmpty()) {
            // Backfill the flag for legacy saves so future checks are fast
            if (!data.isHadAllOrigins() && !data.getOrigins().isEmpty()) {
                data.setHadAllOrigins(true);
            }
            return;
        }

        boolean needsOrigin = false;
        for (var layer : LayerDataManager.INSTANCE.getSortedLayers()) {
            if (!data.hasOriginForLayer(layer.id())) {
                NeoOrigins.LOGGER.debug("Player {} needs origin for layer {} (stored: {})",
                    sp.getName().getString(), layer.id(), data.getOrigins().keySet());
                needsOrigin = true;
                break;
            }
        }
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
        pendingOriginCheck.remove(uuid);
        pendingResync.remove(uuid);
        CompatTickScheduler.clearPlayer(uuid);
        CompatPlayerState.removePlayer(uuid);
        NeoOriginsNetwork.clearDebounce(uuid);
        MinionTracker.clearAll(uuid);
        ActiveOriginService.invalidate(uuid);
        com.cyberday1.neoorigins.service.EventPowerIndex.invalidate(uuid);
    }

    /**
     * Dimension restrictions filter the active-powers map, so any dimension
     * transition invalidates the client's mirror. Push a fresh sync.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        NeoOriginsNetwork.syncActivePowersToPlayer(sp);
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.DIMENSION_CHANGE);
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        // Clear per-slot activation debounce — the new ServerPlayer's tickCount
        // resets to 0 on respawn, so any stored "last activate tick" from before
        // death would block all skill activations until the new tickCount caught
        // up (potentially tens of minutes).
        NeoOriginsNetwork.clearDebounce(sp.getUUID());
        if (NeoOriginsConfig.getRandomMode() == RandomMode.EVERY_DEATH) {
            ActiveOriginService.revokeAllPowers(sp);
            PlayerOriginData data = sp.getData(OriginAttachments.originData());
            data.clear();
            assignRandomOrigins(sp);
        } else {
            ActiveOriginService.forEach(sp, holder -> holder.onRespawn(sp));
            NeoOriginsNetwork.syncToPlayer(sp);
        }
        com.cyberday1.neoorigins.service.EventPowerIndex.dispatch(
            sp, com.cyberday1.neoorigins.service.EventPowerIndex.Event.RESPAWN);
        // Deferred re-sync: the client may not be ready for packets at respawn time,
        // causing the HUD/info to show stale state until relog.
        pendingResync.put(sp.getUUID(), 2);
    }

    /** Players awaiting a deferred origin re-sync after respawn (UUID → ticks remaining). */
    private static final java.util.Map<java.util.UUID, Integer> pendingResync = new java.util.HashMap<>();

    /**
     * Applies datapack-defined origin upgrades when a player earns an advancement.
     * Each origin can declare {@code upgrades: [{advancement, origin, announcement}]}
     * in its JSON; when the referenced advancement fires and the player is currently
     * that origin on some layer, we swap them to the target origin on the same layer.
     *
     * <p>Runs every origin-layer the player currently has — so the same advancement
     * can drive different swaps on different layers (e.g. an origin evolution on the
     * origin layer and an unrelated class promotion on the class layer).
     */
    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Identifier earnedId = event.getAdvancement().id();

        PlayerOriginData data = sp.getData(OriginAttachments.originData());
        // Snapshot before iteration — applyOriginPowers mutates the data map.
        var snapshot = new java.util.ArrayList<>(data.getOrigins().entrySet());
        for (var entry : snapshot) {
            Identifier layerId = entry.getKey();
            Identifier currentOriginId = entry.getValue();
            Origin origin = OriginDataManager.INSTANCE.getOrigin(currentOriginId);
            if (origin == null) continue;

            for (OriginUpgrade upgrade : origin.upgrades()) {
                if (!earnedId.equals(upgrade.advancement())) continue;
                if (!OriginDataManager.INSTANCE.hasOrigin(upgrade.origin())) {
                    NeoOrigins.LOGGER.warn(
                        "Origin upgrade from {} references unknown target origin {} — skipping",
                        currentOriginId, upgrade.origin());
                    continue;
                }

                data.setOrigin(layerId, upgrade.origin());
                ActiveOriginService.applyOriginPowers(sp, layerId, currentOriginId, upgrade.origin());
                NeoOriginsNetwork.syncToPlayer(sp);

                String announcement = upgrade.announcement();
                if (announcement != null && !announcement.isEmpty()) {
                    sp.sendSystemMessage(Component.translatable(announcement));
                }

                NeoOrigins.LOGGER.info(
                    "Upgraded {}'s origin on layer {}: {} → {} (via advancement {})",
                    sp.getName().getString(), layerId, currentOriginId, upgrade.origin(), earnedId);
                // One upgrade per layer per advancement — if authors want chains,
                // they should use distinct advancements.
                break;
            }
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
