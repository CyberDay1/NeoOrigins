package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.power.PowerHolder;
import com.cyberday1.neoorigins.data.PowerDataManager;
import com.cyberday1.neoorigins.data.PowerDataManager.Definitions;
import net.minecraft.resources.Identifier;
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

    private static Definitions before;
    private static Map<UUID, Set<Identifier>> heldBefore;

    /** Called before a power reload replaces anything. Keeps the oldest pending snapshot. */
    public static synchronized void captureBeforeReload() {
        if (before != null) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList().getPlayers().isEmpty()) return;
        Map<UUID, Set<Identifier>> held = new HashMap<>();
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
                Set<Identifier> held = heldBefore.get(sp.getUUID());
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
                                Set<Identifier> heldBefore, Set<Identifier> heldNow) {
        float maxHealthBefore = player.getMaxHealth();
        int changed = 0;
        for (Identifier id : heldBefore) {
            PowerHolder<?> old = before.holder(id);
            PowerHolder<?> now = after.holder(id);
            if (old == null && now == null) continue;
            if (now == null) {
                old.onRevoked(player);
                changed++;
                continue;
            }
            // An id the player stopped holding (an origin dropped it) is not a definition change.
            if (!heldNow.contains(id)) continue;
            if (old == null) {
                now.onGranted(player);
                changed++;
                continue;
            }
            if (Objects.equals(before.fingerprint(id), after.fingerprint(id))) continue;
            old.onRevoked(player);
            now.onGranted(player);
            changed++;
        }
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
