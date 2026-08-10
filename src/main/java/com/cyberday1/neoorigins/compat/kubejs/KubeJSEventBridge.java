package com.cyberday1.neoorigins.compat.kubejs;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Always-loaded facade for firing NeoOrigins events to KubeJS scripts.
 *
 * <p>This class has <strong>no KubeJS imports</strong>. Every fire method
 * short-circuits on {@link KubeJSCompat#isLoaded()} before delegating to
 * {@code com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher},
 * which is only classloaded once the guard passes. As a result, mods
 * running without KubeJS pay only a single cached boolean check per fire
 * site — no reflection, no class-not-found risk.
 *
 * <p>Callsites in core NeoOrigins code should always call into this
 * bridge, never directly reach into the {@code plugin/} sub-package.
 */
public final class KubeJSEventBridge {

    private KubeJSEventBridge() {}

    // ── Origin lifecycle ────────────────────────────────────────────────

    public static void fireOriginChosen(ServerPlayer player, Identifier layerId, Identifier originId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.originChosen(player, layerId, originId);
    }

    public static void fireOriginChanged(ServerPlayer player, Identifier layerId,
                                         Identifier oldOriginId, Identifier newOriginId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.originChanged(player, layerId, oldOriginId, newOriginId);
    }

    public static void fireEvolutionTierChanged(ServerPlayer player, int oldTier, int newTier) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.evolutionTierChanged(player, oldTier, newTier);
    }

    public static void fireEvolutionDeclined(ServerPlayer player) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.evolutionDeclined(player);
    }

    // ── Power lifecycle ─────────────────────────────────────────────────

    public static void firePowerGranted(ServerPlayer player, Identifier powerId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerGranted(player, powerId);
    }

    public static void firePowerRevoked(ServerPlayer player, Identifier powerId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerRevoked(player, powerId);
    }

    public static void firePowerActivated(ServerPlayer player, Identifier powerId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerActivated(player, powerId);
    }

    public static void firePowerTick(ServerPlayer player, Identifier powerId) {
        if (!KubeJSCompat.isLoaded()) return;
        // Opt-in gate: skip the event allocation entirely if nothing's listening.
        if (!com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerTickHasListeners()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerTick(player, powerId);
    }

    /**
     * Fast pre-check for callers that want to skip the per-tick fire-site work
     * (e.g., gathering powerId) when no JS listener exists. Safe to call
     * without KubeJS — short-circuits to {@code false}.
     */
    public static boolean powerTickHasListeners() {
        if (!KubeJSCompat.isLoaded()) return false;
        return com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.powerTickHasListeners();
    }

    // ── Mob origin ──────────────────────────────────────────────────────

    public static void fireMobOriginAssigned(LivingEntity mob, Identifier originId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mobOriginAssigned(mob, originId);
    }

    public static void fireMobOriginCleared(LivingEntity mob, Identifier previousOriginId) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mobOriginCleared(mob, previousOriginId);
    }

    // ── Mount / consent ─────────────────────────────────────────────────

    public static void fireMountRequested(ServerPlayer requester, ServerPlayer target) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mountRequested(requester, target);
    }

    public static void fireMountAccepted(ServerPlayer requester, ServerPlayer target) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mountAccepted(requester, target);
    }

    public static void fireMountDeclined(ServerPlayer requester, ServerPlayer target) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mountDeclined(requester, target);
    }

    public static void fireMountStarted(ServerPlayer rider, LivingEntity vehicle, String position) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mountStarted(rider, vehicle, position);
    }

    public static void fireMountEnded(ServerPlayer rider, Entity vehicle) {
        if (!KubeJSCompat.isLoaded()) return;
        com.cyberday1.neoorigins.compat.kubejs.plugin.KubeJSDispatcher.mountEnded(rider, vehicle);
    }
}
