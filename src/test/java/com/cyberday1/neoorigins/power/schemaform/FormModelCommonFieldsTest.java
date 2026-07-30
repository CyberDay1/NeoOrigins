package com.cyberday1.neoorigins.power.schemaform;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every power the in-game creator offers must let the author set the common root
 * keys — {@code name}, {@code description}, {@code hidden}, {@code required_mods}.
 *
 * <p>{@link FormModel#forPower} resolves a form down one of three paths, and they
 * used to disagree. The registry path prepends the common fields and the schema
 * path inherits them from {@link SchemaFormModel}'s branch seeding, but the
 * codec-reflection fallback returned only the {@code Config} record's own
 * components — and the common keys are not part of any {@code Config}, the loader
 * reads them straight off the power JSON. So the native types with no schema
 * branch ({@code multiple}, {@code status_effect}, {@code glow},
 * {@code night_vision}, …) rendered in the creator with no display-name row at
 * all: authorable in a datapack, unreachable in the editor.
 *
 * <p>This lives in the JUnit suite rather than {@code schemaFormCheck} because
 * reaching the reflection path goes through {@code PowerTypes}, whose
 * {@code DeferredRegister} static init needs a bootstrapped Minecraft.
 */
class FormModelCommonFieldsTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Set<String> fieldNames(String type) {
        ResourceLocation rl = ResourceLocation.parse(type);
        Set<String> names = new HashSet<>();
        for (FormFieldSpec s : FormModel.forPower(rl)) names.add(s.name());
        return names;
    }

    /**
     * The regression itself: {@code neoorigins:multiple} has no schema branch and
     * is not registered in {@code BuiltinPowers}, so it is resolved purely by
     * codec reflection.
     */
    @Test
    void reflectionFallbackStillOffersTheCommonRootFields() {
        SchemaFormModel schema = SchemaFormModel.loadFromClasspath();
        assertFalse(schema.hasStructuredForm("neoorigins:multiple"),
            "neoorigins:multiple gained a schema branch — pick another reflection-path "
                + "sample or this test no longer covers the fallback");
        Set<String> names = fieldNames("neoorigins:multiple");
        for (FormFieldSpec common : schema.commonFields()) {
            assertTrue(names.contains(common.name()),
                "neoorigins:multiple form is missing common root field " + common.name()
                    + "; got " + names);
        }
    }

    /** And no creator-visible type may be missing them, whichever path it takes. */
    @Test
    void everyCreatorTypeOffersTheCommonRootFields() {
        SchemaFormModel schema = SchemaFormModel.loadFromClasspath();
        List<String> commonNames =
            schema.commonFields().stream().map(FormFieldSpec::name).toList();
        assertFalse(commonNames.isEmpty(), "power.schema.json declares no common root fields");

        List<String> offenders = new ArrayList<>();
        for (String type : FormModel.creatorTypes()) {
            Set<String> names = fieldNames(type);
            List<String> missing = commonNames.stream().filter(n -> !names.contains(n)).toList();
            if (!missing.isEmpty()) offenders.add(type + " missing " + missing);
        }
        assertTrue(offenders.isEmpty(),
            "creator types without the common root fields: " + offenders);
    }
}
