package com.cyberday1.neoorigins.compat;

import net.neoforged.fml.ModList;

import java.util.UUID;

/**
 * Soft-dependency probe for FTB Teams.
 *
 * <p>When present, enables team-based auto-consent for the mount power.
 * NeoOrigins loads cleanly without it — the mount consent system falls
 * back to the configured fallback mode when FTB Teams is absent.
 *
 * <p>The inner {@code Impl} class is only classloaded when
 * {@link #isLoaded()} returns {@code true}, keeping FTB Teams types
 * off the classpath when the mod is not installed.
 */
public final class FTBTeamsCompat {

    private FTBTeamsCompat() {}

    private static final String MOD_ID = "ftbteams";

    private static Boolean cached;

    /** True if FTB Teams is present at runtime. Cached after first check. */
    public static boolean isLoaded() {
        if (cached == null) {
            cached = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return cached;
    }

    /**
     * Checks whether two players are on the same FTB team.
     * Only call when {@link #isLoaded()} is {@code true}.
     *
     * @return {@code true} if both players share a team, {@code false} otherwise
     *         or if the API call fails
     */
    public static boolean areOnSameTeam(UUID player1, UUID player2) {
        if (!isLoaded()) return false;
        try {
            return Impl.areOnSameTeam(player1, player2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks whether two players are on the same FTB team OR on teams with an
     * ally(-or-better) relationship. Since FTB Chunks allies are FTB Teams
     * allies, this trusts allied teams for mount consent.
     * Only call when {@link #isLoaded()} is {@code true}.
     *
     * @return {@code true} if both players share a team or are on allied teams,
     *         {@code false} otherwise or if the API call fails
     */
    public static boolean areTeamTrusted(UUID player1, UUID player2) {
        if (!isLoaded()) return false;
        try {
            return Impl.areTeamTrusted(player1, player2);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reflection-based check to avoid compile-time dependency on FTB Teams.
     * FTB Teams API: FTBTeamsAPI.api().getManager().getTeamForPlayerID(uuid)
     * returns Optional&lt;Team&gt;; Team.getId() returns UUID.
     * Ally relation: Team.getRankForPlayer(uuid) returns TeamRank;
     * TeamRank.isAllyOrBetter() is true when the other player's team is allied.
     */
    private static final class Impl {
        static boolean areOnSameTeam(UUID player1, UUID player2) {
            try {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                Object api = apiClass.getMethod("api").invoke(null);
                Object manager = api.getClass().getMethod("getManager").invoke(api);
                java.lang.reflect.Method getTeam = manager.getClass().getMethod("getTeamForPlayerID", UUID.class);

                Object opt1 = getTeam.invoke(manager, player1);
                Object opt2 = getTeam.invoke(manager, player2);

                // Both are Optional<Team>
                var t1 = (java.util.Optional<?>) opt1;
                var t2 = (java.util.Optional<?>) opt2;
                if (t1.isEmpty() || t2.isEmpty()) return false;

                Object id1 = t1.get().getClass().getMethod("getId").invoke(t1.get());
                Object id2 = t2.get().getClass().getMethod("getId").invoke(t2.get());
                return id1.equals(id2);
            } catch (Exception e) {
                return false;
            }
        }

        static boolean areTeamTrusted(UUID player1, UUID player2) {
            try {
                Class<?> apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
                Object api = apiClass.getMethod("api").invoke(null);
                Object manager = api.getClass().getMethod("getManager").invoke(api);
                java.lang.reflect.Method getTeam = manager.getClass().getMethod("getTeamForPlayerID", UUID.class);

                Object opt1 = getTeam.invoke(manager, player1);
                Object opt2 = getTeam.invoke(manager, player2);

                var t1 = (java.util.Optional<?>) opt1;
                var t2 = (java.util.Optional<?>) opt2;
                if (t1.isEmpty() || t2.isEmpty()) return false;

                Object team1 = t1.get();
                Object team2 = t2.get();

                // Same team.
                Object id1 = team1.getClass().getMethod("getId").invoke(team1);
                Object id2 = team2.getClass().getMethod("getId").invoke(team2);
                if (id1.equals(id2)) return true;

                // Allied (or better) team: ask team1 what rank player2 holds.
                // TeamRank.isAllyOrBetter() covers ALLY/MEMBER/OFFICER/OWNER.
                Object rank = team1.getClass()
                    .getMethod("getRankForPlayer", UUID.class)
                    .invoke(team1, player2);
                if (rank == null) return false;
                return (Boolean) rank.getClass().getMethod("isAllyOrBetter").invoke(rank);
            } catch (Exception e) {
                return false;
            }
        }
    }
}
