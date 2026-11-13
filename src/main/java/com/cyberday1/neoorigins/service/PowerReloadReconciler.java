package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager.Definitions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Moves online players onto the new definitions when a datapack reload changes what a
 * power id they hold does. Without this a player kept the old definition's applied
 * effects until relog, and a later revoke ran the new definition's onRevoked, leaking
 * the old effect. A power whose definition is unchanged is not touched, so its state
 * (resource values, toggles, cooldowns) survives a plain {@code /reload}.
 */
public final class PowerReloadReconciler {

    private PowerReloadReconciler() {}

    private record LifecycleKey(UUID player, ResourceLocation power) {}
    private record LifecyclePass(Set<LifecycleKey> grants, Set<LifecycleKey> revokes) {}
    private static final ThreadLocal<LifecyclePass> PASS = new ThreadLocal<>();

    /** A parent may forward a hook or grant/revoke a child during reconciliation. Run each once. */
    public static boolean dispatchLifecycle(ServerPlayer player, ResourceLocation id, boolean grant) {
        LifecyclePass pass = PASS.get();
        return pass == null || (grant ? pass.grants() : pass.revokes()).add(new LifecycleKey(player.getUUID(), id));
    }

    private static Definitions before;
    private static Map<UUID, Set<ResourceLocation>> heldBefore;

    /** Called before a power reload replaces anything. Keeps the oldest pending snapshot. */
    public static synchronized void captureBeforeReload() {
        if (before != null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        Map<UUID, Set<ResourceLocation>> held = new HashMap<>();
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            held.put(sp.getUUID(), Set.copyOf(ActiveOriginService.activePowerIds(sp)));
        }
        before = PowerDataManager.INSTANCE.definitions();
        heldBefore = held;
    }

    /** Called once the reload has reached online players (datapack sync). */
    public static synchronized void afterReload(Collection<ServerPlayer> players) {
        if (before == null) return;
        try {
            Definitions after = PowerDataManager.INSTANCE.definitions();
            int total = 0;
            for (ServerPlayer sp : players) {
                Set<ResourceLocation> held = heldBefore.get(sp.getUUID());
                if (held == null) continue;
                int n = reconcile(sp, before, after, held, ActiveOriginService.activePowerIds(sp));
                if (n > 0) com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(sp);
                total += n;
            }
            NeoOrigins.LOGGER.info("Reload: re-applied {} held power definition(s) that changed", total);
        } finally {
            before = null;
            heldBefore = null;
        }
    }

    /**
     * Revokes through the old definition and grants through the new one for each held
     * id whose definition changed, once per id. A held id that lost its definition is
     * revoked; one that gained a definition is granted. Returns how many ids changed.
     */
    public static int reconcile(ServerPlayer player, Definitions before, Definitions after,
                                Set<ResourceLocation> heldBefore, Set<ResourceLocation> heldNow) {
        float maxHealthBefore = player.getMaxHealth();
        Set<ResourceLocation> changedIds = new java.util.LinkedHashSet<>();
        for (ResourceLocation id : heldBefore) {
            PowerHolder<?> old = before.holder(id);
            PowerHolder<?> now = after.holder(id);
            if (old == null && now == null) continue;
            if (now == null) {
                changedIds.add(id);
                continue;
            }
            // An id the player stopped holding (an origin dropped it) is not a definition change.
            if (!heldNow.contains(id)) continue;
            if (old == null) {
                changedIds.add(id);
                continue;
            }
            if (Objects.equals(before.fingerprint(id), after.fingerprint(id))) continue;
            changedIds.add(id);
        }
        Set<ResourceLocation> actualBefore = Set.copyOf(ActiveOriginService.activePowerIds(player));
        LifecyclePass previous = PASS.get();
        PASS.set(new LifecyclePass(new java.util.HashSet<>(), new java.util.HashSet<>()));
        try {
            // Finish old effects before applying any new effects. Nested API revocations
            // must see the old definitions too, not just EmbeddedPower.holder().
            com.cyberday1.neoorigins.data.EmbeddedPowerLoading.withGeneration(before, () -> {
                for (ResourceLocation id : changedIds) {
                    PowerHolder<?> old = before.holder(id);
                    if (old != null) old.onRevoked(player);
                }
            });
            for (ResourceLocation id : changedIds) {
                PowerHolder<?> now = after.holder(id);
                if (now == null || !heldNow.contains(id)) continue;
                // A parent's revoke may have ended its child grant. The parent's new
                // onGranted can recreate it; applying it again here would leak effects.
                if (actualBefore.contains(id) && !ActiveOriginService.activePowerIds(player).contains(id)) continue;
                now.onGranted(player);
            }
        } finally {
            if (previous == null) PASS.remove(); else PASS.set(previous);
        }
        int changed = changedIds.size();
        if (changed > 0) {
            ActiveOriginService.invalidate(player.getUUID());
            // Same health reconcile as an origin change: fill gained hearts, clamp lost ones.
            float maxHealthAfter = player.getMaxHealth();
            if (maxHealthAfter > maxHealthBefore) {
                player.setHealth(Math.min(player.getHealth() + (maxHealthAfter - maxHealthBefore), maxHealthAfter));
            } else if (player.getHealth() > maxHealthAfter) {
                player.setHealth(maxHealthAfter);
            }
        }
        return changed;
    }
}
