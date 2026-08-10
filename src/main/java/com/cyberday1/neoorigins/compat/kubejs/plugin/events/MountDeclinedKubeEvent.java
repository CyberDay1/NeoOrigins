package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when the target of a mount-power consent prompt declined via
 * {@code /neoorigins mount decline} or the clickable [DECLINE] button.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mountDeclined(event => {
 *     event.requester.tell(`${event.target.name} said no`)
 *   })
 * </pre>
 */
public class MountDeclinedKubeEvent implements KubeEvent {
    private final ServerPlayer requester;
    private final ServerPlayer target;

    public MountDeclinedKubeEvent(ServerPlayer requester, ServerPlayer target) {
        this.requester = requester;
        this.target = target;
    }

    public ServerPlayer getRequester() { return requester; }
    public ServerPlayer getTarget() { return target; }
}
