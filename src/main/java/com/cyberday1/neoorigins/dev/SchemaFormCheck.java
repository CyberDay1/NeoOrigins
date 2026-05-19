package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.power.schemaform.CodecFieldSpecExtractor;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.power.schemaform.PowerConfigClassResolver;
import com.cyberday1.neoorigins.power.schemaform.SchemaFormModel;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Headless packaging + parse guard for the 2.1 creator's field-spec layer.
 *
 * <p>Replaces the throwaway 2.1 spikes ({@code SchemaFormSpike} /
 * {@code CodecFieldSpike}) — those proved the approach; this proves the
 * <em>production</em> path stays wired: that {@code power.schema.json} is
 * actually on the runtime classpath (the {@code processResources} copy ran)
 * and parses into {@link SchemaFormModel}, and that
 * {@link CodecFieldSpecExtractor} still reflects a core power Config.
 *
 * <p>Invoke via {@code ./gradlew schemaFormCheck}. Exit 1 on any failure.
 */
public final class SchemaFormCheck {

    private SchemaFormCheck() {}

    public static void main(String[] args) {
        int failures = 0;

        // 1. Schema is packaged on the classpath and parses.
        SchemaFormModel model;
        try {
            model = SchemaFormModel.loadFromClasspath();
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  classpath load: " + e.getMessage());
            System.exit(1);
            return;
        }

        // 2. The type universe is non-empty.
        int types = model.allTypes().size();
        if (types == 0) {
            System.out.println("[schema-check] FAIL  schema yielded 0 power types");
            failures++;
        }

        // 3. A known structured branch still resolves with fields.
        String structuredSample = "neoorigins:attribute_modifier";
        if (!model.hasStructuredForm(structuredSample)) {
            System.out.println("[schema-check] FAIL  expected structured branch for "
                + structuredSample);
            failures++;
        } else if (model.formFor(structuredSample).isEmpty()) {
            System.out.println("[schema-check] FAIL  " + structuredSample
                + " resolved to an empty form");
            failures++;
        }

        // 4. Codec-reflection fallback still extracts a core power Config.
        try {
            Class<?> cfg = Class.forName(
                "com.cyberday1.neoorigins.power.builtin.SizeScalingPower$Config");
            List<FormFieldSpec> f = CodecFieldSpecExtractor.extract(cfg);
            if (f.isEmpty()) {
                System.out.println("[schema-check] FAIL  SizeScalingPower$Config "
                    + "extracted 0 fields (expected scale/modify_reach)");
                failures++;
            }
        } catch (ReflectiveOperationException e) {
            System.out.println("[schema-check] FAIL  cannot load SizeScalingPower$Config: "
                + e);
            failures++;
        }

        // 5. PowerConfigClassResolver walks direct + intermediate-base chains.
        record ResolveCase(String powerClass, String expectConfig) {}
        for (ResolveCase rc : List.of(
                // direct: extends PowerType<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.SizeScalingPower",
                    "SizeScalingPower$Config"),
                // intermediate: extends AbstractTogglePower<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.FlightPower",
                    "FlightPower$Config"),
                // intermediate: extends AbstractActivePower<C> extends PowerType<C>
                new ResolveCase("com.cyberday1.neoorigins.power.builtin.ActiveDashPower",
                    "ActiveDashPower$Config"))) {
            try {
                Class<?> got = PowerConfigClassResolver.resolve(Class.forName(rc.powerClass()));
                if (got == null || !got.getName().endsWith(rc.expectConfig())) {
                    System.out.println("[schema-check] FAIL  resolver(" + rc.powerClass()
                        + ") = " + got + ", expected …" + rc.expectConfig());
                    failures++;
                }
            } catch (ReflectiveOperationException e) {
                System.out.println("[schema-check] FAIL  cannot load " + rc.powerClass() + ": " + e);
                failures++;
            }
        }

        // 6. FormModel schema path + EnumHints enrich: attribute_modifier's
        //    `operation` must render as an ENUM with the JSON token vocabulary.
        try {
            List<FormFieldSpec> form = FormModel.forPower(
                Identifier.fromNamespaceAndPath("neoorigins", "attribute_modifier"));
            FormFieldSpec op = form.stream()
                .filter(s -> s.name().equals("operation")).findFirst().orElse(null);
            if (op == null) {
                System.out.println("[schema-check] FAIL  attribute_modifier form missing 'operation'");
                failures++;
            } else if (op.kind() != FormFieldSpec.Kind.ENUM
                    || !op.enumValues().contains("add_value")) {
                System.out.println("[schema-check] FAIL  'operation' kind=" + op.kind()
                    + " values=" + op.enumValues() + " (expected ENUM incl. add_value)");
                failures++;
            }
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  FormModel.forPower threw: " + e);
            failures++;
        }

        System.out.printf("[schema-check] %d power types, %d structured branches, %d failures%n",
            types, model.structuredTypes().size(), failures);
        if (failures > 0) System.exit(1);
        System.out.println("[schema-check] OK");
    }
}
