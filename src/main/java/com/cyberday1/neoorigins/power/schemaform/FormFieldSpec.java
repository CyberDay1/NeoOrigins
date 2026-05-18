package com.cyberday1.neoorigins.power.schemaform;

import java.util.List;

/**
 * The renderer-agnostic description of one editable field in the in-game
 * origin/class creator, derived from {@code power.schema.json} where a
 * structured branch exists and otherwise from {@code Config}-record reflection
 * (see {@link CodecFieldSpecExtractor}), then enriched with
 * {@code NeoOriginsConfig.POWER_OVERRIDES} min/max metadata.
 *
 * <p>This is the contract the creator's widget layer consumes: each
 * {@code FormFieldSpec} maps to one concrete {@code Screen} widget by its
 * {@link Kind}.
 *
 * @param name         JSON key this field writes to.
 * @param kind         which widget class is appropriate.
 * @param required     true if the power's schema branch lists it in {@code required}.
 * @param defaultValue schema {@code default} (boxed; may be {@code null}).
 * @param enumValues   allowed values when {@link Kind#ENUM}; empty otherwise.
 * @param min          numeric lower bound (schema {@code minimum}/{@code exclusiveMinimum}
 *                     or config range); {@code null} when unbounded/unknown.
 * @param max          numeric upper bound; {@code null} when unbounded/unknown.
 * @param description  human help text from the schema; may be {@code null}.
 * @param ref          target of a schema {@code $ref} (e.g. condition.schema.json)
 *                     when {@link Kind#REF}; {@code null} otherwise.
 */
public record FormFieldSpec(
    String name,
    Kind kind,
    boolean required,
    Object defaultValue,
    List<String> enumValues,
    Double min,
    Double max,
    String description,
    String ref
) {
    public enum Kind {
        /** free text → text box */
        STRING,
        /** whole number → integer spinner */
        INTEGER,
        /** decimal → number spinner (slider when min+max known) */
        NUMBER,
        /** true/false → toggle */
        BOOLEAN,
        /** fixed value set → dropdown */
        ENUM,
        /** nested object → sub-form / raw-JSON sub-editor */
        OBJECT,
        /** list → list editor */
        ARRAY,
        /** schema $ref → reuse the referenced schema's sub-form (e.g. condition) */
        REF,
        /** schema oneOf of scalars (e.g. string|object name) → text box w/ JSON escape */
        MIXED,
        /** registered but no schema info → raw-JSON only (the coverage gap) */
        UNKNOWN
    }

    /** True when this field carries a usable numeric range (slider-eligible). */
    public boolean hasRange() {
        return (kind == Kind.NUMBER || kind == Kind.INTEGER) && min != null && max != null;
    }
}
