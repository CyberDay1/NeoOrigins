package com.cyberday1.neoorigins.event;

import com.cyberday1.neoorigins.power.builtin.UnderwaterMiningSpeedPower;
import com.cyberday1.neoorigins.service.ActiveOriginService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Guards the second underwater mining penalty being refunded, and only there.
 *
 * <p>Vanilla charges an aquatic miner twice: the submerged-mining attribute
 * (already cancelled by {@code underwater_mining_speed}) and a separate flat
 * {@code /5} for being off the ground, which a swimmer pays continuously unless
 * they are standing on the seafloor. The three negative cases matter as much as
 * the positive one: refunding the off-ground penalty on land, or for an origin
 * without the capability, would be a far broader buff than intended.
 */
class SubmergedMiningEventsTest {

    /** A player in the exact state the correction targets, before the gates are relaxed. */
    private static ServerPlayer submergedSwimmer() {
        ServerPlayer player = mock(ServerPlayer.class);
        when(player.onGround()).thenReturn(false);
        when(player.isEyeInFluid(FluidTags.WATER)).thenReturn(true);
        return player;
    }

    private static MockedStatic<ActiveOriginService> withCapability(ServerPlayer player, boolean has) {
        MockedStatic<ActiveOriginService> service = mockStatic(ActiveOriginService.class);
        service.when(() -> ActiveOriginService.hasCapability(
            player, UnderwaterMiningSpeedPower.CAPABILITY)).thenReturn(has);
        return service;
    }

    @Test
    void anAquaticMinerOffTheSeafloorGetsTheOffGroundPenaltyBack() {
        ServerPlayer player = submergedSwimmer();
        try (MockedStatic<ActiveOriginService> ignored = withCapability(player, true)) {
            assertEquals(10.0f, SubmergedMiningEvents.correctSpeed(player, 2.0f), 1.0e-6f,
                "an off-ground submerged miner with the capability must get the flat /5 refunded");
        }
    }

    @Test
    void standingOnTheSeafloorIsAlreadyFullSpeedAndIsNotBoostedAgain() {
        ServerPlayer player = submergedSwimmer();
        when(player.onGround()).thenReturn(true);
        try (MockedStatic<ActiveOriginService> ignored = withCapability(player, true)) {
            assertEquals(2.0f, SubmergedMiningEvents.correctSpeed(player, 2.0f), 1.0e-6f,
                "vanilla never charged the off-ground penalty here, so there is nothing to refund");
        }
    }

    /**
     * The gate that keeps this from being a general mining buff: jumping on dry
     * land still costs the vanilla fifth, capability or not.
     */
    @Test
    void thePenaltyStandsOnLand() {
        ServerPlayer player = submergedSwimmer();
        when(player.isEyeInFluid(FluidTags.WATER)).thenReturn(false);
        try (MockedStatic<ActiveOriginService> ignored = withCapability(player, true)) {
            assertEquals(2.0f, SubmergedMiningEvents.correctSpeed(player, 2.0f), 1.0e-6f,
                "the off-ground penalty is only refunded underwater");
        }
    }

    @Test
    void anOriginWithoutAquaAffinityKeepsPayingTheFifth() {
        ServerPlayer player = submergedSwimmer();
        try (MockedStatic<ActiveOriginService> ignored = withCapability(player, false)) {
            assertEquals(2.0f, SubmergedMiningEvents.correctSpeed(player, 2.0f), 1.0e-6f,
                "the refund follows the underwater_mining_speed capability, not the water");
        }
    }

    /**
     * The refund is the exact inverse of the vanilla divide. Pinning the constant
     * stops a later "tune the number" edit from turning a correction into a buff.
     */
    @Test
    void theRefundExactlyInvertsTheVanillaDivide() {
        assertEquals(5.0f, SubmergedMiningEvents.OFF_GROUND_PENALTY, 0.0f,
            "vanilla divides by 5 off-ground; anything else here is a balance change, not a fix");
    }
}
