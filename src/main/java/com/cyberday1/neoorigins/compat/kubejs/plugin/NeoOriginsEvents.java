package com.cyberday1.neoorigins.compat.kubejs.plugin;

import com.cyberday1.neoorigins.compat.kubejs.plugin.events.*;
import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;

/**
 * The {@code NeoOriginsEvents} event group exposed to KubeJS scripts.
 *
 * <p>This class is only classloaded when KubeJS is present at runtime
 * (the {@link com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge}
 * guards every callsite with {@code KubeJSCompat.isLoaded()} before
 * touching anything in this package).
 *
 * <p>Each handler is registered as a server-side event — origin/power/mob
 * state changes only happen on the logical server.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.originChosen(event => { ... })
 *   NeoOriginsEvents.powerActivated(event => { ... })
 *   NeoOriginsEvents.mountStarted(event => { ... })
 * </pre>
 */
public final class NeoOriginsEvents {

    private NeoOriginsEvents() {}

    /** The event group all NeoOrigins KubeJS events register under. */
    public static final EventGroup GROUP = EventGroup.of("NeoOriginsEvents");

    // ── Origin lifecycle ────────────────────────────────────────────────
    public static final EventHandler ORIGIN_CHOSEN =
        GROUP.server("originChosen", () -> OriginChosenKubeEvent.class);
    public static final EventHandler ORIGIN_CHANGED =
        GROUP.server("originChanged", () -> OriginChangedKubeEvent.class);
    public static final EventHandler EVOLUTION_TIER_CHANGED =
        GROUP.server("evolutionTierChanged", () -> EvolutionTierChangedKubeEvent.class);
    public static final EventHandler EVOLUTION_DECLINED =
        GROUP.server("evolutionDeclined", () -> EvolutionDeclinedKubeEvent.class);

    // ── Power lifecycle ─────────────────────────────────────────────────
    public static final EventHandler POWER_GRANTED =
        GROUP.server("powerGranted", () -> PowerGrantedKubeEvent.class);
    public static final EventHandler POWER_REVOKED =
        GROUP.server("powerRevoked", () -> PowerRevokedKubeEvent.class);
    public static final EventHandler POWER_ACTIVATED =
        GROUP.server("powerActivated", () -> PowerActivatedKubeEvent.class);
    /** High-frequency — gated by {@link EventHandler#hasListeners()} at the bridge. */
    public static final EventHandler POWER_TICK =
        GROUP.server("powerTick", () -> PowerTickKubeEvent.class);

    // ── Mob origin ──────────────────────────────────────────────────────
    public static final EventHandler MOB_ORIGIN_ASSIGNED =
        GROUP.server("mobOriginAssigned", () -> MobOriginAssignedKubeEvent.class);
    public static final EventHandler MOB_ORIGIN_CLEARED =
        GROUP.server("mobOriginCleared", () -> MobOriginClearedKubeEvent.class);

    // ── Mount / consent ─────────────────────────────────────────────────
    public static final EventHandler MOUNT_REQUESTED =
        GROUP.server("mountRequested", () -> MountRequestedKubeEvent.class);
    public static final EventHandler MOUNT_ACCEPTED =
        GROUP.server("mountAccepted", () -> MountAcceptedKubeEvent.class);
    public static final EventHandler MOUNT_DECLINED =
        GROUP.server("mountDeclined", () -> MountDeclinedKubeEvent.class);
    public static final EventHandler MOUNT_STARTED =
        GROUP.server("mountStarted", () -> MountStartedKubeEvent.class);
    public static final EventHandler MOUNT_ENDED =
        GROUP.server("mountEnded", () -> MountEndedKubeEvent.class);
}
