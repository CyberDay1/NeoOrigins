package com.cyberday1.neoorigins.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Classic mode turns evolution off whatever {@code gameplay.toml} says: the
 * classic origins carry no tier powers, so a tier earned there grants nothing.
 * The rule is tested apart from the loaded config.
 */
class EvolutionDisabledRuleTest {

    @Test
    void classicModeTurnsEvolutionOffEvenWhenTheConfigEnablesIt() {
        assertFalse(GameplayConfig.evolutionEnabled(true, true));
    }

    @Test
    void outsideClassicModeTheConfigDecides() {
        assertTrue(GameplayConfig.evolutionEnabled(true, false));
        assertFalse(GameplayConfig.evolutionEnabled(false, false));
    }

    /** Classic mode only ever subtracts: it cannot switch a disabled system back on. */
    @Test
    void classicModeCannotTurnEvolutionBackOn() {
        assertFalse(GameplayConfig.evolutionEnabled(false, true));
    }
}
