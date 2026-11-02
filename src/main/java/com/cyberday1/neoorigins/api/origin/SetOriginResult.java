package com.cyberday1.neoorigins.api.origin;

/** Outcome of {@link com.cyberday1.neoorigins.api.NeoOriginsAPI#setOrigin}. */
public enum SetOriginResult {
    /** The layer now holds the origin. */
    SUCCESS,
    /** The layer already held that origin; nothing changed. */
    UNCHANGED,
    /** No layer with that id is loaded. */
    UNKNOWN_LAYER,
    /** No origin with that id is loaded (or a listener redirected to one that isn't). */
    UNKNOWN_ORIGIN,
    /** The layer is unique and another player holds that origin. */
    CLAIMED,
    /** An {@link com.cyberday1.neoorigins.api.event.OriginChangedEvent} listener cancelled the change. */
    CANCELLED
}
