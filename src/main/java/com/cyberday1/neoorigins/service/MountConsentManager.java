package com.cyberday1.neoorigins.service;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.attachment.EntityAttachments;
import com.cyberday1.neoorigins.compat.FTBTeamsCompat;
import com.cyberday1.neoorigins.compat.OpenPACCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages mount consent requests between players.
 *
 * <p>When a player with the mount power targets another player, the consent
 * mode (configured in {@link NeoOriginsConfig}) determines whether mounting
 * is immediate or requires approval. Pending requests are tracked in memory
 * and expire after the configured timeout.
 */
public final class MountConsentManager {

    private MountConsentManager() {}

    private record MountRequest(UUID requesterUuid, long expireTick, String mountPosition) {}

    /** Pending requests keyed by target player UUID. Latest request wins. */
    private static final Map<UUID, MountRequest> PENDING = new ConcurrentHashMap<>();

    /**
     * Attempts to mount the requester on the target player, respecting the
     * server consent mode.
     *
     * @return {@code true} if the mount happened immediately,
     *         {@code false} if a consent request was sent instead
     */
    public static boolean tryMount(ServerPlayer requester, ServerPlayer target, String mountPosition) {
        cleanExpired(requester.server.getTickCount());

        NeoOriginsConfig.ConsentMode mode = NeoOriginsConfig.mountConsentMode();

        switch (mode) {
            case ALWAYS:
                doMount(requester, target, mountPosition);
                return true;

            case TEAM:
                if (areTeammates(requester.getUUID(), target.getUUID())) {
                    doMount(requester, target, mountPosition);
                    return true;
                }
                // No team mod loaded → fall back to always-allow
                if (!FTBTeamsCompat.isLoaded() && !OpenPACCompat.isLoaded()) {
                    doMount(requester, target, mountPosition);
                    return true;
                }
                // Team mod loaded but not teammates → send prompt
                sendRequest(requester, target, mountPosition);
                return false;

            case PROMPT:
                sendRequest(requester, target, mountPosition);
                return false;

            default:
                doMount(requester, target, mountPosition);
                return true;
        }
    }

    /**
     * Accepts a pending mount request for the given target player.
     */
    public static void acceptRequest(ServerPlayer target) {
        cleanExpired(target.server.getTickCount());

        MountRequest request = PENDING.remove(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.no_request").withStyle(ChatFormatting.RED));
            return;
        }

        if (target.server.getTickCount() > request.expireTick()) {
            target.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.expired").withStyle(ChatFormatting.RED));
            return;
        }

        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requesterUuid());
        if (requester == null || !requester.isAlive()) {
            target.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.requester_offline").withStyle(ChatFormatting.RED));
            return;
        }

        if (requester.distanceTo(target) > 32.0) {
            target.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.too_far").withStyle(ChatFormatting.RED));
            return;
        }

        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireMountAccepted(requester, target);
        doMount(requester, target, request.mountPosition());

        target.sendSystemMessage(Component.translatable(
            "power.neoorigins.mount.accepted").withStyle(ChatFormatting.GREEN));
        requester.sendSystemMessage(Component.translatable(
            "power.neoorigins.mount.accepted_requester", target.getName())
            .withStyle(ChatFormatting.GREEN));
    }

    /**
     * Declines a pending mount request for the given target player.
     */
    public static void declineRequest(ServerPlayer target) {
        MountRequest request = PENDING.remove(target.getUUID());
        if (request == null) {
            target.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.no_request").withStyle(ChatFormatting.RED));
            return;
        }

        target.sendSystemMessage(Component.translatable(
            "power.neoorigins.mount.declined").withStyle(ChatFormatting.GRAY));

        ServerPlayer requester = target.server.getPlayerList().getPlayer(request.requesterUuid());
        if (requester != null) {
            requester.sendSystemMessage(Component.translatable(
                "power.neoorigins.mount.declined_requester", target.getName())
                .withStyle(ChatFormatting.RED));
            com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireMountDeclined(requester, target);
        }
    }

    // ── Internal ────────────────────────────────────────────────────────

    private static void doMount(ServerPlayer requester, ServerPlayer target, String mountPosition) {
        requester.startRiding(target, true);
        requester.setData(EntityAttachments.mountPosition(), mountPosition);
        requester.sendSystemMessage(Component.translatable(
            "power.neoorigins.mount.success", target.getName())
            .withStyle(ChatFormatting.GREEN), true);
        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireMountStarted(requester, target, mountPosition);
    }

    private static void sendRequest(ServerPlayer requester, ServerPlayer target, String mountPosition) {
        long expireTick = requester.server.getTickCount()
            + (long) NeoOriginsConfig.mountRequestTimeoutSeconds() * 20L;
        PENDING.put(target.getUUID(), new MountRequest(requester.getUUID(), expireTick, mountPosition));

        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireMountRequested(requester, target);

        // Notify requester
        requester.sendSystemMessage(Component.translatable(
            "power.neoorigins.mount.request_sent", target.getName())
            .withStyle(ChatFormatting.YELLOW), true);

        // Notify target with clickable buttons
        target.sendSystemMessage(Component.literal(""));

        target.sendSystemMessage(
            Component.translatable("power.neoorigins.mount.request_received", requester.getName())
                .withStyle(ChatFormatting.GOLD));

        target.sendSystemMessage(
            Component.literal("  [")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("ACCEPT")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.GREEN)
                        .withBold(true)
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/neoorigins mount accept"))
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to allow mounting")))))
                .append(Component.literal("]  ")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("DECLINE")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new ClickEvent(
                            ClickEvent.Action.RUN_COMMAND,
                            "/neoorigins mount decline"))
                        .withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to decline")))))
                .append(Component.literal("]")
                    .withStyle(ChatFormatting.GRAY)));

        target.sendSystemMessage(Component.literal(""));
    }

    private static boolean areTeammates(UUID player1, UUID player2) {
        if (FTBTeamsCompat.isLoaded() && FTBTeamsCompat.areTeamTrusted(player1, player2)) {
            return true;
        }
        return OpenPACCompat.isLoaded() && OpenPACCompat.areInSameParty(player1, player2);
    }

    private static void cleanExpired(long currentTick) {
        PENDING.values().removeIf(req -> currentTick > req.expireTick());
    }
}
