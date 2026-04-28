package com.cyberday1.neoorigins.client;

/**
 * Client-side moisture value for the Slime origin HUD bar.
 * Updated by {@code SyncMoisturePayload} from the server.
 * A value of -1 means no slime origin is active (hide the bar).
 */
public class ClientMoistureState {

    private static float moisture = -1.0F;

    public static void set(float value) {
        moisture = value;
    }

    public static float get() {
        return moisture;
    }

    public static boolean isActive() {
        return moisture >= 0.0F;
    }

    public static void clear() {
        moisture = -1.0F;
    }
}
