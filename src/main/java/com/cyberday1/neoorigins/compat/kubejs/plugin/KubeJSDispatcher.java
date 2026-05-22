package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.compat.kubejs.plugin.events.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * On-demand dispatcher that translates plain-Java fire calls from
 * {@link com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge} into
 * KubeJS event posts.
 *
 * <p>This class is only classloaded when one of its static methods is
 * first invoked. The bridge guards every call with
 * {@code KubeJSCompat.isLoaded()}, so this class is never touched when
 * KubeJS is absent — keeping KubeJS API references out of the always-
 * loaded path.
 */
public final class KubeJSDispatcher {

    private KubeJSDispatcher() {}

    // ── Origin lifecycle ────────────────────────────────────────────────

    public static void originChosen(ServerPlayer player, ResourceLocation layerId, ResourceLocation originId) {
        NeoOriginsEvents.ORIGIN_CHOSEN.post(new OriginChosenKubeEvent(player, layerId, originId));
    }

    public static void originChanged(ServerPlayer player, ResourceLocation layerId,
                                     ResourceLocation oldOriginId, ResourceLocation newOriginId) {
        NeoOriginsEvents.ORIGIN_CHANGED.post(new OriginChangedKubeEvent(player, layerId, oldOriginId, newOriginId));
    }

    public static void evolutionTierChanged(ServerPlayer player, int oldTier, int newTier) {
        NeoOriginsEvents.EVOLUTION_TIER_CHANGED.post(new EvolutionTierChangedKubeEvent(player, oldTier, newTier));
    }

    public static void evolutionDeclined(ServerPlayer player) {
        NeoOriginsEvents.EVOLUTION_DECLINED.post(new EvolutionDeclinedKubeEvent(player));
    }

    // ── Power lifecycle ─────────────────────────────────────────────────

    public static void powerGranted(ServerPlayer player, ResourceLocation powerId) {
        NeoOriginsEvents.POWER_GRANTED.post(new PowerGrantedKubeEvent(player, powerId));
    }

    public static void powerRevoked(ServerPlayer player, ResourceLocation powerId) {
        NeoOriginsEvents.POWER_REVOKED.post(new PowerRevokedKubeEvent(player, powerId));
    }

    public static void powerActivated(ServerPlayer player, ResourceLocation powerId) {
        NeoOriginsEvents.POWER_ACTIVATED.post(new PowerActivatedKubeEvent(player, powerId));
    }

    public static void powerTick(ServerPlayer player, ResourceLocation powerId) {
        NeoOriginsEvents.POWER_TICK.post(new PowerTickKubeEvent(player, powerId));
    }

    /** Opt-in gate for the high-frequency power_tick event. */
    public static boolean powerTickHasListeners() {
        return NeoOriginsEvents.POWER_TICK.hasListeners();
    }

    // ── Mob origin ──────────────────────────────────────────────────────

    public static void mobOriginAssigned(LivingEntity mob, ResourceLocation originId) {
        NeoOriginsEvents.MOB_ORIGIN_ASSIGNED.post(new MobOriginAssignedKubeEvent(mob, originId));
    }

    public static void mobOriginCleared(LivingEntity mob, ResourceLocation previousOriginId) {
        NeoOriginsEvents.MOB_ORIGIN_CLEARED.post(new MobOriginClearedKubeEvent(mob, previousOriginId));
    }

    // ── Mount / consent ─────────────────────────────────────────────────

    public static void mountRequested(ServerPlayer requester, ServerPlayer target) {
        NeoOriginsEvents.MOUNT_REQUESTED.post(new MountRequestedKubeEvent(requester, target));
    }

    public static void mountAccepted(ServerPlayer requester, ServerPlayer target) {
        NeoOriginsEvents.MOUNT_ACCEPTED.post(new MountAcceptedKubeEvent(requester, target));
    }

    public static void mountDeclined(ServerPlayer requester, ServerPlayer target) {
        NeoOriginsEvents.MOUNT_DECLINED.post(new MountDeclinedKubeEvent(requester, target));
    }

    public static void mountStarted(ServerPlayer rider, LivingEntity vehicle, String position) {
        NeoOriginsEvents.MOUNT_STARTED.post(new MountStartedKubeEvent(rider, vehicle, position));
    }

    public static void mountEnded(ServerPlayer rider, Entity vehicle) {
        NeoOriginsEvents.MOUNT_ENDED.post(new MountEndedKubeEvent(rider, vehicle));
    }
}
