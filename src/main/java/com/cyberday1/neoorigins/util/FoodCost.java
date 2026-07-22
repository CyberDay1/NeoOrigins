package com.cyberday1.neoorigins.util;

import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.food.FoodData;

/**
 * Shared helper for deducting active-ability / power hunger costs the way vanilla
 * does: saturation is spent BEFORE the food bar (exactly like sprinting), and the
 * vanilla invariant {@code saturation <= foodLevel} is preserved.
 *
 * <p>Raw {@code setFoodLevel(getFoodLevel() - cost)} deductions bypass saturation
 * and leave saturation floating above food. They also desync the client: vanilla
 * {@link ServerPlayer} only auto-sends a {@link ClientboundSetHealthPacket} when
 * {@code foodLevel} changes vs. the last value it sent. When saturation absorbs the
 * whole cost (foodLevel unchanged) no packet is sent, so saturation-display mods
 * show a stale value until the next {@code eat()} re-clamps it. {@link #spend} forces
 * a resync to avoid that.
 */
public final class FoodCost {

    private FoodCost() {}

    /** @return true if the player's food + saturation can cover {@code cost}. */
    public static boolean canAfford(ServerPlayer p, int cost) {
        FoodData fd = p.getFoodData();
        return fd.getFoodLevel() + fd.getSaturationLevel() >= cost;
    }

    /**
     * Spends {@code cost} hunger points, draining saturation first then the food
     * bar, matching vanilla's rounding: a partial saturation point absorbs a WHOLE
     * cost point (vanilla drains saturation by 1.0/step while sat &gt; 0, touching
     * food only after sat hits 0).
     */
    public static void spend(ServerPlayer p, int cost) {
        if (cost <= 0) return;
        FoodData fd = p.getFoodData();
        float sat = fd.getSaturationLevel();
        int satAbsorbs = Math.min(cost, Mth.ceil(sat)); // partial sat soaks a whole point
        fd.setSaturation(Math.max(0f, sat - satAbsorbs));
        fd.setFoodLevel(Math.max(0, fd.getFoodLevel() - (cost - satAbsorbs)));
        // Force resync: ServerPlayer only auto-sends on foodLevel change, so
        // saturation-display mods would otherwise stay stale until the next eat().
        p.connection.send(new ClientboundSetHealthPacket(
            p.getHealth(), fd.getFoodLevel(), fd.getSaturationLevel()));
    }
}
