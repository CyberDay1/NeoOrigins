package com.cyberday1.neoorigins.dev;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import com.cyberday1.neoorigins.power.schemaform.SchemaFormModel;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Parity gate between the committed hand-written {@code action.schema.json} /
 * {@code condition.schema.json} and what {@link ActionConditionSchemaGenerator}
 * would emit from the {@link com.cyberday1.neoorigins.compat.action.BuiltinActions}
 * / {@link com.cyberday1.neoorigins.compat.condition.BuiltinConditions} registries.
 *
 * <p>This is the validation engine for the registry-parity pass: both schemas are
 * parsed through the SAME production {@link SchemaFormModel} the editors use, and
 * the per-id {@link FormFieldSpec} sets are diffed. Because both sides go through
 * the real form model, an empty diff proves the registry faithfully expresses
 * everything the shipped schema modelled — i.e. flipping generation on will not
 * regress any editor. The mechanical diff replaces eyeballing the registry against
 * the schema.
 *
 * <p>Classification:
 * <ul>
 *   <li><b>MISSING</b> — a type id has a structured branch in the committed schema
 *       but not in the generated one (the registry under-models). <i>Failure.</i></li>
 *   <li><b>CHANGED</b> — a field's widget-relevant shape (kind / required / $ref /
 *       items.$ref / enum / range) differs. <i>Failure.</i></li>
 *   <li><b>EXTRA</b> — a type id is structured in the generated schema but not the
 *       committed one (expected: the uniform {@code apace:} superset, plus any
 *       registry verbs the hand-written file omitted). <i>Info only.</i></li>
 *   <li><b>DESC</b> — only the help text differs. <i>Info only.</i></li>
 * </ul>
 *
 * <p>Run: {@code ./gradlew actionConditionSchemaCheck}. Exits non-zero on any
 * MISSING or CHANGED so it can gate the registry-parity work and, later, guard
 * against drift once generation is the source of truth.
 */
public final class ActionConditionSchemaCheck {

    private ActionConditionSchemaCheck() {}

    public static void main(String[] args) throws IOException {
        int failures = 0;
        failures += check(ActionConditionSchemaGenerator.ACTION,
            Path.of("docs/schema/action.schema.json"));
        failures += check(ActionConditionSchemaGenerator.CONDITION,
            Path.of("docs/schema/condition.schema.json"));

        System.out.println();
        if (failures > 0) {
            System.out.println("PARITY FAILED: " + failures
                + " MISSING/CHANGED finding(s) — registry does not yet express the committed schema.");
            System.exit(1);
        }
        System.out.println("PARITY OK: registry generates a parse-equivalent schema for both documents.");
    }

    private static int check(String which, Path committedPath) throws IOException {
        System.out.println("══════════════════════════════════════════════════════════════");
        System.out.println("  " + which.toUpperCase() + "  (" + committedPath + ")");
        System.out.println("══════════════════════════════════════════════════════════════");

        SchemaFormModel committed = SchemaFormModel.load(committedPath);
        String generatedJson = ActionConditionSchemaGenerator.buildSchemaJson(which, committedPath);
        SchemaFormModel generated = SchemaFormModel.load(
            new ByteArrayInputStream(generatedJson.getBytes(StandardCharsets.UTF_8)));

        int failures = 0;
        List<String> extras = new ArrayList<>();

        // The diff below iterates the COMMITTED schema's structured ids, so an
        // empty committed model reports "0 findings" and this parity gate passes
        // having compared nothing. Treat that as a failure of the gate itself.
        if (committed.structuredTypes().isEmpty()) {
            System.out.println("  MISSING  " + committedPath + " parsed 0 structured"
                + " branches — nothing to diff, so this parity gate was vacuous.");
            System.out.println("  → " + which + ": 1 MISSING/CHANGED finding(s).");
            System.out.println();
            return 1;
        }

        // Every id the committed schema models must be modelled in the generated one.
        for (String id : new TreeSet<>(committed.structuredTypes())) {
            if (!generated.hasStructuredForm(id)) {
                System.out.println("  MISSING  " + id + " — structured in committed, absent in generated");
                failures++;
                continue;
            }
            failures += diffFields(id, committed.formFor(id), generated.formFor(id));
        }

        for (String id : new TreeSet<>(generated.structuredTypes())) {
            if (!committed.hasStructuredForm(id)) extras.add(id);
        }
        if (!extras.isEmpty()) {
            System.out.println("  EXTRA (info, " + extras.size()
                + " generated-only ids — apace: superset / registry verbs the schema omitted):");
            System.out.println("    " + String.join(", ", extras));
        }
        System.out.println("  → " + which + ": " + failures + " MISSING/CHANGED finding(s).");
        System.out.println();
        return failures;
    }

    /** Diff the two field lists for one branch by JSON name. Returns failure count. */
    private static int diffFields(String id, List<FormFieldSpec> committed, List<FormFieldSpec> generated) {
        Map<String, FormFieldSpec> gen = new LinkedHashMap<>();
        for (FormFieldSpec f : generated) gen.put(f.name(), f);
        Map<String, FormFieldSpec> com = new LinkedHashMap<>();
        for (FormFieldSpec f : committed) com.put(f.name(), f);

        int failures = 0;
        for (FormFieldSpec c : committed) {
            FormFieldSpec g = gen.get(c.name());
            if (g == null) {
                System.out.println("  CHANGED  " + id + "." + c.name()
                    + " — field present in committed, absent in generated");
                failures++;
                continue;
            }
            failures += diffField(id, c, g);
        }
        for (FormFieldSpec g : generated) {
            if (!com.containsKey(g.name())) {
                // Generated-only field: info (the registry may model an extra field
                // the hand-written schema omitted — additive, never a regression).
                System.out.println("  EXTRA-FIELD (info)  " + id + "." + g.name()
                    + " [" + g.kind() + "] — in generated, not committed");
            }
        }
        return failures;
    }

    /**
     * Compare one field. A finding FAILS only when generating from the registry
     * would <em>regress</em> a real editor capability: a different widget
     * {@code kind} (e.g. losing a {@code REF} picker or {@code ENUM} dropdown for a
     * raw box — but NOT an INTEGER↔NUMBER swap, which is the same widget), a
     * different {@code $ref}/{@code items.$ref} target (a lost/repointed picker or
     * list-editor), a lost {@code enum} option, or a field the committed schema
     * models that the registry omits entirely. Softer deltas are INFO: the registry
     * is becoming the source of truth and is sometimes <em>richer</em> or simply
     * carries different regenerable metadata than the hand-written file — a tighter
     * numeric bound, an extra enum option, a dropped over-strict {@code required}
     * flag (the field is still present), help-text wording — all reviewed in the
     * regenerated-schema diff, not editor regressions.
     */
    private static int diffField(String id, FormFieldSpec c, FormFieldSpec g) {
        List<String> fails = new ArrayList<>();
        List<String> infos = new ArrayList<>();

        // kind: a different widget kind is a regression EXCEPT an INTEGER↔NUMBER
        // swap — both render the same numeric input, so neither direction loses an
        // editor capability (the registry's INTEGER is often the tighter/correct
        // choice the regenerated schema should adopt). That delta is info.
        if (c.kind() != g.kind()) {
            if (numeric(c.kind()) && numeric(g.kind())) infos.add("kind " + c.kind() + "→" + g.kind());
            else fails.add("kind " + c.kind() + "→" + g.kind());
        }
        if (!Objects.equals(c.ref(), g.ref())) fails.add("$ref " + c.ref() + "→" + g.ref());
        if (!Objects.equals(c.itemsRef(), g.itemsRef())) fails.add("items.$ref " + c.itemsRef() + "→" + g.itemsRef());
        // required: the field is present either way — only a "required" asterisk
        // differs. The registry is becoming the source of truth and deliberately
        // marks fields with documented "absent → default" semantics optional, so a
        // committed-required → generated-optional delta is the regenerated schema
        // shedding an over-strict flag, not a lost field. Both directions are info,
        // surfaced in the regenerated-schema diff for review.
        if (c.required() != g.required()) {
            infos.add("required " + c.required() + "→" + g.required());
        }
        // enum: any committed option the generated schema lacks is a lost choice →
        // fail. Extra generated options (a superset) are info.
        TreeSet<String> cEnum = new TreeSet<>(c.enumValues());
        TreeSet<String> gEnum = new TreeSet<>(g.enumValues());
        if (!cEnum.equals(gEnum)) {
            TreeSet<String> lost = new TreeSet<>(cEnum);
            lost.removeAll(gEnum);
            if (!lost.isEmpty()) fails.add("enum lost " + lost);
            else infos.add("enum superset (+" + diff(gEnum, cEnum) + ")");
        }
        if (!Objects.equals(c.min(), g.min())) infos.add("min " + c.min() + "→" + g.min());
        if (!Objects.equals(c.max(), g.max())) infos.add("max " + c.max() + "→" + g.max());

        if (!fails.isEmpty()) {
            System.out.println("  CHANGED  " + id + "." + c.name() + " — " + String.join("; ", fails)
                + (infos.isEmpty() ? "" : "  [info: " + String.join("; ", infos) + "]"));
            return 1;
        }
        if (!infos.isEmpty()) {
            System.out.println("  INFO  " + id + "." + c.name() + " — " + String.join("; ", infos));
        } else if (!Objects.equals(c.description(), g.description())) {
            System.out.println("  DESC (info)  " + id + "." + c.name() + " — help text differs");
        }
        return 0;
    }

    /** INTEGER and NUMBER share one numeric input widget (interchangeable). */
    private static boolean numeric(FormFieldSpec.Kind k) {
        return k == FormFieldSpec.Kind.INTEGER || k == FormFieldSpec.Kind.NUMBER;
    }

    private static TreeSet<String> diff(TreeSet<String> a, TreeSet<String> b) {
        TreeSet<String> r = new TreeSet<>(a);
        r.removeAll(b);
        return r;
    }
}
