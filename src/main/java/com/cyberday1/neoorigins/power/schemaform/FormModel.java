package com.cyberday1.neoorigins.power.schemaform;

import com.cyberday1.neoorigins.NeoOriginsConfig;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The hybrid field-spec source the 2.1 creator's Powers tab renders from.
 * Resolves, per power type, the {@link FormFieldSpec} list by:
 *
 * <ol>
 *   <li><b>Schema</b> — if {@code power.schema.json} has a structured branch for
 *       the type, use it ({@link SchemaFormModel#formFor}); else</li>
 *   <li><b>Codec reflection</b> — reflect the power's {@code Config} record via
 *       {@link PowerConfigClassResolver} + {@link CodecFieldSpecExtractor}.</li>
 * </ol>
 *
 * <p>The result is then enriched: {@link EnumHints} turns String-backed
 * vocabularies into dropdowns, and {@link NeoOriginsConfig} supplies numeric
 * (min,max) ranges and effective defaults that record reflection / the schema
 * can't see (codec {@code optionalFieldOf} hides them). This makes the schema
 * authoritative where it exists and reflection the safety net everywhere else
 * — the permanent fix for the historical schema-coverage gap.
 */
public final class FormModel {

    private static SchemaFormModel schema;

    private FormModel() {}

    private static synchronized SchemaFormModel schema() {
        if (schema == null) schema = SchemaFormModel.loadFromClasspath();
        return schema;
    }

    /** Every power type id in the schema's {@code type} enum, sorted. Includes
     *  compat aliases — use for validation/tooling, NOT the authoring UI. */
    public static List<String> allTypes() {
        return new ArrayList<>(schema().allTypes());
    }

    /**
     * Native {@code neoorigins:} power types only, for the creator's type
     * picker. Two categories are hidden:
     * <ul>
     *   <li>Foreign-namespace entries in the schema enum ({@code apugli:},
     *       {@code apoli:}, {@code apace:}, …) — compat-translation aliases
     *       for importing existing datapacks; never the right choice when
     *       authoring a brand-new power.</li>
     *   <li>Native {@code neoorigins:} types that have been retired in 2.0
     *       and aliased to a generic replacement (see {@link
     *       com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases}).
     *       Their Java class is gone, so the form has nothing to render
     *       (empty-form footgun) and saving lands a power whose type the
     *       loader rewrites on every reload (lossy round-trip).</li>
     * </ul>
     * Existing on-disk JSON using retired ids keeps loading transparently
     * via the alias remap.
     */
    public static List<String> creatorTypes() {
        java.util.Set<net.minecraft.resources.Identifier> retired =
            com.cyberday1.neoorigins.power.registry.LegacyPowerTypeAliases.aliasedTypeIds();
        return schema().allTypes().stream()
            .filter(t -> t.startsWith("neoorigins:"))
            .filter(t -> {
                net.minecraft.resources.Identifier rl =
                    net.minecraft.resources.Identifier.tryParse(t);
                return rl == null || !retired.contains(rl);
            })
            .toList();
    }

    /** True when the schema carries a structured (field-typed) branch for the type. */
    public static boolean isSchemaBacked(Identifier typeId) {
        return schema().hasStructuredForm(typeId.toString());
    }

    /**
     * The renderer-ready field list for {@code typeId}. Never null; an
     * unresolvable/markerless power yields an empty list (the raw-JSON escape
     * hatch still covers it).
     */
    public static List<FormFieldSpec> forPower(Identifier typeId) {
        String key = typeId.toString();
        List<FormFieldSpec> base;

        if (schema().hasStructuredForm(key)) {
            base = schema().formFor(key);
        } else {
            Class<?> cfg = PowerConfigClassResolver.resolve(typeId);
            base = (cfg != null && cfg.isRecord())
                ? CodecFieldSpecExtractor.extract(cfg)
                : List.of();
        }

        List<FormFieldSpec> out = new ArrayList<>(base.size());
        for (FormFieldSpec s : base) out.add(enrich(key, s));
        return out;
    }

    /** EnumHints overlay + config-range/default enrich + doc fallback for one field. */
    private static FormFieldSpec enrich(String powerId, FormFieldSpec s) {
        FormFieldSpec.Kind kind = s.kind();
        List<String> enums = s.enumValues();
        Double min = s.min(), max = s.max();
        Object def = s.defaultValue();
        String desc = s.description();
        if (desc == null || desc.isBlank()) {
            desc = FieldDocs.get().describe(powerId, s.name());
        }

        List<String> hint = EnumHints.valuesFor(powerId, s.name());
        if (!hint.isEmpty()) {
            kind = FormFieldSpec.Kind.ENUM;
            enums = hint;
        }

        if (kind == FormFieldSpec.Kind.NUMBER || kind == FormFieldSpec.Kind.INTEGER) {
            if (min == null || max == null) {
                NeoOriginsConfig.NumericRange r = NeoOriginsConfig.getPowerRange(powerId, s.name());
                if (r != null) { min = r.min(); max = r.max(); }
            }
            if (def == null) {
                Object cd = NeoOriginsConfig.getPowerDefault(powerId, s.name());
                if (cd != null) def = cd;
            }
        }

        if (kind == s.kind() && enums == s.enumValues()
                && min == s.min() && max == s.max() && def == s.defaultValue()
                && java.util.Objects.equals(desc, s.description())) {
            return s; // untouched — avoid a needless copy
        }
        return new FormFieldSpec(s.name(), kind, s.required(), def, enums,
            min, max, desc, s.ref());
    }
}
