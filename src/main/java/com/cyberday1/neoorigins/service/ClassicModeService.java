package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Applies {@code content.toml}'s {@code classic_mode} to the live origin registry.
 *
 * <p>The SERVER spec loads after the boot-time datapack reload, so the first
 * {@code OriginDataManager.apply()} always sees classic mode off. The spec's
 * Loading event (server start) and Reloading event (the file edited while the
 * server runs) land here and swap the classic variants in or out. A mid-session
 * swap also reconciles every online player's powers and resyncs the registry, so
 * the picker and the info screen match without a relog.
 */
public final class ClassicModeService {

    private ClassicModeService() {}

    public static void onConfigLoading(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == ContentTogglesConfig.SPEC) schedule();
    }

    public static void onConfigReloading(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == ContentTogglesConfig.SPEC) schedule();
    }

    private static void schedule() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        // No server: a remote client receiving the synced spec. Its registry is the
        // server's synced copy, which already reflects the server's classic mode.
        if (server == null) return;
        // Config events can fire off the server thread (the file watcher), so hop on.
        server.execute(() -> apply(server));
    }

    static void apply(MinecraftServer server) {
        var players = server.getPlayerList().getPlayers();
        Map<UUID, Map<Identifier, Origin>> before = new HashMap<>();
        for (ServerPlayer sp : players) before.put(sp.getUUID(), ActiveOriginService.snapshotDefinitions(sp));

        boolean changed = OriginDataManager.INSTANCE.rebuildEffective();
        // Resend the registry even when no definition changed: the edit may have
        // flipped an origin or class toggle, and the picker must match.
        for (ServerPlayer sp : players) {
            if (changed) ActiveOriginService.reconcileDefinitionChange(sp, before.get(sp.getUUID()));
            NeoOriginsNetwork.syncRegistryToPlayer(sp);
            NeoOriginsNetwork.syncToPlayer(sp);
        }
        if (changed && !players.isEmpty()) {
            NeoOrigins.LOGGER.info("Classic mode {}: reconciled powers for {} online player(s)",
                ContentTogglesConfig.isClassicMode() ? "on" : "off", players.size());
        }
    }
}
