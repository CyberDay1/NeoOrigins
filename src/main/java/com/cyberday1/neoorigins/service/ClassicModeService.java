package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.api.origin.Origin;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.config.ContentTogglesConfig;
import com.cyberday1.neoorigins.data.OriginDataManager;
import com.cyberday1.neoorigins.network.NeoOriginsNetwork;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 *
 * <p>Classic mode does not take away an origin a player already holds. Instead,
 * a player holding a built-in origin or class it hides is told so in chat, once
 * per join and once when the mode is switched on live, and switching it on in a
 * world that already has player data logs one server warning.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
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
        Map<UUID, Map<ResourceLocation, Origin>> before = new HashMap<>();
        for (ServerPlayer sp : players) before.put(sp.getUUID(), ActiveOriginService.snapshotDefinitions(sp));

        boolean changed = OriginDataManager.INSTANCE.rebuildEffective();
        boolean switchedOn = changed && ContentTogglesConfig.isClassicMode();
        if (switchedOn && worldHasPlayerData(server)) {
            NeoOrigins.LOGGER.warn("[NeoOrigins] Classic mode is on in a world that already has players. "
                + "Anyone holding an origin or class that classic mode hides keeps it until it is reset "
                + "with an orb or /neoorigins reset.");
        }
        // Resend the registry even when no definition changed: the edit may have
        // flipped an origin or class toggle, and the picker must match.
        for (ServerPlayer sp : players) {
            if (changed) ActiveOriginService.reconcileDefinitionChange(sp, before.get(sp.getUUID()));
            NeoOriginsNetwork.syncRegistryToPlayer(sp);
            NeoOriginsNetwork.syncToPlayer(sp);
            // Classic mode switches evolution off, so the info screen's Evolution Path follows it.
            NeoOriginsNetwork.syncEvolutionToPlayer(sp);
            if (switchedOn) warnHiddenSelections(sp);
        }
        if (changed && !players.isEmpty()) {
            NeoOrigins.LOGGER.info("Classic mode {}: reconciled powers for {} online player(s)",
                ContentTogglesConfig.isClassicMode() ? "on" : "off", players.size());
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && ContentTogglesConfig.isClassicMode()) {
            warnHiddenSelections(sp);
        }
    }

    /** True when the world's playerdata folder holds any saved player. */
    static boolean worldHasPlayerData(MinecraftServer server) {
        java.nio.file.Path dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
        if (!java.nio.file.Files.isDirectory(dir)) return false;
        try (var files = java.nio.file.Files.list(dir)) {
            return files.anyMatch(f -> f.getFileName().toString().endsWith(".dat"));
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /**
     * Tells the player, in chat, about each layer where they hold a built-in
     * origin or class that classic mode hides, and how to reset it. A classic
     * origin hidden by its own toggle is not mentioned: that is not classic mode's
     * doing, and the toggles never warned about it.
     */
    static void warnHiddenSelections(ServerPlayer sp) {
        var data = sp.getData(OriginAttachments.originData());
        data.getOrigins().forEach((layer, id) -> {
            if (!NeoOrigins.MOD_ID.equals(id.getNamespace())) return;
            if (!hiddenByClassicMode(id.getPath())) return;
            Origin origin = OriginDataManager.INSTANCE.getOrigin(id);
            Component name = origin != null ? origin.name() : Component.literal(id.toString());
            boolean isClass = com.cyberday1.neoorigins.api.PowerLayers.CLASS_LAYER.equals(layer);
            String command = "/neoorigins reset " + sp.getGameProfile().getName() + " " + layer;
            sp.sendSystemMessage(Component.translatable(
                isClass ? "neoorigins.classic_mode.hidden_class" : "neoorigins.classic_mode.hidden_origin",
                name, command).withStyle(net.minecraft.ChatFormatting.YELLOW));
        });
    }

    /** A built-in that classic mode itself hides, as opposed to one switched off by its own toggle. */
    public static boolean hiddenByClassicMode(String path) {
        return ContentTogglesConfig.isBuiltIn(path) && !ContentTogglesConfig.isClassic(path);
    }
}
