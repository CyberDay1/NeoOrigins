package com.cyberday1.neoorigins.compat;

import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Soft-dependency probe for Open Parties and Claims.
 *
 * <p>When present, enables party-based auto-consent for the mount power.
 * NeoOrigins loads cleanly without it — the mount consent system falls
 * back to the configured fallback mode when Open Parties and Claims is absent.
 *
 * <p>The inner {@code Impl} class is only classloaded when
 * {@link #isLoaded()} returns {@code true}, keeping OPAC types
 * off the classpath when the mod is not installed.
 */
public final class OpenPACCompat {

    private OpenPACCompat() {}

    private static final String MOD_ID = "openpartiesandclaims";

    private static Boolean cached;

    /** True if Open Parties and Claims is present at runtime. Cached after first check. */
    public static boolean isLoaded() {
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return cached;
    }

    /**
     * Checks whether two players are in the same party.
     * Only call when {@link #isLoaded()} is {@code true}.
     *
     * @return {@code true} if both players share a party, {@code false} otherwise
     *         or if the API call fails
     */
    public static boolean areInSameParty(UUID player1, UUID player2) {
        if (!isLoaded()) return false;
        try {
            return Impl.areInSameParty(player1, player2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reflection-based check to avoid compile-time dependency on Open Parties and Claims.
     * API path: OpenPartiesAndClaims.INSTANCE.getServer().getServerData()
     *           .getPartyManager().getPartyByMember(uuid) → party or null;
     *           party.getId() → UUID.
     */
    private static final class Impl {
        static boolean areInSameParty(UUID player1, UUID player2) {
            try {
                Class<?> mainClass = Class.forName("xaero.pac.OpenPartiesAndClaims");
                Object instance = mainClass.getField("INSTANCE").get(null);
                Object server = instance.getClass().getMethod("getServer").invoke(instance);
                Object serverData = server.getClass().getMethod("getServerData").invoke(server);
                Object partyManager = serverData.getClass().getMethod("getPartyManager").invoke(serverData);

                java.lang.reflect.Method getParty = partyManager.getClass().getMethod("getPartyByMember", UUID.class);
                Object party1 = getParty.invoke(partyManager, player1);
                Object party2 = getParty.invoke(partyManager, player2);

                if (party1 == null || party2 == null) return false;

                Object id1 = party1.getClass().getMethod("getId").invoke(party1);
                Object id2 = party2.getClass().getMethod("getId").invoke(party2);
                return id1.equals(id2);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
