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

    private ClientCreatorState() {}

    public static void setResult(boolean ok, String message) {
        lastOk = ok;
        lastMessage = message == null ? "" : message;
    }

    public static boolean lastOk() { return lastOk; }
    public static String lastMessage() { return lastMessage; }

    public static void clear() { lastMessage = ""; }
}
