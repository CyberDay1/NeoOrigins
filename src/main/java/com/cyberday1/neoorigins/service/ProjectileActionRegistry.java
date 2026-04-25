package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.compat.action.EntityAction;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Session-scoped registry of deferred actions keyed by projectile UUID.
 *
 * <p>When a {@code neoorigins:spawn_projectile} action fires with an
 * {@code on_hit_action} configured, the action is stored here against the
 * spawned projectile's UUID. On {@link net.neoforged.neoforge.event.entity.ProjectileImpactEvent}
 * the registered action is drained and invoked with an impact-position override
 * installed in {@link ActionContextHolder}.
 *
 * <p>Entries are drained on impact. A periodic cleanup sweeps entries older
 * than {@link #EXPIRY_TICKS} so long-lived / never-hit projectiles don't
 * pin actions in memory forever.
 */
public final class ProjectileActionRegistry {

    /** Max projectile lifetime we track — 60 seconds. Anything uncollected after that is orphaned. */
    private static final long EXPIRY_TICKS = 20L * 60L;

    private record Pending(EntityAction action, long registeredAtTick) {}

    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();

    private ProjectileActionRegistry() {}

    /** Register an {@code on_hit_action} for a newly spawned projectile. */
    public static void register(UUID projectileUuid, EntityAction action, long serverTick) {
        PENDING.put(projectileUuid, new Pending(action, serverTick));
    }

    /** Drain and return the action registered for {@code projectileUuid}, if any. */
    public static EntityAction drain(UUID projectileUuid) {
        Pending p = PENDING.remove(projectileUuid);
        return p == null ? null : p.action;
    }

    /**
     * Evict entries older than {@link #EXPIRY_TICKS}. Called periodically from
     * the server-tick loop — no-op if the map is empty.
     */
    public static void sweep(long currentTick) {
        if (PENDING.isEmpty()) return;
        PENDING.entrySet().removeIf(e -> currentTick - e.getValue().registeredAtTick > EXPIRY_TICKS);
    }

    /** For diagnostics. */
    public static int size() { return PENDING.size(); }
}
