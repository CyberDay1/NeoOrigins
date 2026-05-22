package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired every server tick for every power on every player that has the
 * power active — <strong>but only when at least one JS listener has been
 * registered on this event</strong>. The bridge short-circuits firing
 * via {@code POWER_TICK.hasListeners()} so the per-tick cost is paid only
 * when scripts actually consume it.
 *
 * <p>Use sparingly — a typical pack can have 50+ powers across 20+ players,
 * which is 1000+ fires per tick if you listen broadly. Filter on
 * {@code event.powerId} early.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.powerTick(event => {
 *     if (event.powerId != 'mypack:gradual_heal') return
 *     if (event.player.health &lt; event.player.maxHealth) {
 *       event.player.heal(0.1)
 *     }
 *   })
 * </pre>
 */
public class PowerTickKubeEvent implements KubeEvent {
    private final ServerPlayer player;
    private final ResourceLocation powerId;

    public PowerTickKubeEvent(ServerPlayer player, ResourceLocation powerId) {
        this.player = player;
        this.powerId = powerId;
    }

    public ServerPlayer getPlayer() { return player; }
    public ResourceLocation getPowerId() { return powerId; }
}
