package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired after a power has been granted to a player — typically as part of
 * an origin change, but also during world load / re-grant sweeps.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.powerGranted(event => {
 *     if (event.powerId == 'mypack:fire_breath') {
 *       event.player.give('minecraft:netherite_helmet')
 *     }
 *   })
 * </pre>
 */
public class PowerGrantedKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final Identifier powerId;

    public PowerGrantedKubeEvent(ServerPlayer player, Identifier powerId) {
        this.player = player;
        this.powerId = powerId;
    }

    public ServerPlayer getPlayer() { return player; }
    public Identifier getPowerId() { return powerId; }
}
