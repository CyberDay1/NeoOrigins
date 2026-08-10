package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired whenever a player's evolution tier changes — from a prompt accept
 * ({@code /neoorigins evolve accept}), admin command ({@code /neoorigins
 * evolve <player> <tier>}), or programmatic set.
 *
 * <p>Tier values: 0 = base, 1 = evolved, 2 = ascended, 3 = apex.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.evolutionTierChanged(event => {
 *     if (event.newTier > event.oldTier) {
 *       event.player.tell(`Congrats on tier ${event.newTier}!`)
 *     }
 *   })
 * </pre>
 */
public class EvolutionTierChangedKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final int oldTier;
    private final int newTier;

    public EvolutionTierChangedKubeEvent(ServerPlayer player, int oldTier, int newTier) {
        this.player = player;
        this.oldTier = oldTier;
        this.newTier = newTier;
    }

    public ServerPlayer getPlayer() { return player; }
    public int getOldTier() { return oldTier; }
    public int getNewTier() { return newTier; }
}
