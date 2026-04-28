package com.cyberday1.neoorigins.client;

/**
 * Client-side cache of server-authoritative evolution config values.
 * Populated by {@code SyncEvolutionConfigPayload} on login.
 * All getters return the server's values, not the local config file.
 */
public final class ClientEvolutionConfig {

    private ClientEvolutionConfig() {}

    private static boolean enabled = true;
    private static int tier1Kills = 1000;
    private static int tier2Kills = 2500;
    private static int tier3Kills = 5000;
    private static int messageInterval = 100;
    private static int currentKills = 0;
    private static int currentTier = 0;

    public static void sync(boolean enabled, int t1, int t2, int t3, int interval, int kills, int tier) {
        ClientEvolutionConfig.enabled = enabled;
        tier1Kills = t1;
        tier2Kills = t2;
        tier3Kills = t3;
        messageInterval = interval;
        currentKills = kills;
        currentTier = tier;
    }

    public static void updateProgress(int kills, int tier) {
        currentKills = kills;
        currentTier = tier;
    }

    public static boolean isEnabled()       { return enabled; }
    public static int getTier1Kills()       { return tier1Kills; }
    public static int getTier2Kills()       { return tier2Kills; }
    public static int getTier3Kills()       { return tier3Kills; }
    public static int getMessageInterval()  { return messageInterval; }
    public static int getCurrentKills()     { return currentKills; }
    public static int getCurrentTier()      { return currentTier; }

    public static int killsForTier(int tier) {
        return switch (tier) {
            case 1 -> tier1Kills;
            case 2 -> tier2Kills;
            case 3 -> tier3Kills;
            default -> Integer.MAX_VALUE;
        };
    }

    public static String tierName(int tier) {
        return switch (tier) {
            case 1 -> "Evolved";
            case 2 -> "Ascended";
            case 3 -> "Apex";
            default -> "";
        };
    }

    public static void clear() {
        currentKills = 0;
        currentTier = 0;
    }
}
