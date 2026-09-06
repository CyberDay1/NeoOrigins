package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.api.power.PowerType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A {@code key} FieldSpec is a promise the editors make to a pack author: put a
 * hotkey here and the power fires from it. Nothing in the build proved that
 * promise, because {@code key} is not on any power Codec &mdash; it is read out
 * of the raw JSON by {@code OriginsCompatPowerLoader#registerNativeActiveHotkeys},
 * which skips anything failing {@code PowerHolder#isActive()}.
 *
 * <p>{@code neoorigins:toggle} declared {@code key} and failed exactly that gate:
 * {@link com.cyberday1.neoorigins.power.builtin.TogglePower} is a bare data holder
 * that overrides neither {@code isActivePower} nor {@code onActivated}, so the
 * field was authorable in both editors, schema-valid, and inert. Both editors
 * offered it because both read the generated schema, so neither could catch it.
 */
class KeyFieldReachabilityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<BuiltinPowers.PowerSpec> declaringKey() {
        List<BuiltinPowers.PowerSpec> hits = new ArrayList<>();
        for (var spec : BuiltinPowers.descriptors().values()) {
            if (spec.fields().stream().anyMatch(f -> "key".equals(f.name()))) hits.add(spec);
        }
        return hits;
    }

    /**
     * Some types are active only for certain configs (ConditionPassivePower is
     * active when {@code toggleable}), so the no-arg {@code isActivePower()} is
     * false while the config-aware override is not. Such a type is still
     * keypress-reachable, so accept it. The bridge method the compiler emits for
     * the override counts, which is why this matches on name and arity only.
     */
    private static boolean overridesConfigAwareIsActive(Class<?> cls) {
        for (Class<?> c = cls; c != null && c != PowerType.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if ("isActivePower".equals(m.getName()) && m.getParameterCount() == 1) return true;
            }
        }
        return false;
    }

    @Test
    void keyIsOnlyDeclaredOnPowersAKeypressCanReach() throws Exception {
        List<BuiltinPowers.PowerSpec> declaring = declaringKey();

        // Anti-vacuity: with no key field anywhere the sweep below proves nothing.
        assertFalse(declaring.isEmpty(),
            "no registered power declares a 'key' FieldSpec, so this test is vacuous");

        List<String> unreachable = new ArrayList<>();
        for (var spec : declaring) {
            Object instance = spec.powerClass().getDeclaredConstructor().newInstance();
            assertTrue(instance instanceof PowerType<?>,
                spec.id() + " is registered as a power but is not a PowerType");
            boolean reachable = ((PowerType<?>) instance).isActivePower()
                || overridesConfigAwareIsActive(spec.powerClass());
            if (!reachable) unreachable.add(spec.id() + " (" + spec.powerClass().getSimpleName() + ")");
        }

        assertTrue(unreachable.isEmpty(),
            "these power types advertise a 'key' field the editors will happily author, but "
                + "registerNativeActiveHotkeys skips them on the isActive() gate, so the hotkey "
                + "never fires: " + unreachable);
    }

    @Test
    void activeAbilityStillDeclaresKey() {
        // The docs now tell authors to put `key` straight on the native type
        // rather than rewriting the power as origins:active_self. Dropping the
        // spec would make POWER_TYPES.md and API.md wrong again.
        FieldSpec key = BuiltinPowers.fieldsFor("neoorigins:active_ability").stream()
            .filter(f -> "key".equals(f.name()))
            .findFirst()
            .orElse(null);
        assertNotNull(key, "neoorigins:active_ability lost its 'key' FieldSpec");
    }

    @Test
    void toggleDeclaresNoKey() {
        // Keybind a toggle by giving the key to an active_ability whose
        // entity_action is neoorigins:toggle, not by keying the flag itself.
        assertTrue(BuiltinPowers.fieldsFor("neoorigins:toggle").stream()
                .noneMatch(f -> "key".equals(f.name())),
            "neoorigins:toggle declares 'key' again; TogglePower is not an active power, "
                + "so the field is authorable and inert");
    }
}
