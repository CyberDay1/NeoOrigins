package com.cyberday1.neoorigins.power.builtin;

import com.cyberday1.neoorigins.power.builtin.base.AbstractActivePower;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Seven active types dropped {@code resource_cost} / {@code resource_cost_amount}
 * on the floor: their Config records never declared the keys, and a
 * RecordCodecBuilder discards what it does not declare without a word, so a pack
 * could author a cost and watch the ability stay free.
 *
 * <p>The mod's own data deliberately does NOT use these keys — hunger remains the
 * cost for built-in powers — so nothing shipped exercises this wiring. That is
 * exactly why it is pinned here: this is a capability for pack authors, and
 * without a test it could rot back to silence unnoticed.
 */
// hub: neoorigins/teleport-resource-cost.md
class ResourceCostWiringTest {

    private static final String RESOURCE = "mypack:mana";

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static <C> C decode(Codec<C> codec, String body) {
        String json = body.isEmpty()
            ? "{\"resource_cost\": \"%s\", \"resource_cost_amount\": 7}".formatted(RESOURCE)
            : "{%s, \"resource_cost\": \"%s\", \"resource_cost_amount\": 7}".formatted(body, RESOURCE);
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    /** Both keys readable, through the accessors {@code onActivated} consults. */
    private static void assertCharges(AbstractActivePower.Config config, String type) {
        assertEquals(RESOURCE, config.resourceCost(), type + " drops resource_cost");
        assertEquals(7, config.resourceCostAmount(), type + " drops resource_cost_amount");
    }

    @Test
    void activeTeleportCharges() {
        assertCharges(decode(ActiveTeleportPower.Config.CODEC, ""), "active_teleport");
    }

    @Test
    void activePhaseCharges() {
        assertCharges(decode(ActivePhasePower.Config.CODEC, ""), "active_phase");
    }

    @Test
    void activePlaceBlockCharges() {
        assertCharges(decode(ActivePlaceBlockPower.Config.CODEC, ""), "active_place_block");
    }

    @Test
    void groundSlamCharges() {
        assertCharges(decode(ActiveGroundSlamPower.Config.CODEC, ""), "ground_slam");
    }

    @Test
    void tidalWaveCharges() {
        assertCharges(decode(ActiveTidalWavePower.Config.CODEC, ""), "tidal_wave");
    }

    @Test
    void shadowOrbCharges() {
        assertCharges(decode(ShadowOrbPower.Config.CODEC, ""), "shadow_orb");
    }

    /**
     * {@code summon_minion} charges itself, once per activation, so its cost sits
     * under non-interface names and the base-class accessors must stay empty —
     * otherwise {@code onActivated} charges a second time on top. Its
     * {@code hunger_cost} defaults to 4, which is what made that double charge
     * cost 8 food points a summon, and why a resource has to REPLACE hunger here
     * rather than add to it.
     */
    @Test
    void summonMinionChargesItselfAndHidesTheCostFromTheBaseClass() {
        SummonMinionPower.Config config =
            decode(SummonMinionPower.Config.CODEC, "\"mob_type\": \"minecraft:zombie\"");

        assertEquals(RESOURCE, config.summonResourceId(), "summon_minion drops resource_cost");
        assertEquals(7, config.summonResourceAmount(), "summon_minion drops resource_cost_amount");
        assertEquals(4, config.hungerCostLegacy(), "the authored hunger_cost default");

        assertEquals("", config.resourceCost(), "the base class would charge the resource twice");
        assertEquals(0, config.resourceCostAmount(), "the base class would charge the resource twice");
        assertEquals(0, config.hungerCost(), "the base class would charge hunger twice");
    }

    /** No key, no cost: an existing pack must not start paying for anything. */
    @Test
    void anAbilityWithoutTheKeysStaysFree() {
        var teleport = ActiveTeleportPower.Config.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"range\": 24}")).getOrThrow();
        assertEquals("", teleport.resourceCost());
        assertEquals(0, teleport.resourceCostAmount());

        var orb = ShadowOrbPower.Config.CODEC
            .parse(JsonOps.INSTANCE, JsonParser.parseString("{\"max_orbs\": 2}")).getOrThrow();
        assertEquals("", orb.resourceCost());
        assertEquals(0, orb.resourceCostAmount());
    }
}
