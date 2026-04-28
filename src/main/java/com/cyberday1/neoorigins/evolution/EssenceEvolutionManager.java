package com.cyberday1.neoorigins.evolution;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import com.cyberday1.neoorigins.attachment.OriginAttachments;
import com.cyberday1.neoorigins.attachment.PlayerOriginData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Manages the Essence Evolution system: mob kill tracking, milestone
 * chat messages, and evolution tier prompts.
 *
 * <p>Kill counts and tiers are persisted in {@link PlayerOriginData}.
 * Thresholds are configurable via {@link NeoOriginsConfig}.
 */
public final class EssenceEvolutionManager {

    private EssenceEvolutionManager() {}

    /** Tier names for display. Index 0 is unused (base has no prefix). */
    public static final String[] TIER_NAMES = {"", "Evolved", "Ascended", "Apex"};

    /** Tier colours for chat messages. */
    private static final ChatFormatting[] TIER_COLORS = {
        ChatFormatting.WHITE,       // base (unused)
        ChatFormatting.GREEN,       // Evolved
        ChatFormatting.LIGHT_PURPLE, // Ascended
        ChatFormatting.GOLD         // Apex
    };

    /**
     * Called when a player kills a mob (non-player). Increments the kill
     * counter, sends milestone messages, and triggers evolution prompts.
     */
    public static void onMobKill(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int kills = data.incrementEssenceKills();
        int currentTier = data.getEvolutionTier();
        int interval = NeoOriginsConfig.evolutionMessageInterval();

        // ── Milestone chat messages ────────────────────────────────────
        if (kills % interval == 0) {
            int nextTier = currentTier + 1;
            if (nextTier > 3) {
                // Already at Apex — just flavor text
                player.sendSystemMessage(Component.literal(
                    "The essence within you burns at its peak. (" + kills + " kills)")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
                return;
            }

            int nextThreshold = NeoOriginsConfig.killsForTier(nextTier);

            if (kills < nextThreshold) {
                // Progress message
                player.sendSystemMessage(Component.literal(
                    "You feel the essence of your victims strengthening you... ("
                    + kills + "/" + nextThreshold + ")")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
            }
        }

        // ── Evolution threshold reached ────────────────────────────────
        int nextTier = currentTier + 1;
        if (nextTier <= 3) {
            int required = NeoOriginsConfig.killsForTier(nextTier);
            if (kills >= required) {
                triggerEvolutionPrompt(player, nextTier);
            }
        }
    }

    /**
     * Sends an evolution prompt to the player. Currently sends a chat
     * message with a clickable accept command. A full GUI screen can
     * replace this later.
     */
    private static void triggerEvolutionPrompt(ServerPlayer player, int tier) {
        String tierName = TIER_NAMES[tier];
        ChatFormatting color = TIER_COLORS[tier];

        player.sendSystemMessage(Component.literal("").append(
            Component.literal("═══════════════════════════════════")
                .withStyle(color)));

        player.sendSystemMessage(Component.literal("  Your essence has reached a new threshold!")
            .withStyle(color, ChatFormatting.BOLD));

        player.sendSystemMessage(Component.literal("  You may evolve to: ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(tierName)
                .withStyle(color, ChatFormatting.BOLD)));

        player.sendSystemMessage(Component.literal(""));

        // Clickable accept button
        player.sendSystemMessage(
            Component.literal("  [")
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal("EVOLVE")
                    .withStyle(style -> style
                        .withColor(color)
                        .withBold(true)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/origin evolve accept"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Click to evolve!")))))
                .append(Component.literal("]  ")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("[")
                    .withStyle(ChatFormatting.GRAY))
                .append(Component.literal("DECLINE")
                    .withStyle(style -> style
                        .withColor(ChatFormatting.RED)
                        .withClickEvent(new net.minecraft.network.chat.ClickEvent(
                            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND,
                            "/origin evolve decline"))
                        .withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                            Component.literal("Decline this evolution")))))
                .append(Component.literal("]")
                    .withStyle(ChatFormatting.GRAY)));

        player.sendSystemMessage(Component.literal("").append(
            Component.literal("═══════════════════════════════════")
                .withStyle(color)));
    }

    /**
     * Called when the player accepts an evolution via command.
     */
    public static void acceptEvolution(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int currentTier = data.getEvolutionTier();
        int nextTier = currentTier + 1;

        if (nextTier > 3) {
            player.sendSystemMessage(Component.literal("You are already at Apex tier.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        int required = NeoOriginsConfig.killsForTier(nextTier);
        if (data.getEssenceKills() < required) {
            player.sendSystemMessage(Component.literal("You don't have enough essence kills yet.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        data.setEvolutionTier(nextTier);
        String tierName = TIER_NAMES[nextTier];
        ChatFormatting color = TIER_COLORS[nextTier];

        player.sendSystemMessage(Component.literal("You have evolved to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(tierName)
                .withStyle(color, ChatFormatting.BOLD))
            .append(Component.literal("!")
                .withStyle(ChatFormatting.WHITE)));

        // Sync updated evolution state to client
        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncEvolutionToPlayer(player);

        // TODO: swap origin to evolved variant + apply name prefix
        // For now, the tier is persisted and powers can check it via
        // PlayerOriginData.getEvolutionTier()
    }

    /**
     * Called when the player declines an evolution via command.
     */
    public static void declineEvolution(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
            "You resist the pull of essence... for now. The offer will return when you're ready.")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /**
     * Returns the display prefix for the player's current evolution tier.
     */
    public static String getTierPrefix(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int tier = data.getEvolutionTier();
        return tier > 0 && tier < TIER_NAMES.length ? TIER_NAMES[tier] + " " : "";
    }
}
