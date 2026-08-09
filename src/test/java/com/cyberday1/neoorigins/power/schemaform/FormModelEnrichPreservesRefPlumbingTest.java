package com.cyberday1.neoorigins.power.schemaform;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FormModel}'s {@code enrich} pass overlays a {@code FieldDocs}
 * description, an {@code EnumHints} enum, and config-driven ranges/defaults onto
 * each field. It used to rebuild the result through the 9-argument back-compat
 * {@link FormFieldSpec} constructor, which silently defaults {@code itemsRef},
 * {@code children}, {@code itemPattern} and {@code scalarOrArray}.
 *
 * <p>That copy is taken whenever enrich changes anything, and the description
 * fallback alone fires for most schema-derived fields, which carry no inline doc
 * string. {@code FieldWidgetFactory} only builds an {@code ArrayRefRow} when
 * {@code spec.itemsRef() != null}, so the enrich pass was knocking the composite
 * {@code and} / {@code or} types out of their array widget and into a degraded
 * one — on the most commonly authored verbs in the DSL.
 *
 * <p>The composites are the reachable case: their {@code actions} /
 * {@code conditions} arrays are the only array-of-$ref fields in the action,
 * condition, block_condition, item_condition and item_action schemas, and they
 * all resolve through {@code formForRef}, which enriches.
 */
class FormModelEnrichPreservesRefPlumbingTest {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static FormFieldSpec field(List<FormFieldSpec> form, String name) {
        return form.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    /** The array field must keep the itemsRef the widget factory dispatches on. */
    private static void assertArrayOfRefs(List<FormFieldSpec> form, String fieldName, String what) {
        assertTrue(!form.isEmpty(), what + " has no structured form at all");
        FormFieldSpec spec = field(form, fieldName);
        assertNotNull(spec, what + " is missing its '" + fieldName + "' field");
        assertEquals(FormFieldSpec.Kind.ARRAY, spec.kind(),
            what + "." + fieldName + " should render as an ARRAY");
        assertNotNull(spec.itemsRef(),
            what + "." + fieldName + " lost its itemsRef — FieldWidgetFactory falls back "
                + "off the ArrayRefRow branch, so the author cannot add entries");
    }

    @Test
    void andActionKeepsItsActionsArrayRef() {
        assertArrayOfRefs(FormModel.forAction("neoorigins:and"), "actions", "action neoorigins:and");
    }

    @Test
    void andOrConditionsKeepTheirConditionsArrayRef() {
        assertArrayOfRefs(FormModel.forCondition("neoorigins:and"), "conditions", "condition neoorigins:and");
        assertArrayOfRefs(FormModel.forCondition("neoorigins:or"), "conditions", "condition neoorigins:or");
    }

    @Test
    void nestedSubShapeCompositesKeepTheirConditionsArrayRef() {
        assertArrayOfRefs(FormModel.forBlockCondition("neoorigins:and"), "conditions",
            "block_condition neoorigins:and");
        assertArrayOfRefs(FormModel.forItemCondition("neoorigins:and"), "conditions",
            "item_condition neoorigins:and");
        assertArrayOfRefs(FormModel.forItemAction("neoorigins:and"), "actions",
            "item_action neoorigins:and");
    }

    /**
     * The other half of the same plumbing: a list of plain ids reaches its widget
     * through {@code itemPattern} rather than {@code itemsRef}.
     * {@code FieldWidgetFactory.isScalarStringList} demands a non-null
     * {@code itemPattern} AND no {@code itemsRef}, so both halves are asserted.
     */
    private static void assertScalarStringList(List<FormFieldSpec> form, String fieldName, String what) {
        assertTrue(!form.isEmpty(), what + " has no structured form at all");
        FormFieldSpec spec = field(form, fieldName);
        assertNotNull(spec, what + " is missing its '" + fieldName + "' field");
        assertEquals(FormFieldSpec.Kind.ARRAY, spec.kind(),
            what + "." + fieldName + " should render as an ARRAY");
        assertNull(spec.itemsRef(),
            what + "." + fieldName + " is a list of plain ids and must not carry an itemsRef, "
                + "which would send it down the ArrayRefRow branch instead");
        assertNotNull(spec.itemPattern(),
            what + "." + fieldName + " lost its itemPattern, so isScalarStringList is false and "
                + "FieldWidgetFactory drops the field to the raw-JSON TextRow");
    }

    /**
     * Regression guard for the gap shipped in 2.2.23: {@code SchemaFormModel} never
     * carried {@code items.pattern} across from the schema, so {@code itemPattern}
     * was always null, {@code isScalarStringList} was never true, and
     * {@code ArrayStringRow} was dead code for all 39 schema-derived id lists. The
     * failure was completely silent - the widget and its gate both existed and
     * nothing errored - which is exactly why it needs a test rather than a reading.
     */
    @Test
    void listsOfPlainIdsReachTheirPerEntryWidget() {
        assertScalarStringList(FormModel.forCondition("neoorigins:near_block"), "blocks",
            "condition neoorigins:near_block");
        assertScalarStringList(FormModel.forCondition("neoorigins:near_block"), "tags",
            "condition neoorigins:near_block");
        assertScalarStringList(FormModel.forCondition("neoorigins:nearby_entities"), "entity_types",
            "condition neoorigins:nearby_entities");
        assertScalarStringList(FormModel.forAction("neoorigins:open_layer_picker"), "layers",
            "action neoorigins:open_layer_picker");
    }

    /** The list added in 2.2.24, whose missing FieldSpec pattern was the other layer of the gap. */
    @Test
    void removeEnchantmentIdListReachesItsPerEntryWidget() {
        assertScalarStringList(FormModel.forItemAction("neoorigins:remove_enchantment"), "enchantments",
            "item_action neoorigins:remove_enchantment");
    }
}
