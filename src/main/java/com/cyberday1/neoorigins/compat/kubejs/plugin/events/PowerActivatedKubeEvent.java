package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired after an active (keybind) power has successfully executed — i.e.,
 * the power's {@code execute()} returned {@code true} and the cooldown has
 * started. Does NOT fire for powers that aborted due to cooldown, hunger,
 * resource cost, or a no-op return.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.powerActivated(event => {
 *     if (event.powerId == 'mypack:dash') {
 *       event.player.playSound('minecraft:entity.bat.takeoff')
 *     }
 *   })
 * </pre>
 */
public class PowerActivatedKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final Identifier powerId;

    public PowerActivatedKubeEvent(ServerPlayer player, Identifier powerId) {
        this.player = player;
        this.powerId = powerId;
    }

    public ServerPlayer getPlayer() { return player; }
    public Identifier getPowerId() { return powerId; }
}
