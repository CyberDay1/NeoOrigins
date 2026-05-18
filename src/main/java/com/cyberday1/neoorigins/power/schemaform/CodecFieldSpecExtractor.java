package com.cyberday1.neoorigins.power.schemaform;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Hybrid-fallback field-spec source for the in-game creator: when a power has
 * no structured branch in {@code power.schema.json}, derive its
 * {@link FormFieldSpec}s by reflecting its {@code Config} record.
 *
 * <p>The 2.0 architecture made every power's config a Java record implementing
 * {@code PowerConfiguration}, and this codebase's codecs follow a strict
 * convention: record component {@code fooBar} ↔ JSON key {@code foo_bar}
 * (verified across builtin powers, e.g. {@code damageTypes ↔ "damage_types"}).
 * So {@link Class#getRecordComponents()} + camel→snake yields the JSON shape
 * without needing to read the codec lambdas.
 *
 * <p>Known limitations (carried forward from spike validation, not bugs):
 * <ul>
 *   <li>Codec {@code optionalFieldOf} <em>default values</em> live in the
 *       codec lambda and are not visible via record reflection. Only
 *       {@code Optional<>}-typed components are provably optional; others are
 *       reported required-unknown. {@code POWER_OVERRIDES} supplies min/max
 *       (and effective defaults) for the ranged numeric knobs.</li>
 *   <li>A {@code String} component whose codec maps it to an enum can't be
 *       distinguished from free text by reflection (no enum type to read); a
 *       curated enum-hint table covers the known cases.</li>
 * </ul>
 */
public final class CodecFieldSpecExtractor {

    /** Components that are internal plumbing, never user-facing form fields. */
    private static final List<String> INTERNAL = List.of("type", "powerId");

    private CodecFieldSpecExtractor() {}

    /**
     * @param configRecord a {@code *Power.Config} record class
     * @return one {@link FormFieldSpec} per user-facing record component
     */
    public static List<FormFieldSpec> extract(Class<?> configRecord) {
        if (!configRecord.isRecord()) {
            throw new IllegalArgumentException(configRecord + " is not a record");
        }
        List<FormFieldSpec> out = new ArrayList<>();
        for (RecordComponent rc : configRecord.getRecordComponents()) {
            if (INTERNAL.contains(rc.getName())) continue;
            out.add(mapComponent(rc));
        }
        return out;
    }

    private static FormFieldSpec mapComponent(RecordComponent rc) {
        String json = camelToSnake(rc.getName());
        Class<?> raw = rc.getType();
        Type generic = rc.getGenericType();

        boolean optional = raw == Optional.class;
        Class<?> effective = optional ? typeArgRaw(generic) : raw;

        FormFieldSpec.Kind kind;
        List<String> enumVals = List.of();

        if (effective == null) {
            kind = FormFieldSpec.Kind.UNKNOWN;
        } else if (effective == boolean.class || effective == Boolean.class) {
            kind = FormFieldSpec.Kind.BOOLEAN;
        } else if (effective == int.class || effective == Integer.class
                || effective == long.class || effective == Long.class) {
            kind = FormFieldSpec.Kind.INTEGER;
        } else if (effective == float.class || effective == Float.class
                || effective == double.class || effective == Double.class) {
            kind = FormFieldSpec.Kind.NUMBER;
        } else if (effective == String.class) {
            kind = FormFieldSpec.Kind.STRING;
        } else if (effective.isEnum()) {
            kind = FormFieldSpec.Kind.ENUM;
            List<String> v = new ArrayList<>();
            for (Object c : effective.getEnumConstants()) v.add(((Enum<?>) c).name());
            enumVals = v;
        } else if (List.class.isAssignableFrom(effective)) {
            kind = FormFieldSpec.Kind.ARRAY;
        } else if (isDslRef(effective)) {
            kind = FormFieldSpec.Kind.REF;
        } else if (effective.isRecord()) {
            kind = FormFieldSpec.Kind.OBJECT;
        } else {
            kind = FormFieldSpec.Kind.UNKNOWN;
        }

        String ref = kind == FormFieldSpec.Kind.REF ? effective.getSimpleName() : null;
        // required: only provable when NOT Optional-typed; reflection can't see
        // optionalFieldOf defaults, so non-Optional is reported required(true)
        // with the caveat documented at class level.
        return new FormFieldSpec(json, kind, !optional, null, enumVals, null, null,
            null, ref);
    }

    /** EntityCondition / EntityAction / ItemCondition etc. → reuse a sub-form. */
    private static boolean isDslRef(Class<?> c) {
        String n = c.getName();
        return n.contains(".compat.condition.") || n.contains(".compat.action.");
    }

    private static Class<?> typeArgRaw(Type generic) {
        if (generic instanceof ParameterizedType pt && pt.getActualTypeArguments().length > 0) {
            Type a = pt.getActualTypeArguments()[0];
            if (a instanceof Class<?> c) return c;
            if (a instanceof ParameterizedType apt && apt.getRawType() instanceof Class<?> rc) return rc;
        }
        return null;
    }

    static String camelToSnake(String s) {
        StringBuilder b = new StringBuilder(s.length() + 4);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isUpperCase(ch)) {
                if (i > 0) b.append('_');
                b.append(Character.toLowerCase(ch));
            } else {
                b.append(ch);
            }
        }
        return b.toString();
    }
}
