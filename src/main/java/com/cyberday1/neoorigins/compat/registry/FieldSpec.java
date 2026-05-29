package com.cyberday1.neoorigins.compat.registry;

import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;

import java.util.List;

/**
 * Declarative description of one config field on an action/condition descriptor.
 *
 * <p>Phase-1 keystone (see {@code planning/REGISTRY_REFACTOR_PLAN.md}): this is
 * the single source from which the JSON schema, the doc reference tables, and
 * both editors' forms are generated (Phase 2/4). It deliberately mirrors the
 * in-game {@link FormFieldSpec} shape — same {@link FormFieldSpec.Kind}
 * vocabulary — so the existing renderer can consume descriptors directly via
 * {@link #toFormSpec()} (Phase-1.1: "reuse the in-game FormFieldSpec shape").
 *
 * <p>Per locked decision <b>D2</b>, the field's doc string lives physically on
 * the {@code FieldSpec} that drives parsing (fluent {@link #doc(String)}),
 * replacing the hand-maintained {@code field_docs.json} — so a parsed field
 * cannot exist without its doc slot beside it.
 *
 * <p>Records are immutable; the fluent builders ({@link #doc}, {@link #def},
 * {@link #range}, {@link #options}, {@link #ref}) each return a new instance,
 * so a spec reads as {@code new FieldSpec("amount", NUMBER, true).doc("…")}.
 */
public record FieldSpec(
    String name,
    FormFieldSpec.Kind kind,
    boolean required,
    Object defaultValue,
    List<String> enumValues,
    Double min,
    Double max,
    String description,
    String ref
) {
    public FieldSpec {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    /** Minimal spec — name, widget kind, required-ness. Enrich via the fluent withers. */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required) {
        this(name, kind, required, null, List.of(), null, null, null, null);
    }

    /** Attach the human-readable help string (D2: doc lives on the spec). */
    public FieldSpec doc(String description) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref);
    }

    /** Set the schema {@code default} value. */
    public FieldSpec def(Object defaultValue) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref);
    }

    /** Set a numeric range (schema {@code minimum}/{@code maximum}). */
    public FieldSpec range(Double min, Double max) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref);
    }

    /** Set the allowed values for an {@link FormFieldSpec.Kind#ENUM} field. */
    public FieldSpec options(String... values) {
        return new FieldSpec(name, kind, required, defaultValue, List.of(values), min, max, description, ref);
    }

    /** Set the {@code $ref} target for a {@link FormFieldSpec.Kind#REF} field. */
    public FieldSpec ref(String ref) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref);
    }

    /**
     * Project this descriptor field onto the renderer-facing {@link FormFieldSpec}.
     * Compile-time proof that the descriptor subsumes the in-game form contract;
     * the Phase-4 picker work sources its fields through here.
     */
    public FormFieldSpec toFormSpec() {
        return new FormFieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref);
    }
}
