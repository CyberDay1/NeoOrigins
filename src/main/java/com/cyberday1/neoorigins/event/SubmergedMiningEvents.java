package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.client.ClientActivePowers;
import com.cyberday1.neoorigins.power.builtin.UnderwaterMiningSpeedPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Finishes the job {@link UnderwaterMiningSpeedPower} starts: cancels vanilla's
 * SECOND underwater mining penalty for origins that mine at full speed submerged.
 *
 * <p>{@code Player.getDestroySpeed} charges two independent penalties, not one:
 * <ol>
 *   <li>{@code Attributes.SUBMERGED_MINING_SPEED} (base 0.2, i.e. 5x slower)
 *       multiplied in whenever the eyes are in water. This is the one
 *       {@code underwater_mining_speed} already cancels, by pushing the
 *       attribute back to 1.0.</li>
 *   <li>{@code if (!this.onGround()) speed /= 5.0F;} — a separate, flat fifth
 *       for mining while airborne. Nothing in the mod touched it.</li>
 * </ol>
 * A swimmer is airborne by that test for as long as they are not standing on the
 * seafloor, so every aquatic origin kept paying the second penalty in full and
 * the tooltip "Mines at full speed while submerged" was only true with the
 * player's feet planted. Cancelling it here makes the tooltip literally true.
 *
 * <p><b>Scope.</b> Only submerged mining is corrected: eye-in-water AND
 * off-ground AND holding the capability. The off-ground penalty is left alone on
 * land, where it is a deliberate anti-cheese rule about mining from a jump or a
 * ladder and has nothing to do with being aquatic.
 *
 * <p><b>Why an event and not a mixin.</b> {@code PlayerEvent.BreakSpeed} fires as
 * the very last line of {@code getDestroySpeed}, after both penalties, so a plain
 * multiply here restores exactly what the divide took. It runs at
 * {@link EventPriority#HIGHEST} so it sees the raw vanilla figure: the two other
 * break-speed listeners in this package are a multiply
 * ({@link BreakSpeedModifierEvents}, order-independent) and a
 * <em>replacement</em> ({@link BareHandToolEvents}, which swaps in a virtual
 * tool's speed if it beats the running value). Correcting first keeps the
 * bare-hand comparison honest instead of multiplying a figure that never
 * carried the penalty.
 *
 * <p>Handled on both sides, like its two neighbours: destroy progress is
 * predicted client-side, so a server-only correction would leave the local
 * player watching the slow animation and mining at the fast rate.
 */
@EventBusSubscriber(modid = NeoOrigins.MOD_ID)
public final class SubmergedMiningEvents {

    private SubmergedMiningEvents() {}

    /** The divisor vanilla applies off-ground; multiplying by it is the cancel. */
    static final float OFF_GROUND_PENALTY = 5.0f;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        float corrected = correctSpeed(event.getEntity(), event.getNewSpeed());
        if (corrected != event.getNewSpeed()) {
            event.setNewSpeed(corrected);
        }
    }

    /**
     * {@code speed} with the off-ground penalty refunded, or unchanged when any
     * of the three gates fails. Split out from the listener so the decision can
     * be exercised without an event bus.
     */
    static float correctSpeed(Player player, float speed) {
        if (player.onGround()) return speed;
        if (!player.isEyeInFluid(FluidTags.WATER)) return speed;
        if (!hasSubmergedMining(player)) return speed;
        return speed * OFF_GROUND_PENALTY;
    }

    /**
     * Whether the player mines at full speed submerged. Reads the capability tag
     * rather than the power configs so the client answer comes from synced state
     * — the same server/client parity split {@link BreakSpeedModifierEvents} uses.
     */
    private static boolean hasSubmergedMining(Player player) {
        if (player instanceof ServerPlayer sp) {
            return ActiveOriginService.hasCapability(sp, UnderwaterMiningSpeedPower.CAPABILITY);
        }
        return player.level().isClientSide()
            && ClientActivePowers.hasCapability(UnderwaterMiningSpeedPower.CAPABILITY);
    }
}
