package com.cyberday1.neoorigins.client;

/**
 * Client-side holder for the latest {@code CreatorResultPayload} so the
 * creator screen can surface it. Kept out of {@code NeoOriginsNetwork} for the
 * same dist-safety reason as {@link ClientOriginState}: the common-side
 * handler must not reference client-only code directly.
 */
public final class ClientCreatorState {

    private static volatile boolean lastOk;
    private static volatile String lastMessage = "";
    /** Author preference: hover tooltips in the creator. Some users find the
     *  boxes obstruct the fields they hover, so the screen offers a top-bar
     *  toggle. Static (per-session) — defaults on. */
    private static volatile boolean tooltipsEnabled = true;

    private ClientCreatorState() {}

    public static boolean tooltipsEnabled() { return tooltipsEnabled; }
    public static void toggleTooltips() { tooltipsEnabled = !tooltipsEnabled; }

    public static void setResult(boolean ok, String message) {
        lastOk = ok;
        lastMessage = message == null ? "" : message;
    }

    public static boolean lastOk() { return lastOk; }
    public static String lastMessage() { return lastMessage; }

    public static void clear() { lastMessage = ""; }
}
