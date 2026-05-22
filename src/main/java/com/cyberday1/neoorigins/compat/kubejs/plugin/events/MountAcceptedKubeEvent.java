package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when the target of a mount-power consent prompt accepted via
 * {@code /neoorigins mount accept} or the clickable [ACCEPT] button.
 *
 * <p>The actual mount is fired separately as
 * {@link MountStartedKubeEvent} — this event is just the consent step.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mountAccepted(event => {
 *     console.log(`${event.target.name} accepted ${event.requester.name}'s mount`)
 *   })
 * </pre>
 */
public class MountAcceptedKubeEvent implements KubeEvent {
    private final ServerPlayer requester;
    private final ServerPlayer target;

    public MountAcceptedKubeEvent(ServerPlayer requester, ServerPlayer target) {
        this.requester = requester;
        this.target = target;
    }

    public ServerPlayer getRequester() { return requester; }
    public ServerPlayer getTarget() { return target; }
}
