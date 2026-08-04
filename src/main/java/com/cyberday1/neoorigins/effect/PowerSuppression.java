package com.cyberday1.neoorigins.effect;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The {@code neoorigins:suppression} activation gate, kept apart from the
 * {@link SuppressionEffect} registration so {@link
 * com.cyberday1.neoorigins.api.power.PowerHolder} has one small thing to call.
 */
public final class PowerSuppression {

    private PowerSuppression() {}

    /** True while the player holds {@code neoorigins:suppression}. */
    public static boolean isSuppressed(ServerPlayer player) {
        return player != null && player.hasEffect(ModEffects.SUPPRESSION);
    }

    /**
     * Decides one activation. Returns true when it must not run.
     *
     * <p>The refusal itself is universal, but the feedback is not. Only a
     * deliberate, edge-triggered attempt by the player earns the action bar line
     * and the sound: a key press, or a click in the power GUI. Those fire once,
     * and without the message a press that does nothing reads as the mod being
     * broken — the player cannot see Suppression the way they can see a cooldown
     * on the HUD.
     *
     * <p>Everything else refuses silently, like the cooldown abort next to it.
     * The gate sits in {@link com.cyberday1.neoorigins.api.power.PowerHolder}, so
     * the datapack-facing {@code neoorigins:activate_power} action comes through
     * here too — and a pack that fires that from a per-tick trigger would repaint
     * the action bar twenty times a second. Continuous keybinds have the same
     * problem: they fire every tick the key is held. Silence is the default for
     * that reason; a new call site that nobody thought about fails quietly rather
     * than spamming.
     *
     * @param announce true only when the caller knows this was a one-shot,
     *                 player-initiated attempt
     */
    public static boolean refuseActivation(ServerPlayer player, boolean announce) {
        if (!isSuppressed(player)) return false;
        if (announce) {
            player.displayClientMessage(
                Component.translatable("neoorigins.suppression.blocked").withStyle(ChatFormatting.RED), true);
            player.playNotifySound(SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        return true;
    }
}
