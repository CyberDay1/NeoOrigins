package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.event.PowerGrantedEvent;
import com.cyberday1.neoorigins.api.event.PowerRevokedEvent;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import com.cyberday1.neoorigins.data.GlobalPowerSetDataManager;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;

/**
 * Applies {@link com.cyberday1.neoorigins.api.global_power.GlobalPowerSet global
 * power sets} (Apoli {@code apoli:global} port) to players and mobs.
 *
 * <p><b>Players</b> — {@link #reconcilePlayer(ServerPlayer)} is idempotent: it
 * diffs the player's persisted global-grant ledger against the currently-matching
 * global powers, granting newly-added powers and revoking removed ones. It runs on
 * login and on datapack-sync ({@code /reload}). Granted powers flow through the
 * same dynamic-grant pipeline as the {@code grant_power} action, so they activate
 * via {@link ActiveOriginService}, persist across respawn, and survive reloads.
 *
 * <p><b>Mobs</b> — {@link #applyMobGlobalPowers(LivingEntity)} is one-shot at
 * spawn (FinalizeSpawn). Only powers whose type {@code appliesToMobs()} are
 * applied, via {@link PowerHolder#applyToMob}.
 */
public final class GlobalPowerService {

    private GlobalPowerService() {}

    /**
     * Bring the player's global-power grants in line with the loaded global power
     * sets. Newly-matching powers are granted (and fire {@code onGranted}); powers
     * no longer offered by any matching set are revoked (and fire {@code onRevoked},
     * unless still provided by one of the player's origins).
     */
    public static void reconcilePlayer(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());

        List<Identifier> desired = GlobalPowerSetDataManager.INSTANCE.playerPowersOrdered();
        boolean changed = false;

        // Revoke powers the ledger owns but that are no longer desired.
        for (Identifier powerId : new ArrayList<>(data.getGlobalGrantedPowers())) {
            if (desired.contains(powerId)) continue;
            data.removeGlobalGrant(powerId);
            // Only drop the active dynamic grant / fire onRevoked if no origin
            // still supplies this power (mirrors revoke_power semantics).
            if (!isProvidedByOrigin(player, data, powerId)) {
                PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
                if (data.removeDynamicGrant(powerId) && holder != null) {
                    holder.onRevoked(player);
                    NeoForge.EVENT_BUS.post(new PowerRevokedEvent(player, powerId));
                }
            } else {
                // Power survives via an origin — just relinquish the dynamic flag.
                data.removeDynamicGrant(powerId);
            }
            changed = true;
        }

        // Grant newly-desired powers (in order).
        for (Identifier powerId : desired) {
            data.addGlobalGrant(powerId);
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null) {
                NeoOrigins.LOGGER.warn("[global-power] player set references unknown power '{}'", powerId);
                continue;
            }
            // Already provided by an origin? Record the dynamic flag without
            // re-firing onGranted (matches grant_power's fromOrigin path).
            if (isProvidedByOrigin(player, data, powerId)) {
                if (data.addDynamicGrant(powerId)) changed = true;
                continue;
            }
            if (data.addDynamicGrant(powerId)) {
                holder.onGranted(player);
                NeoForge.EVENT_BUS.post(new PowerGrantedEvent(player, powerId));
                changed = true;
            }
        }

        if (changed) {
            if (player.getHealth() > player.getMaxHealth()) {
                player.setHealth(player.getMaxHealth());
            }
            NeoOriginsNetwork.syncToPlayer(player);
        }
    }

    private static boolean isProvidedByOrigin(ServerPlayer player, PlayerOriginData data, Identifier powerId) {
        for (var entry : data.getOrigins().entrySet()) {
            var origin = OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin != null && origin.powers().contains(powerId)) return true;
        }
        return false;
    }

    /**
     * Apply mob-applicable global powers to a freshly-spawned mob (FinalizeSpawn).
     * Only powers whose type {@code appliesToMobs()} are applied; everything else
     * is silently skipped, mirroring {@link MobOriginService}.
     */
    public static void applyMobGlobalPowers(LivingEntity mob) {
        List<Identifier> powers =
            GlobalPowerSetDataManager.INSTANCE.mobPowersOrdered(mob.getType());
        if (powers.isEmpty()) return;
        for (Identifier powerId : powers) {
            PowerHolder<?> holder = PowerDataManager.INSTANCE.getPower(powerId);
            if (holder == null || !holder.appliesToMobs()) continue;
            try {
                holder.applyToMob(mob);
            } catch (RuntimeException e) {
                NeoOrigins.LOGGER.error("[global-power] applyToMob failed for {} on {}",
                    powerId, mob.getType(), e);
            }
        }
    }
}
