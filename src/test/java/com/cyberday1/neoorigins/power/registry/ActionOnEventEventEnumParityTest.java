package com.cyberday1.neoorigins.power.registry;

import com.cyberday1.neoorigins.compat.CompatEventAliases;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.service.EventPowerIndex;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code event} key of {@code action_on_event} is described in three places
 * that nothing held together: the {@link EventPowerIndex.Event} enum the codec
 * resolves against, the schema {@code options} list both editors read, and
 * {@link CompatEventAliases}.
 *
 * <p>Drift between the first two is invisible and asymmetric. An enum constant
 * missing from the schema is an event that works but that no editor offers; a
 * schema option with no enum constant is worse, because an unknown event is a
 * hard {@code DataResult.error} — the editors would hand an author a value that
 * kills the whole power at load.
 *
 * <p>That second direction is not hypothetical. Two powers in
 * {@code datapacks/thfox-origin} shipped naming {@code mod_food_nutrition} and
 * {@code item_use_start}, neither of which existed, and both failed to load.
 */
class ActionOnEventEventEnumParityTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<String> schemaOptions() {
        BuiltinPowers.PowerSpec spec = BuiltinPowers.descriptors()
            .get(ResourceLocation.parse("neoorigins:action_on_event"));
        assertNotNull(spec, "action_on_event must be a registered power");
        FieldSpec event = spec.fields().stream()
            .filter(f -> "event".equals(f.name()))
            .findFirst()
            .orElseThrow(() -> new AssertionError("action_on_event must declare an `event` field"));
        assertTrue(!event.enumValues().isEmpty(), "`event` must publish its allowed values");
        return event.enumValues();
    }

    private static Set<String> canonicalNames() {
        return java.util.Arrays.stream(EventPowerIndex.Event.values())
            .map(e -> e.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.toCollection(TreeSet::new));
    }

    @Test
    void everyEnumConstantIsOfferedByTheEditors() {
        Set<String> missing = new TreeSet<>(canonicalNames());
        missing.removeAll(schemaOptions());
        assertTrue(missing.isEmpty(),
            "EventPowerIndex.Event constants absent from the action_on_event schema options, "
                + "so no editor offers them: " + missing);
    }

    @Test
    void everySchemaOptionLoads() {
        Set<String> canonical = canonicalNames();
        Set<String> unloadable = new TreeSet<>();
        for (String opt : schemaOptions()) {
            if (canonical.contains(opt)) continue;
            if (CompatEventAliases.resolve(opt) != null) continue;
            unloadable.add(opt);
        }
        assertTrue(unloadable.isEmpty(),
            "schema options that are neither an enum constant nor a compat alias — authoring one "
                + "fails the codec and kills the power: " + unloadable);
    }

    @Test
    void aliasesResolveAndAreNotShadowed() {
        Set<String> canonical = canonicalNames();
        for (String alias : CompatEventAliases.names()) {
            assertEquals(alias.toLowerCase(Locale.ROOT), alias,
                "alias spellings are looked up lowercased, so declaring `" + alias + "` makes it unreachable");
            assertNotNull(CompatEventAliases.resolve(alias),
                "alias `" + alias + "` does not resolve");
            assertTrue(!canonical.contains(alias),
                "alias `" + alias + "` is also a real enum constant, so Event.valueOf wins first "
                    + "and the alias entry is dead — delete it");
        }
    }

    @Test
    void aliasesAreOfferedByTheEditors() {
        Set<String> missing = new TreeSet<>(CompatEventAliases.names());
        missing.removeAll(schemaOptions());
        assertTrue(missing.isEmpty(),
            "compat aliases the loader accepts but the schema omits, so the editors would mark a "
                + "loadable pack invalid: " + missing);
    }
}
