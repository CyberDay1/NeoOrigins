package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.power.schemaform.CodecFieldSpecExtractor;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormModel;
import com.cyberday1.neoorigins.power.schemaform.PowerConfigClassResolver;
import com.cyberday1.neoorigins.power.schemaform.SchemaFormModel;
import net.minecraft.resources.ResourceLocation;

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
                ResourceLocation.fromNamespaceAndPath("neoorigins", "attribute_modifier"));
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

        // 7. Full per-power form coverage: every builtin power must resolve a
        //    Config and either expose fields or be a genuine marker-only power.
        failures += auditPowerFormCoverage(model);

        // 8. Condition/Action picker sources stay in sync with the parsers.
        failures += auditParserTypes(
            "src/main/java/com/cyberday1/neoorigins/compat/condition/ConditionParser.java",
            "condition");
        failures += auditParserTypes(
            "src/main/java/com/cyberday1/neoorigins/compat/action/ActionParser.java",
            "action");

        // 9. Every form field of every power must have a description.
        failures += auditFieldDocs(model);

        // 10. Action / condition schemas must also be packaged + parse, so the
        //     2.1 RefRow widget can render sub-forms when an entity_action /
        //     condition REF is picked.
        failures += auditAuxSchema(SchemaFormModel.ACTION_RESOURCE_PATH,
            "action", "neoorigins:add_velocity");
        failures += auditAuxSchema(SchemaFormModel.CONDITION_RESOURCE_PATH,
            "condition", "neoorigins:in_water");

        System.out.printf("[schema-check] %d power types, %d structured branches, %d failures%n",
            types, model.structuredTypes().size(), failures);
        if (failures > 0) System.exit(1);
        System.out.println("[schema-check] OK");
    }

    private static int auditAuxSchema(String resource, String label, String sampleId) {
        SchemaFormModel s;
        try {
            s = SchemaFormModel.loadFromClasspath(resource);
        } catch (RuntimeException e) {
            System.out.println("[schema-check] FAIL  " + label + " schema load: " + e.getMessage());
            return 1;
        }
        int branches = s.structuredTypes().size();
        if (branches == 0) {
            System.out.println("[schema-check] FAIL  " + label + " schema parsed 0 structured branches");
            return 1;
        }
        if (!s.hasStructuredForm(sampleId)) {
            System.out.println("[schema-check] FAIL  " + label + " schema missing branch for " + sampleId);
            return 1;
        }
        System.out.printf("[schema-check] %s schema: %d structured branches (sample %s OK)%n",
            label, branches, sampleId);
        return 0;
    }

    /**
     * Re-derives the parser's {@code case "<ns>:<name>"} labels from its source
     * (canonicalised to {@code neoorigins:<name>}, as the parser does at
     * runtime) and asserts they exactly equal the parser's {@code KNOWN_TYPES}
     * set — so the creator's picker can never silently drift from what the
     * parser actually accepts. Returns the failure count.
     */
    private static int auditParserTypes(String src, String label) {
        String text;
        try {
            text = java.nio.file.Files.readString(java.nio.file.Path.of(src));
        } catch (java.io.IOException e) {
            System.out.println("[schema-check] FAIL  cannot read " + src + ": " + e);
            return 1;
        }
        // Switch arms — canonicalised to neoorigins:<name> like the parser does.
        java.util.Set<String> fromSwitch = new java.util.TreeSet<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("case \"[a-z_]+:([a-z_]+)\"").matcher(text);
        while (m.find()) fromSwitch.add("neoorigins:" + m.group(1));

        // The declared KNOWN_TYPES = Set.of( … ) literal block.
        java.util.Set<String> declared = new java.util.TreeSet<>();
        int s = text.indexOf("KNOWN_TYPES");
        int open = s < 0 ? -1 : text.indexOf("Set.of(", s);
        int close = open < 0 ? -1 : text.indexOf(");", open);
        if (close > 0) {
            java.util.regex.Matcher dm = java.util.regex.Pattern
                .compile("\"(neoorigins:[a-z_]+)\"").matcher(text.substring(open, close));
            while (dm.find()) declared.add(dm.group(1));
        }

        java.util.Set<String> missing = new java.util.TreeSet<>(fromSwitch);
        missing.removeAll(declared);
        java.util.Set<String> extra = new java.util.TreeSet<>(declared);
        extra.removeAll(fromSwitch);
        int fails = 0;
        if (declared.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES literal not found in " + src);
            fails++;
        }
        if (!missing.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES missing switch arms: " + missing);
            fails++;
        }
        if (!extra.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + label
                + " KNOWN_TYPES has ids the switch doesn't handle: " + extra);
            fails++;
        }
        if (fails == 0) {
            System.out.printf("[schema-check] %s picker: %d types, in sync with switch%n",
                label, declared.size());
        }
        return fails;
    }

    /**
     * Enumerates every concrete {@code *Power} class in {@code power.builtin},
     * resolves its {@code Config} the way the creator does, and proves the form
     * is thorough: a missing/unresolvable Config is a hard failure; a power
     * with zero fields is reported as marker-only (its empty form is correct
     * and intentional — nothing to configure). Origins and classes share this
     * exact path (a class is just an origin in the class layer), so this
     * covers both. Returns the failure count.
     */
    private static int auditPowerFormCoverage(SchemaFormModel model) {
        int fails = 0;
        java.io.File dir;
        try {
            java.io.File root = new java.io.File(SchemaFormCheck.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
            dir = new java.io.File(root, "com/cyberday1/neoorigins/power/builtin");
        } catch (Exception e) {
            System.out.println("[schema-check] FAIL  cannot locate builtin classes: " + e);
            return 1;
        }
        java.io.File[] files = dir.listFiles((d, n) ->
            n.endsWith("Power.class") && !n.contains("$"));
        if (files == null || files.length == 0) {
            System.out.println("[schema-check] FAIL  no builtin power classes found at " + dir);
            return 1;
        }
        java.util.Arrays.sort(files, java.util.Comparator.comparing(java.io.File::getName));

        int total = 0, withFields = 0, schemaBacked = 0;
        java.util.List<String> markerOnly = new java.util.ArrayList<>();
        java.util.Set<String> structured = model.structuredTypes();

        for (java.io.File f : files) {
            String cn = "com.cyberday1.neoorigins.power.builtin."
                + f.getName().substring(0, f.getName().length() - 6);
            Class<?> pc;
            try {
                // initialize=false: reflection on structure/records doesn't need
                // static init, and some powers' static fields touch MC bootstrap
                // that isn't available headless (would be a false failure).
                pc = Class.forName(cn, false, SchemaFormCheck.class.getClassLoader());
            } catch (Throwable t) {
                System.out.println("[schema-check] FAIL  load " + cn + ": " + t);
                fails++;
                continue;
            }
            if (java.lang.reflect.Modifier.isAbstract(pc.getModifiers())
                    || !com.cyberday1.neoorigins.api.power.PowerType.class.isAssignableFrom(pc)) {
                continue; // abstract base / non-power
            }
            total++;
            Class<?> cfg = com.cyberday1.neoorigins.power.schemaform
                .PowerConfigClassResolver.resolve(pc);
            if (cfg == null || !cfg.isRecord()) {
                System.out.println("[schema-check] FAIL  " + pc.getSimpleName()
                    + ": no Config record resolved (form would be permanently empty)");
                fails++;
                continue;
            }
            int n = CodecFieldSpecExtractor.extract(cfg).size();
            if (n > 0) withFields++;
            else markerOnly.add(pc.getSimpleName());
        }
        for (String t : structured) if (t.startsWith("neoorigins:")) schemaBacked++;

        System.out.printf(
            "[schema-check] coverage: %d builtin powers - %d with fields, %d marker-only, "
            + "%d schema-enriched%n", total, withFields, markerOnly.size(), schemaBacked);
        System.out.println("[schema-check] marker-only (empty form is correct - no config): "
            + String.join(", ", markerOnly));
        return fails;
    }

    /**
     * Enforces that every form field of every registered power has a
     * description — from the schema branch or from {@code field_docs.json}.
     * Fails the build listing every undocumented {@code type.field} so the
     * docs can never silently regress or stay incomplete.
     */
    private static int auditFieldDocs(SchemaFormModel model) {
        String src;
        try {
            src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/cyberday1/neoorigins/power/registry/PowerTypes.java"));
        } catch (java.io.IOException e) {
            System.out.println("[schema-check] FAIL  cannot read PowerTypes.java: " + e);
            return 1;
        }
        com.cyberday1.neoorigins.power.schemaform.FieldDocs docs =
            com.cyberday1.neoorigins.power.schemaform.FieldDocs.get();

        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("reg\\(\\s*\"([a-z0-9_]+)\"\\s*,\\s*new\\s+([\\w.]+)\\s*\\(")
            .matcher(src);
        int totalFields = 0, documented = 0;
        java.util.List<String> undoc = new java.util.ArrayList<>();

        while (m.find()) {
            String id = m.group(1);
            String ref = m.group(2);
            String simple = ref.substring(ref.lastIndexOf('.') + 1);
            Class<?> pc = null;
            for (String fqn : new String[]{
                    ref.contains(".") ? ref : null,
                    "com.cyberday1.neoorigins.power.builtin." + simple,
                    "com.cyberday1.neoorigins.compat." + simple}) {
                if (fqn == null) continue;
                try { pc = Class.forName(fqn, false, SchemaFormCheck.class.getClassLoader()); break; }
                catch (Throwable ignored) { }
            }
            if (pc == null
                    || !com.cyberday1.neoorigins.api.power.PowerType.class.isAssignableFrom(pc)) {
                continue;
            }
            String typeId = "neoorigins:" + id;

            java.util.List<FormFieldSpec> specs;
            boolean schemaBranch = model.hasStructuredForm(typeId);
            if (schemaBranch) {
                specs = model.formFor(typeId);
            } else {
                Class<?> cfg = com.cyberday1.neoorigins.power.schemaform
                    .PowerConfigClassResolver.resolve(pc);
                if (cfg == null || !cfg.isRecord()) continue; // marker-only
                specs = CodecFieldSpecExtractor.extract(cfg);
            }
            for (FormFieldSpec s : specs) {
                if (s.name().equals("type")) continue;
                totalFields++;
                boolean ok = (schemaBranch && s.description() != null
                        && !s.description().isBlank())
                    || (docs.describe(typeId, s.name()) != null
                        && !docs.describe(typeId, s.name()).isBlank());
                if (ok) documented++;
                else undoc.add(typeId + "." + s.name());
            }
        }
        java.util.Collections.sort(undoc);
        System.out.printf("[schema-check] field docs: %d/%d documented%n",
            documented, totalFields);
        if (!undoc.isEmpty()) {
            System.out.println("[schema-check] FAIL  " + undoc.size()
                + " undocumented fields:");
            for (String u : undoc) System.out.println("    " + u);
            return 1;
        }
        return 0;
    }
}
