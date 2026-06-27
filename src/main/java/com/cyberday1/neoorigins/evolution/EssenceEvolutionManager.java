package com.cyberday1.neoorigins.evolution;

import com.cyberday1.neoorigins.config.GameplayConfig;
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
 * Thresholds are configurable via {@link com.cyberday1.neoorigins.config.GameplayConfig}.
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
        int interval = GameplayConfig.evolutionMessageInterval();

        // Live progress sync for the Origin Info (O-key) menu. Packet is
        // ~3 bytes payload + frame -- cheap enough to send every kill so the
        // GUI updates instantly without polling. The full config payload
        // (thresholds, enabled, interval) stays login/reload-only.
        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncEvolutionProgressToPlayer(player);

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

            int nextThreshold = GameplayConfig.killsForTier(nextTier);

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
            int required = GameplayConfig.killsForTier(nextTier);
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
                            "/neoorigins evolve accept"))
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
                            "/neoorigins evolve decline"))
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

        int required = GameplayConfig.killsForTier(nextTier);
        if (data.getEssenceKills() < required) {
            player.sendSystemMessage(Component.literal("You don't have enough essence kills yet.")
                .withStyle(ChatFormatting.RED));
            return;
        }

        // Persist the new tier, then grant/revoke the tier overlay powers and
        // reconcile health. Shared with the datapack/admin force-set path.
        data.setEvolutionTier(nextTier);
        applyTierPowerChange(player, currentTier, nextTier);

        String tierName = TIER_NAMES[nextTier];
        ChatFormatting color = TIER_COLORS[nextTier];

        player.sendSystemMessage(Component.literal("You have evolved to ")
            .withStyle(ChatFormatting.WHITE)
            .append(Component.literal(tierName)
                .withStyle(color, ChatFormatting.BOLD))
            .append(Component.literal("!")
                .withStyle(ChatFormatting.WHITE)));

        // Sync updated evolution state + powers to client
        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncEvolutionToPlayer(player);
        com.cyberday1.neoorigins.network.NeoOriginsNetwork.syncToPlayer(player);

        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireEvolutionTierChanged(
            player, currentTier, nextTier);
    }

    /**
     * Called when the player declines an evolution via command.
     */
    public static void declineEvolution(ServerPlayer player) {
        player.sendSystemMessage(Component.literal(
            "You resist the pull of essence... for now. The offer will return when you're ready.")
            .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        com.cyberday1.neoorigins.compat.kubejs.KubeJSEventBridge.fireEvolutionDeclined(player);
    }

    /**
     * Returns the display prefix for the player's current evolution tier.
     */
    public static String getTierPrefix(ServerPlayer player) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        int tier = data.getEvolutionTier();
        return tier > 0 && tier < TIER_NAMES.length ? TIER_NAMES[tier] + " " : "";
    }

    /**
     * Applies the power/attribute diff for an evolution tier change and syncs
     * the player's current health to the (possibly changed) maximum. Shared by
     * the accept-prompt path ({@link #acceptEvolution}) and the datapack/admin
     * force-set command so both routes grant/revoke tier-overlay powers
     * identically — otherwise a force-set only updated the tier field and the
     * attribute modifiers (e.g. evolution HP) never applied until a relog
     * rebuilt the power cache, and a reset never revoked them.
     *
     * <p>The caller must have already persisted the new tier via
     * {@link PlayerOriginData#setEvolutionTier}. {@code getEffectivePowers}
     * resolves powers from the explicit tier argument, not the stored tier, so
     * the persisted value does not affect the diff itself.
     */
    public static void applyTierPowerChange(ServerPlayer player, int oldTier, int newTier) {
        PlayerOriginData data = player.getData(OriginAttachments.originData());
        var oldPowers = getEffectivePowers(player, data, oldTier);
        var newPowers = getEffectivePowers(player, data, newTier);

        applyPowerDiff(player, oldPowers, newPowers);

        // Sync max_health changes to current health — without this the player's
        // HP bar doesn't reflect the new maximum until they die or relog.
        player.setHealth(player.getMaxHealth());
    }

    // ── Power diff helpers ─────────────────────────────────────────────

    /**
     * Collects the effective power set for a player at a given tier,
     * across all origin layers (excluding class layer).
     */
    private static java.util.Set<net.minecraft.resources.ResourceLocation> getEffectivePowers(
            ServerPlayer player, PlayerOriginData data, int tier) {
        java.util.Set<net.minecraft.resources.ResourceLocation> powers = new java.util.LinkedHashSet<>();
        for (var entry : data.getOrigins().entrySet()) {
            if (net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("neoorigins", "class")
                    .equals(entry.getKey())) continue;
            var origin = com.cyberday1.neoorigins.data.OriginDataManager.INSTANCE.getOrigin(entry.getValue());
            if (origin == null) continue;
            powers.addAll(origin.powersForTier(tier));
        }
        return powers;
    }

    /**
     * Revokes powers that are in oldPowers but not newPowers, and grants
     * powers that are in newPowers but not oldPowers.
     */
    private static void applyPowerDiff(ServerPlayer player,
            java.util.Set<net.minecraft.resources.ResourceLocation> oldPowers,
            java.util.Set<net.minecraft.resources.ResourceLocation> newPowers) {
        // Revoke removed
        for (var powerId : oldPowers) {
            if (!newPowers.contains(powerId)) {
                var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) holder.onRevoked(player);
            }
        }
        // Grant added
        for (var powerId : newPowers) {
            if (!oldPowers.contains(powerId)) {
                var holder = com.cyberday1.neoorigins.data.PowerDataManager.INSTANCE.getPower(powerId);
                if (holder != null) holder.onGranted(player);
            }
        }
    }
}
