package com.cyberday1.neoorigins.compat;

import com.cyberday1.neoorigins.power.registry.PowerTypes;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Every well-known stand-in has to name a type the injector can actually resolve.
 *
 * <p>{@code injectWellKnownPowers} looks the emitted {@code type} up in
 * {@link PowerTypes} and, failing that, parses it as a Route B (Origins-format) type.
 * A {@code neoorigins:} id that is only a LEGACY ALIAS answers neither: the alias table
 * is consulted by {@code PowerDataManager} for powers that arrive as files, which a
 * synthetic injection never does. The stand-in is then dropped with no warning at all,
 * which is how {@code origins:water_vulnerability} came to name
 * {@code neoorigins:damage_in_water} and silently supply nothing. A headless boot caught
 * it; this catches the next one.
 *
 * <p>{@code PowerEnumCheck} already asserts the emitted id EXISTS. Existing is not the
 * same as being resolvable here, which is the whole bug.
 */
// hub: neoorigins/compat-mapping-gaps.md
class WellKnownTargetsResolveTest {

    @Test
    void everyStandInNamesATypeTheInjectorCanResolve() {
        List<String> unresolvable = new ArrayList<>();
        for (var entry : OriginsCompatPowerLoader.WELL_KNOWN.entrySet()) {
            JsonObject json = entry.getValue().get();
            String type = json.get("type").getAsString();
            boolean ok;
            if (type.startsWith("neoorigins:")) {
                // Native: it has to be a registered power type, not an alias id.
                ok = PowerTypes.isBuiltinPath(type.substring("neoorigins:".length()));
            } else {
                // Origins-format: Route B has to claim it.
                ok = OriginsCompatPowerLoader.ROUTE_B_TYPES.contains(type);
            }
            if (!ok) unresolvable.add(entry.getKey() + " -> " + type);
        }
        assertTrue(unresolvable.isEmpty(),
            "these well-known ids name a type the injector cannot resolve, so they inject "
            + "nothing and log nothing: " + unresolvable);
    }

    /** The guard above is only honest while isBuiltinPath answers outside a running game. */
    @Test
    void theRegistryGuardIsNotInert() {
        assertTrue(PowerTypes.isBuiltinPath("condition_passive"),
            "condition_passive is registered; if this is false the test above is vacuous");
        assertFalse(PowerTypes.isBuiltinPath("damage_in_water"),
            "damage_in_water is a legacy alias, not a registered type; if this is true the "
            + "test above cannot tell an alias from a type");
    }
}
