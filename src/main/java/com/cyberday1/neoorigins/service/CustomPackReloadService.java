package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.repository.PackRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Triggers a datapack reload after {@link CustomPackWriter} has written, then
 * re-syncs online players so the new/edited origin is immediately selectable.
 *
 * <p>This is the deliberate "Apply" action (the locked UX: Save writes files,
 * a separate Apply reloads — author controls the visible hitch and can batch
 * edits). No programmatic reload helper existed before 2.1; this uses the
 * vanilla mechanism ({@code PackRepository#reload} to rediscover the world
 * datapack, then {@code MinecraftServer#reloadResources} with our pack added
 * to the selected set), the same path {@code /reload} drives.
 */
public final class CustomPackReloadService {

    /** Vanilla world-datapack id prefix; folder name is OriginDraft.CUSTOM_NAMESPACE. */
    private static final String CUSTOM_PACK_ID = "file/neoorigins_custom";

    private CustomPackReloadService() {}

    /**
     * @return a future completing (on the server thread) once resources have
     *         reloaded and players are re-synced.
     */
    public static CompletableFuture<Void> reload(MinecraftServer server) {
        PackRepository repo = server.getPackRepository();
        // Synchronous filesystem rescan on the server thread. This is a
        // deliberate, bounded hitch: it is the explicit author-driven "Apply"
        // action (same cost/behavior as a manual /reload), throttled and
        // single-flighted by NeoOriginsNetwork's RELOAD_IN_FLIGHT guard, so a
        // brief stall here is acceptable and intended — not a per-tick path.
        repo.reload(); // rediscover world/datapacks/* incl. our freshly-written pack

        List<String> ids = new ArrayList<>(repo.getSelectedIds());
        if (!ids.contains(CUSTOM_PACK_ID) && repo.getAvailableIds().contains(CUSTOM_PACK_ID)) {
            ids.add(CUSTOM_PACK_ID);
        } else if (!repo.getAvailableIds().contains(CUSTOM_PACK_ID)) {
            NeoOrigins.LOGGER.warn("[creator] '{}' not discovered after write — "
                + "reloading without it", CUSTOM_PACK_ID);
        }

        return server.reloadResources(ids).thenRunAsync(() -> {
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                NeoOriginsNetwork.syncRegistryToPlayer(sp);
                NeoOriginsNetwork.syncToPlayer(sp);
            }
            NeoOrigins.LOGGER.info("[creator] reload complete, re-synced {} player(s)",
                server.getPlayerList().getPlayers().size());
        }, server);
    }
}
