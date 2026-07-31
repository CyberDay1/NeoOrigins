package com.cyberday1.neoorigins.power.schemaform;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
}
