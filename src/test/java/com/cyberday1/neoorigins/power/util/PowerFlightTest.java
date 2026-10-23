package com.cyberday1.neoorigins.power.util;

import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.neoforged.neoforge.common.NeoForgeMod;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Power flight rides NeoForge's {@code creative_flight} attribute so mods that
 * reset {@code Abilities.mayfly} every tick can't cancel it. Drives the real
 * helper against a real attribute instance: grant/revoke are per power, a
 * dispatch-less revoke clears all of ours, and nothing reaches the save.
 */
class PowerFlightTest {

    private static final Identifier SWORD = Identifier.parse("neoorigins:riding_the_sword");
    private static final Identifier PHANTOM = Identifier.parse("mypack:phantom_form");

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static AttributeInstance flight() {
        return new AttributeInstance(NeoForgeMod.CREATIVE_FLIGHT, inst -> { });
    }

    @Test
    void grantTurnsFlightOnAndRevokeTurnsItOff() {
        AttributeInstance inst = flight();
        assertEquals(0.0, inst.getValue());
        assertTrue(PowerFlight.grant(inst, SWORD));
        assertTrue(inst.getValue() > 0);
        assertFalse(PowerFlight.grant(inst, SWORD), "re-granting each tick must be a no-op");
        assertTrue(PowerFlight.revoke(inst, SWORD));
        assertEquals(0.0, inst.getValue());
        assertFalse(PowerFlight.revoke(inst, SWORD), "revoking with nothing granted must report no change");
    }

    @Test
    void revokingOnePowerLeavesAnothersFlight() {
        AttributeInstance inst = flight();
        PowerFlight.grant(inst, SWORD);
        PowerFlight.grant(inst, PHANTOM);
        assertNotEquals(PowerFlight.modifierId(SWORD), PowerFlight.modifierId(PHANTOM));
        PowerFlight.revoke(inst, SWORD);
        assertTrue(inst.getValue() > 0);
        assertTrue(PowerFlight.hasAny(inst));
    }

    @Test
    void dispatchlessRevokeClearsAllOursButNotForeignModifiers() {
        AttributeInstance inst = flight();
        PowerFlight.grant(inst, SWORD);
        PowerFlight.grant(inst, PHANTOM);
        PowerFlight.grant(inst, null);
        Identifier foreign = Identifier.parse("othermod:jetpack");
        inst.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(
            foreign, 1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));

        assertTrue(PowerFlight.revoke(inst, null));
        assertFalse(PowerFlight.hasAny(inst));
        assertTrue(inst.hasModifier(foreign), "another mod's flight modifier must survive");
    }

    @Test
    void modifierIsTransientSoItNeverReachesTheSave() {
        AttributeInstance inst = flight();
        PowerFlight.grant(inst, SWORD);
        assertTrue(inst.pack().modifiers().isEmpty(), "flight modifier was written to the save");
    }
}
