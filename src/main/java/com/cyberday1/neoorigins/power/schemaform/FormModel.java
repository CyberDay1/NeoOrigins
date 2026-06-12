package com.cyberday1.neoorigins.power.schemaform;

import com.cyberday1.neoorigins.config.PowerOverridesConfig;
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
 * vocabularies into dropdowns, and {@link com.cyberday1.neoorigins.config.PowerOverridesConfig} supplies numeric
 * (min,max) ranges and effective defaults that record reflection / the schema
 * can't see (codec {@code optionalFieldOf} hides them). This makes the schema
 * authoritative where it exists and reflection the safety net everywhere else
 * — the permanent fix for the historical schema-coverage gap.
 */
public final class FormModel {

    private static SchemaFormModel schema;
    private static SchemaFormModel actionSchema;
    private static SchemaFormModel conditionSchema;
    private static SchemaFormModel blockConditionSchema;
    private static SchemaFormModel itemConditionSchema;
    private static SchemaFormModel itemActionSchema;

    private FormModel() {}

    private static synchronized SchemaFormModel schema() {
        if (schema == null) schema = SchemaFormModel.loadFromClasspath();
        return schema;
    }

    private static synchronized SchemaFormModel actionSchema() {
        if (actionSchema == null) {
            actionSchema = SchemaFormModel.loadFromClasspath(
                SchemaFormModel.ACTION_RESOURCE_PATH);
        }
        return actionSchema;
    }

    private static synchronized SchemaFormModel conditionSchema() {
        if (conditionSchema == null) {
            conditionSchema = SchemaFormModel.loadFromClasspath(
                SchemaFormModel.CONDITION_RESOURCE_PATH);
        }
        return conditionSchema;
    }

    private static synchronized SchemaFormModel blockConditionSchema() {
        if (blockConditionSchema == null) {
            blockConditionSchema = SchemaFormModel.loadFromClasspath(
                SchemaFormModel.BLOCK_CONDITION_RESOURCE_PATH);
        }
        return blockConditionSchema;
    }

    private static synchronized SchemaFormModel itemConditionSchema() {
        if (itemConditionSchema == null) {
            itemConditionSchema = SchemaFormModel.loadFromClasspath(
                SchemaFormModel.ITEM_CONDITION_RESOURCE_PATH);
        }
        return itemConditionSchema;
    }

    private static synchronized SchemaFormModel itemActionSchema() {
        if (itemActionSchema == null) {
            itemActionSchema = SchemaFormModel.loadFromClasspath(
                SchemaFormModel.ITEM_ACTION_RESOURCE_PATH);
        }
        return itemActionSchema;
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

        // Registry-refactor preference order (mirrors the verb migration): a
        // power registered in BuiltinPowers declares its fields directly, so
        // that spec is authoritative — project each FieldSpec onto the
        // renderer-facing FormFieldSpec. A registered marker-only power yields
        // an empty list (correct: nothing to author), which is byte-identical
        // to the codec-reflection-empty result it replaced. When a power is NOT
        // registered here yet (fieldsFor == null), fall back to the EXISTING
        // path — schema branch, else Config-record reflection — unchanged.
        java.util.List<com.cyberday1.neoorigins.compat.registry.FieldSpec> declared =
            com.cyberday1.neoorigins.power.registry.BuiltinPowers.fieldsFor(typeId);
        if (declared != null) {
            base = new ArrayList<>(declared.size());
            for (com.cyberday1.neoorigins.compat.registry.FieldSpec fs : declared) {
                base.add(fs.toFormSpec());
            }
        } else if (schema().hasStructuredForm(key)) {
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

    /**
     * Renderer-ready field list for a DSL action ({@code entity_action} REF
     * values like {@code neoorigins:add_velocity}). Empty when the action has
     * no structured schema branch yet (~40 actions are still raw-JSON-only —
     * the existing schema covers ~21 with declared fields).
     */
    public static List<FormFieldSpec> forAction(String typeId) {
        return formForRef(actionSchema(), typeId);
    }

    /** Renderer-ready field list for a DSL condition ({@code condition} REF values). */
    public static List<FormFieldSpec> forCondition(String typeId) {
        return formForRef(conditionSchema(), typeId);
    }

    /**
     * Renderer-ready field list for a nested {@code block_condition} sub-shape
     * (REF values like {@code neoorigins:block} / {@code in_tag} / {@code and}).
     * Empty when the id has no structured branch.
     */
    public static List<FormFieldSpec> forBlockCondition(String typeId) {
        return formForRef(blockConditionSchema(), typeId);
    }

    /** Native {@code neoorigins:} block_condition type ids for the type picker
     *  (apace: aliases filtered out as import-only noise, mirroring creatorTypes). */
    public static List<String> blockConditionTypes() {
        return blockConditionSchema().allTypes().stream()
            .filter(t -> t.startsWith("neoorigins:"))
            .toList();
    }

    /** True when a block_condition id has a structured schema branch. */
    public static boolean hasBlockConditionForm(String typeId) {
        return blockConditionSchema().hasStructuredForm(typeId);
    }

    /**
     * Renderer-ready field list for a nested {@code item_condition} sub-shape
     * (REF values like {@code neoorigins:enchantment} / {@code nbt} / {@code and}).
     * Empty when the id has no structured branch.
     */
    public static List<FormFieldSpec> forItemCondition(String typeId) {
        return formForRef(itemConditionSchema(), typeId);
    }

    /** Native {@code neoorigins:} item_condition type ids for the type picker
     *  (apace: aliases filtered out as import-only noise, mirroring creatorTypes). */
    public static List<String> itemConditionTypes() {
        return itemConditionSchema().allTypes().stream()
            .filter(t -> t.startsWith("neoorigins:"))
            .toList();
    }

    /** True when an item_condition id has a structured schema branch. */
    public static boolean hasItemConditionForm(String typeId) {
        return itemConditionSchema().hasStructuredForm(typeId);
    }

    /**
     * Renderer-ready field list for a nested {@code item_action} sub-shape
     * (REF values like {@code neoorigins:consume} / {@code damage} / {@code and}).
     * Empty when the id has no structured branch.
     */
    public static List<FormFieldSpec> forItemAction(String typeId) {
        return formForRef(itemActionSchema(), typeId);
    }

    /** Native {@code neoorigins:} item_action type ids for the type picker
     *  (apace: aliases filtered out as import-only noise, mirroring creatorTypes). */
    public static List<String> itemActionTypes() {
        return itemActionSchema().allTypes().stream()
            .filter(t -> t.startsWith("neoorigins:"))
            .toList();
    }

    /** True when an item_action id has a structured schema branch. */
    public static boolean hasItemActionForm(String typeId) {
        return itemActionSchema().hasStructuredForm(typeId);
    }

    /** True when an action id has a structured schema branch (renderable as a sub-form). */
    public static boolean hasActionForm(String typeId) {
        return actionSchema().hasStructuredForm(typeId);
    }

    /** True when a condition id has a structured schema branch. */
    public static boolean hasConditionForm(String typeId) {
        return conditionSchema().hasStructuredForm(typeId);
    }

    private static List<FormFieldSpec> formForRef(SchemaFormModel s, String typeId) {
        if (!s.hasStructuredForm(typeId)) return List.of();
        List<FormFieldSpec> base = s.formFor(typeId);
        List<FormFieldSpec> out = new ArrayList<>(base.size());
        for (FormFieldSpec spec : base) out.add(enrich(typeId, spec));
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
                PowerOverridesConfig.NumericRange r = PowerOverridesConfig.getPowerRange(powerId, s.name());
                if (r != null) { min = r.min(); max = r.max(); }
            }
            if (def == null) {
                Object cd = PowerOverridesConfig.getPowerDefault(powerId, s.name());
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
