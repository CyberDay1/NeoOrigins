package com.cyberday1.neoorigins.api.event;

import net.neoforged.bus.api.Event;

/**
 * Fired on the NeoForge event bus after origins and layers have been (re)loaded from datapacks.
 * Addon mods can use this to react to the new data.
 */
public class OriginsLoadedEvent extends Event {
    public OriginsLoadedEvent() {}
}
