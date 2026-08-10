package com.cyberday1.neoorigins.power.builtin.base;

import com.cyberday1.neoorigins.power.builtin.FlightPower;
import com.cyberday1.neoorigins.power.builtin.StealthPower;
import com.cyberday1.neoorigins.power.builtin.WraithPhasePower;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Companion to {@code ToggleKeyIdentityTest}, covering the worst case of the
 * same bug. The other toggle families at least mixed some config into their key;
 * {@link AbstractTogglePower} used {@code getClass().getName()} and nothing else,
 * so any two powers of one subclass shared a flag unconditionally. Its javadoc
 * told subclasses to override the key with a discriminator and none of the seven
 * ever did.
 *
 * <p>Keys are now the power's resource id, computed in a {@code final} method so
 * a new subclass cannot reintroduce the bug by forgetting to override anything.
 */
class AbstractTogglePowerKeyIdentityTest {

    private static final Identifier POWER_A = Identifier.parse("mypack:flight_a");
    private static final Identifier POWER_B = Identifier.parse("mypack:flight_b");

    private static FlightPower.Config flightConfig() {
        return new FlightPower.Config("neoorigins:flight", "", false, false, "");
    }

    /**
     * The bug: an origin with two {@code flight} powers (say one gated to the
     * Nether) had one toggle flag between them, so the keybind on either flipped
     * both, and revoking either cleared the other's state.
     */
    @Test
    void twoPowersOfOneSubclassGetDistinctKeys() {
        FlightPower power = new FlightPower();
        FlightPower.Config config = flightConfig();

        assertEquals(POWER_A.toString(), power.toggleKeyFor(POWER_A, config),
            "the key is the power's own id");
        assertNotEquals(power.toggleKeyFor(POWER_A, config), power.toggleKeyFor(POWER_B, config),
            "two flight powers must never share a toggle flag");
    }

    /** The pre-2.2.24 key, which is why the above was broken. */
    @Test
    void theOldKeyWasTheClassNameAlone() {
        FlightPower power = new FlightPower();
        assertEquals(FlightPower.class.getName(), power.getLegacyToggleKey(flightConfig()),
            "the fallback must stay byte-identical to the old formula, or saved toggles read as on");
    }

    /**
     * Outside a {@link com.cyberday1.neoorigins.api.power.PowerHolder} dispatch
     * there is no ambient id, and the fallback is the old key rather than some
     * third shape that would match neither the saved flag nor the new one.
     */
    @Test
    void aNullDispatchIdFallsBackToTheLegacyKey() {
        FlightPower power = new FlightPower();
        FlightPower.Config config = flightConfig();
        assertEquals(power.getLegacyToggleKey(config), power.toggleKeyFor(null, config));
    }

    /**
     * Different subclasses never collided even before the fix, and must not start
     * colliding now: two powers of different types sharing one id would be the
     * same power.
     */
    @Test
    void differentSubclassesStayDistinctUnderBothKeyShapes() {
        FlightPower flight = new FlightPower();
        StealthPower stealth = new StealthPower();
        assertNotEquals(flight.getLegacyToggleKey(flightConfig()),
            stealth.getLegacyToggleKey(new StealthPower.Config(200, "neoorigins:stealth", "", false)));
    }

    /**
     * {@code wraith_phase} short-circuits {@code isToggledOff} when authored
     * {@code always_on}. That override has to sit on the id-taking form, because
     * the two-arg form delegates to it: overriding only the two-arg form would
     * let every caller holding a power id (the HUD sync, the capability probe,
     * the compat toggle facade) walk straight past the short-circuit and report
     * an always-on wraith phase as switched off.
     */
    @Test
    void wraithPhaseOverridesTheIdTakingFormOfIsToggledOff() {
        assertDoesNotThrow(() -> WraithPhasePower.class.getDeclaredMethod(
            "isToggledOff", ServerPlayer.class, WraithPhasePower.Config.class, Identifier.class));
    }
}
