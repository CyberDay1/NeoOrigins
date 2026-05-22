package com.cyberday1.neoorigins.compat.kubejs.plugin.events;

import dev.latvian.mods.kubejs.event.KubeEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a mount-power consent prompt has been sent to the target —
 * i.e., the consent mode is PROMPT or TEAM-with-no-team and a clickable
 * message went out.
 *
 * <p>JS:
 * <pre>
 *   NeoOriginsEvents.mountRequested(event => {
 *     console.log(`${event.requester.name} wants to mount ${event.target.name}`)
 *   })
 * </pre>
 */
public class MountRequestedKubeEvent implements KubeEvent {
    private final ServerPlayer requester;
    private final ServerPlayer target;

    public MountRequestedKubeEvent(ServerPlayer requester, ServerPlayer target) {
        this.requester = requester;
        this.target = target;
    }

    public ServerPlayer getRequester() { return requester; }
    public ServerPlayer getTarget() { return target; }
}
