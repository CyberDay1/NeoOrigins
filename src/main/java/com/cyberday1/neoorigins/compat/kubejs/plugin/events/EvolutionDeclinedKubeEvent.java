package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a player explicitly declines an evolution prompt via
 * {@code /neoorigins evolve decline} or the clickable [DECLINE] button.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.evolutionDeclined(event => {
 *     console.log(`${event.player.name} declined evolution`)
 *   })
 * </pre>
 */
public class EvolutionDeclinedKubeEvent implements KubeEvent {
    private final ServerPlayer player;

    public EvolutionDeclinedKubeEvent(ServerPlayer player) {
        this.player = player;
    }

    public ServerPlayer getPlayer() { return player; }
}
