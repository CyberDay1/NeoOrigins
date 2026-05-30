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
 * {@link #range}, {@link #options}, {@link #ref}, {@link #boundTo}) each return a
 * new instance, so a spec reads as {@code new FieldSpec("amount", NUMBER, true).doc("…")}.
 *
 * <p><b>Key-alias.</b> Most powers' JSON keys equal the camel→snake of their
 * {@code Config} record component, so {@link #name} alone resolves both. A few
 * codecs deliberately read a field under a JSON key that differs from the
 * component (e.g. {@code action_on_event} reads its {@code EntityAction action}
 * component from the {@code entity_action} JSON key). For those, {@link #name}
 * stays the author-facing JSON key and {@link #componentName} carries the real
 * record-component name (camelCase) so the drift audit
 * ({@code SchemaFormCheck.auditPowerFieldSpecs}) resolves the component via
 * {@link #boundTo} instead of camel→snake-ing the JSON key. {@code null} (the
 * default) means "component == camel→snake(name)" — i.e. no regression for the
 * already-registered powers.
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
    String ref,
    String componentName,
    List<FieldSpec> children,
    boolean virtual
) {
    public FieldSpec {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        children = children == null ? List.of() : List.copyOf(children);
    }

    /** Full constructor without children/virtual — back-compat for the key-aliased ctor. */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required, Object defaultValue,
                     List<String> enumValues, Double min, Double max, String description, String ref,
                     String componentName) {
        this(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName,
             List.of(), false);
    }

    /** Full constructor without the key-alias — component defaults to camel→snake(name). */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required, Object defaultValue,
                     List<String> enumValues, Double min, Double max, String description, String ref) {
        this(name, kind, required, defaultValue, enumValues, min, max, description, ref, null,
             List.of(), false);
    }

    /** Minimal spec — name, widget kind, required-ness. Enrich via the fluent withers. */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required) {
        this(name, kind, required, null, List.of(), null, null, null, null, null, List.of(), false);
    }

    /** Attach the human-readable help string (D2: doc lives on the spec). */
    public FieldSpec doc(String description) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual);
    }

    /** Set the schema {@code default} value. */
    public FieldSpec def(Object defaultValue) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual);
    }

    /** Set a numeric range (schema {@code minimum}/{@code maximum}). */
    public FieldSpec range(Double min, Double max) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual);
    }

    /** Set the allowed values for an {@link FormFieldSpec.Kind#ENUM} field. */
    public FieldSpec options(String... values) {
        return new FieldSpec(name, kind, required, defaultValue, List.of(values), min, max, description, ref, componentName, children, virtual);
    }

    /** Set the {@code $ref} target for a {@link FormFieldSpec.Kind#REF} field. */
    public FieldSpec ref(String ref) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual);
    }

    /**
     * Bind this spec's JSON {@link #name} to a differently-named {@code Config}
     * record component (camelCase). Use only when the codec reads the field
     * under a JSON key that is NOT the camel→snake of its component — e.g.
     * {@code new FieldSpec("entity_action", REF, false).boundTo("action")} for
     * a codec whose {@code EntityAction action} component is keyed
     * {@code entity_action}. When unset the component is {@code camel→snake(name)}.
     */
    public FieldSpec boundTo(String componentName) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual);
    }

    /**
     * Attach nested sub-record fields to an {@link FormFieldSpec.Kind#OBJECT}
     * spec that HAS a backing {@code Config} component of its own (a real
     * sub-record). The children resolve against that component-record's own
     * component map (drift-audit "real OBJECT with children" path).
     */
    public FieldSpec children(FieldSpec... kids) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, List.of(kids), false);
    }

    /**
     * Mark this {@link FormFieldSpec.Kind#OBJECT} spec as a VIRTUAL nested JSON
     * wrapper that has NO backing {@code Config} component of its own (e.g.
     * {@code hud_render}, whose {@code label}/{@code color}/{@code should_render}
     * keys are stored on FLATTENED top-level components via {@code .boundTo}).
     * The drift audit skips component-existence/Optional-ness for the wrapper
     * itself and resolves each child against the SAME top-level component map.
     */
    public FieldSpec virtualObject(FieldSpec... kids) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, List.of(kids), true);
    }

    /** True when this OBJECT is a virtual wrapper with no backing component of its own. */
    public boolean isVirtual() {
        return virtual;
    }

    /**
     * The {@code Config} record component (camelCase) this spec maps to: the
     * explicit {@link #componentName} key-alias when set, else {@link #name}
     * itself (the audit camel→snakes both sides, so an un-aliased snake-case
     * JSON name round-trips to its component). Drives component resolution in
     * {@code SchemaFormCheck.auditPowerFieldSpecs}.
     */
    public String effectiveComponentName() {
        return componentName != null ? componentName : name;
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
