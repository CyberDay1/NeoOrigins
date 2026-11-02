package com.cyberday1.neoorigins.api.power;

/** What {@link com.cyberday1.neoorigins.api.NeoOriginsAPI#grantPower} did. */
public enum GrantPowerResult {
    /** The source now grants the power. */
    GRANTED,
    /** The source already granted it, and its layer moved to the one given. */
    UPDATED,
    /** The source already granted it on the same layer; nothing changed. */
    ALREADY_GRANTED,
    /** No power with that id is loaded; nothing was stored. */
    UNKNOWN_POWER;

    /** True when the source holds the power after the call. */
    public boolean held() {
        return this != UNKNOWN_POWER;
    }
}
