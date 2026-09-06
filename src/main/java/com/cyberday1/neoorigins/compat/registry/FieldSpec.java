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
 *
 * <p><b>Array element refs.</b> {@link #itemsRef} mirrors the in-game
 * {@link FormFieldSpec#itemsRef()}: on a {@link FormFieldSpec.Kind#ARRAY} field
 * it names the {@code items.$ref} target (e.g. {@code "#"} for a list of the same
 * doc, or {@code "condition.schema.json"} for a cross-doc list) so the generated
 * schema emits {@code {"type":"array","items":{"$ref":…}}} and the editors render
 * a list-of-sub-forms instead of a raw-JSON box. {@code null} → a permissive
 * scalar array ({@code items:{}}).
 *
 * <p><b>Array element shape.</b> An array whose elements are neither a cross-doc
 * {@code $ref} nor a scalar string — a list of fixed-shape OBJECTS such as
 * {@code edible_item.tiers} — declares its element's fields via {@link #children},
 * which on a {@link FormFieldSpec.Kind#ARRAY} means "one element's shape" (see that
 * method). The generated schema then emits
 * {@code items:{type:object,properties:…,required:…}} and the editors render a
 * repeatable sub-form.
 *
 * <p><b>Mixed type arms.</b> {@link #mixedTypes} lists the JSON types a
 * {@link FormFieldSpec.Kind#MIXED} field genuinely accepts, emitted as the
 * {@code oneOf} arms. Empty (the default) keeps the historical
 * {@code oneOf:[{"type":"string"},{"type":"object"}]} shape, so only fields that
 * ask for something else — e.g. {@code key}, which the loader reads as a number,
 * a string, or an object — widen it. SCHEMA = PARSER: the arms must be what the
 * parser actually accepts.
 *
 * <p><b>Externally-read fields.</b> {@link #readBy} records that this field has
 * NO backing {@code Config} record component because it is read straight off the
 * raw power JSON by another class, named {@code fully.qualified.Class#method}.
 * The drift audit ({@code SchemaFormCheck.auditSpec}) treats it as an explicit,
 * staleness-checked exception: it fails if the named reader's source no longer
 * exists, no longer contains that method, no longer mentions this field's JSON
 * key, or if the field HAS gained a real component (the exception is then obsolete
 * and must be deleted). Never use it to silence an ordinary missing component.
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
    boolean virtual,
    String pattern,
    String itemsRef,
    String itemPattern,
    List<String> mixedTypes,
    String readBy
) {
    public FieldSpec {
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
        children = children == null ? List.of() : List.copyOf(children);
        mixedTypes = mixedTypes == null ? List.of() : List.copyOf(mixedTypes);
    }

    /** Full constructor without children/virtual — back-compat for the key-aliased ctor. */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required, Object defaultValue,
                     List<String> enumValues, Double min, Double max, String description, String ref,
                     String componentName) {
        this(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName,
             List.of(), false, null, null, null, List.of(), null);
    }

    /** Full constructor without the key-alias — component defaults to camel→snake(name). */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required, Object defaultValue,
                     List<String> enumValues, Double min, Double max, String description, String ref) {
        this(name, kind, required, defaultValue, enumValues, min, max, description, ref, null,
             List.of(), false, null, null, null, List.of(), null);
    }

    /** Minimal spec — name, widget kind, required-ness. Enrich via the fluent withers. */
    public FieldSpec(String name, FormFieldSpec.Kind kind, boolean required) {
        this(name, kind, required, null, List.of(), null, null, null, null, null, List.of(), false, null, null, null, List.of(), null);
    }

    /** Attach the human-readable help string (D2: doc lives on the spec). */
    public FieldSpec doc(String description) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /** Set the schema {@code default} value. */
    public FieldSpec def(Object defaultValue) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /** Set a numeric range (schema {@code minimum}/{@code maximum}). */
    public FieldSpec range(Double min, Double max) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /** Set the allowed values for an {@link FormFieldSpec.Kind#ENUM} field. */
    public FieldSpec options(String... values) {
        return new FieldSpec(name, kind, required, defaultValue, List.of(values), min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /** Set the {@code $ref} target for a {@link FormFieldSpec.Kind#REF} field. */
    public FieldSpec ref(String ref) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /**
     * Set the {@code items.$ref} target for a {@link FormFieldSpec.Kind#ARRAY}
     * field — the array's element type (e.g. {@code "#"} for a same-doc list, or
     * {@code "condition.schema.json"} for a cross-doc list). Drives the generated
     * {@code items.$ref} and the editors' list-of-sub-forms rendering.
     */
    public FieldSpec itemsRef(String itemsRef) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
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
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /**
     * Attach nested sub-fields.
     *
     * <p>On an {@link FormFieldSpec.Kind#OBJECT} spec that HAS a backing
     * {@code Config} component of its own (a real sub-record) these are the
     * object's own properties, and they resolve against that component-record's
     * own component map (drift-audit "real OBJECT with children" path).
     *
     * <p>On an {@link FormFieldSpec.Kind#ARRAY} spec they are the shape of ONE
     * ELEMENT — the array is a list of fixed-shape objects (e.g.
     * {@code edible_item.tiers}, {@code kill_loot_drops.drops}). The generated
     * schema emits them under {@code items.properties}/{@code items.required}
     * instead of the permissive {@code items:{}}, and both editors then render a
     * repeatable sub-form rather than a raw-JSON box. This is the same overload
     * {@link FormFieldSpec#children()} already carries — there, an ARRAY's
     * children have always meant "the element's fields" (that is what
     * {@code SchemaFormModel} writes when it reads {@code items.properties}, and
     * what {@code FieldWidgetFactory}'s {@code ArrayObjectRow} consumes) — so
     * reusing the same component here keeps the two records mirror-exact rather
     * than adding an {@code itemChildren} slot that would have to be flattened
     * back into {@code children} by {@link #toFormSpec()} anyway.
     *
     * <p>An ARRAY's children are NOT audited against the owner's {@code Config}
     * components: the element is parsed by its own element codec
     * ({@code EdibleItemPower.Tier.CODEC}, {@code EntityTargetSpec.CODEC}), whose
     * author-facing keys need not match any record component — see
     * {@code SchemaFormCheck.auditSpec}.
     */
    public FieldSpec children(FieldSpec... kids) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, List.of(kids), false, pattern, itemsRef, itemPattern, mixedTypes, readBy);
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
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, List.of(kids), true, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /**
     * Attach a JSON-Schema {@code pattern} (regex) to a
     * {@link FormFieldSpec.Kind#STRING} field — emitted into the generated
     * schema's field node and surfaced by the web editor as a format hint
     * (see {@code StringFieldSpec.pattern}). Used for resource-location-shaped
     * string fields (e.g. {@code loot_table}, {@code attribute}) that the
     * hand-written schema validated against {@code ^[a-z0-9_.-]+:[a-z0-9_./-]+$}.
     */
    public FieldSpec pattern(String pattern) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /**
     * On a {@link FormFieldSpec.Kind#ARRAY} field with NO {@link #itemsRef} (a list
     * of scalars rather than sub-forms), declare the array's STRING elements and
     * their validation regex. Drives the generated {@code items:{type:string,pattern}}
     * and flips the editors from a raw-JSON box to a list-of-text-inputs widget. The
     * presence of {@code itemPattern} on a no-{@code itemsRef} array is the
     * scalar-string-list discriminator. (Resource-location lists use
     * {@code ^[a-z0-9_.-]+:[a-z0-9_./-]+$}.)
     */
    public FieldSpec itemPattern(String itemPattern) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, readBy);
    }

    /**
     * Declare the JSON types a {@link FormFieldSpec.Kind#MIXED} field accepts, in
     * emission order — e.g. {@code mixedTypes("integer", "string", "object")} for a
     * field the parser reads as a raw number, a token string, or an options object.
     * Unset keeps the default {@code string | object} pair.
     */
    public FieldSpec mixedTypes(String... types) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, List.of(types), readBy);
    }

    /**
     * Record that this field is read off the raw power JSON by {@code reader}
     * (format {@code fully.qualified.Class#method}) and therefore has no backing
     * {@code Config} record component. This is an explicit exception to the
     * component-existence audit, and it is staleness-checked — see the class doc.
     */
    public FieldSpec readBy(String reader) {
        return new FieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, componentName, children, virtual, pattern, itemsRef, itemPattern, mixedTypes, reader);
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
     *
     * <p>An {@link FormFieldSpec.Kind#OBJECT} spec's {@link #children} (real or
     * {@link #virtualObject() virtual} — the {@code virtual} flag is a parse-time
     * binding detail the renderer doesn't care about) are projected recursively so
     * the creator renders the nested sub-form instead of a raw-JSON box.
     */
    public FormFieldSpec toFormSpec() {
        return new FormFieldSpec(name, kind, required, defaultValue, enumValues, min, max, description, ref, itemsRef,
            children.stream().map(FieldSpec::toFormSpec).toList(), itemPattern, false, pattern);
    }
}
