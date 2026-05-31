package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ConditionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor-metadata registry for the {@code item_condition} sub-shape — the
 * recurring nested object accepted by {@code equipped_item} (condition) and
 * {@code modify_inventory} (action), i.e. Apoli's "item condition". Second
 * member of the reusable ref-doc family (Step 3), mirroring
 * {@link BuiltinBlockConditions}: its descriptors drive a generated
 * {@code item_condition.schema.json} that the action/condition use-sites
 * reference via {@code $ref}, so all three editors render a real type-picker
 * sub-form instead of a raw-JSON box.
 *
 * <p><b>Editor-metadata ONLY.</b> These descriptors do not back any runtime
 * dispatch — item-condition parsing stays verbatim in
 * {@link ItemConditionParser#parse}. The {@link ConditionType.Factory} here is
 * a never-invoked passthrough (returns an {@link EntityCondition}, not an
 * {@link ItemCondition}, since the registry shape is shared with the
 * action/condition generator), present only so the descriptor reuses the same
 * FieldSpec shape the schema generator already consumes.
 *
 * <p><b>Shapes</b> (type-discriminated, mirroring {@code ItemConditionParser}'s
 * switch on the canonicalized {@code type}):
 * <ul>
 *   <li>{@code empty} — true when the stack is empty (no fields).</li>
 *   <li>{@code nbt} (alias {@code custom_data}) — SNBT subtree match on the
 *       stack's {@code minecraft:custom_data} component.</li>
 *   <li>{@code enchantment} — stack-level enchantment level comparison.</li>
 *   <li>{@code ingredient} — vanilla-recipe-style item / tag match.</li>
 *   <li>{@code not} — single nested {@code item_condition} negated (explicit
 *       cross-doc {@code ref} so the name-heuristic resolver routes to this
 *       doc — not the entity condition doc — when nested).</li>
 *   <li>{@code and} / {@code or} — recursive combinators over
 *       {@code conditions[]}, each element a nested {@code item_condition}.</li>
 * </ul>
 */
public final class BuiltinItemConditions {

    private BuiltinItemConditions() {}

    /** The {@code item_condition.schema.json} doc name used for self/element refs. */
    public static final String DOC = "item_condition.schema.json";

    /** Insertion-ordered so generation/audit output is deterministic. */
    private static final Map<ResourceLocation, ConditionType> DESCRIPTORS = new LinkedHashMap<>();

    /** Never-invoked passthrough: parsing lives in {@link ItemConditionParser#parse}. */
    private static final ConditionType.Factory PASSTHROUGH = (json, ctx) -> EntityCondition.alwaysTrue();

    private static void define(String path, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields));
    }

    private static void define(String path, List<String> aliasPaths, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<ResourceLocation> aliases = aliasPaths.stream()
            .map(p -> ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields, aliases));
    }

    static {
        // empty — true when the stack is empty.
        define("empty", List.of());
        // nbt / custom_data — SNBT subtree match on the custom_data component.
        define("nbt", List.of("custom_data"), List.of(
            new FieldSpec("nbt", FormFieldSpec.Kind.STRING, true)
                .doc("SNBT subtree the stack's minecraft:custom_data must contain (e.g. {foo:1}).")));
        // enchantment — stack-level enchantment level comparison.
        define("enchantment", List.of(
            new FieldSpec("enchantment", FormFieldSpec.Kind.STRING, true)
                .doc("Enchantment id checked on the stack itself (e.g. minecraft:sharpness)."),
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the enchantment level (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("Enchantment level threshold (default 1).")));
        // ingredient — vanilla-recipe-style item / tag match (top-level or nested under `ingredient`).
        define("ingredient", List.of(
            new FieldSpec("item", FormFieldSpec.Kind.STRING, false)
                .doc("Exact item id to match (e.g. minecraft:diamond)."),
            new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                .doc("Item tag the stack must be in (e.g. minecraft:planks).")));
        // not — single nested item condition, negated.
        define("not", List.of(
            new FieldSpec("condition", FormFieldSpec.Kind.REF, false)
                .ref(DOC)
                .doc("Nested item condition that must NOT match.")));
        // and — every nested item condition must match.
        define("and", List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested item conditions; all must match (evaluated against the same stack).")));
        // or — at least one nested item condition must match.
        define("or", List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested item conditions; at least one must match.")));
    }

    /** Canonical id → descriptor (insertion-ordered). */
    public static Map<ResourceLocation, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
