package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.config.GameplayConfig;
import com.cyberday1.neoorigins.rig.PlayerLifecycle;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The air-refill block reads {@link BreathOutOfFluidPower#DRAIN_CAPABILITY} on both
 * sides, so that tag has to follow the same switch as the drain. With
 * {@code [ocean_origins] dries_out} off the drain stops, and a tag left on would
 * freeze the bubble row on land.
 */
class BreathOutOfFluidDriesOutGateTest {

    private static BreathOutOfFluidPower.Config decode() {
        return BreathOutOfFluidPower.Config.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{ \"fluid\": \"water\" }").getAsJsonObject())
            .getOrThrow(msg -> new AssertionError("decode failed: " + msg));
    }

    @Test
    void theDrainTagFollowsTheDriesOutSwitch() {
        assertTrue(BreathOutOfFluidPower.capabilitiesFor(true).contains(BreathOutOfFluidPower.DRAIN_CAPABILITY));
        assertFalse(BreathOutOfFluidPower.capabilitiesFor(false).contains(BreathOutOfFluidPower.DRAIN_CAPABILITY),
            "dries_out off must release the refill");
        // Water vision, the hidden bubble row and the conduit reach are not part of
        // the drain, so the aquatic tag stays either way.
        assertTrue(BreathOutOfFluidPower.capabilitiesFor(false).contains(BreathOutOfFluidPower.DRIES_OUT_CAPABILITY));
    }

    @Test
    void thePlayerAwareSetReadsTheLiveConfig() {
        var power = new BreathOutOfFluidPower();
        var config = decode();
        assertEquals(BreathOutOfFluidPower.capabilitiesFor(GameplayConfig.isOceanOriginsDriesOutEnabled()),
            power.capabilities(PlayerLifecycle.realPlayer(), config));
        assertTrue(power.capabilities(config).containsAll(BreathOutOfFluidPower.capabilitiesFor(true)),
            "the static set must still bound the player-aware one");
    }
}
