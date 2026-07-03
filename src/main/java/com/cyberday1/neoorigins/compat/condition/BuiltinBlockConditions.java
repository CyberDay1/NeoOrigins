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
 * Editor-metadata registry for the {@code block_condition} sub-shape — the
 * recurring nested object accepted by {@code on_block} / {@code block} /
 * {@code in_block} / {@code near_block} (Apoli's "block condition"). It is the
 * first member of the reusable ref-doc family (Step 3 of the registry refactor):
 * its descriptors drive a generated {@code block_condition.schema.json} that the
 * action/condition use-sites reference via {@code $ref}, so all three editors
 * render a real type-picker sub-form instead of a raw-JSON box.
 *
 * <p><b>Editor-metadata ONLY.</b> Unlike {@link BuiltinConditions}, these
 * descriptors do not back any runtime dispatch — block-condition parsing stays
 * verbatim in {@link ConditionParser#parseOnBlock} (and the {@code near_block} /
 * {@code in_block} variants). The {@link ConditionType.Factory} here is a never-
 * invoked passthrough, present only so the descriptor reuses the same shape the
 * schema generator already consumes for actions/conditions.
 *
 * <p><b>Shapes</b> (type-discriminated, mirroring {@code parseOnBlock}'s switch on
 * the stripped {@code type}):
 * <ul>
 *   <li>{@code block} — exact block id via {@code block} (or legacy {@code id}).</li>
 *   <li>{@code in_tag} — block-tag membership via {@code tag}.</li>
 *   <li>{@code and} / {@code or} — recursive combinators over {@code conditions[]},
 *       each element a nested {@code block_condition} (explicit cross-doc
 *       {@code itemsRef} rather than {@code "#"}, so the in-game editor's
 *       name-heuristic ref resolver routes the list to this doc — not the entity
 *       condition doc — when nested).</li>
 *   <li>{@code offset} — structural wrapper: evaluates the nested
 *       {@code condition} at the tested position shifted by {@code x}/{@code y}/
 *       {@code z} (parsed in {@code OriginsCompatPowerLoader.compileBlockPredicate}).</li>
 * </ul>
 */
public final class BuiltinBlockConditions {

    private BuiltinBlockConditions() {}

    /** The {@code block_condition.schema.json} doc name used for self/element refs. */
    public static final String DOC = "block_condition.schema.json";

    /** Insertion-ordered so generation/audit output is deterministic. */
    private static final Map<ResourceLocation, ConditionType> DESCRIPTORS = new LinkedHashMap<>();

    /** Never-invoked passthrough: parsing lives in {@link ConditionParser#parseOnBlock}. */
    private static final ConditionType.Factory PASSTHROUGH = (json, ctx) -> EntityCondition.alwaysTrue();

    private static void define(String path, List<FieldSpec> fields) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields));
    }

    static {
        // block — exact block id at the tested position.
        define("block", List.of(
            new FieldSpec("block", FormFieldSpec.Kind.STRING, false)
                .doc("Block id to match (e.g. minecraft:water)."),
            new FieldSpec("id", FormFieldSpec.Kind.STRING, false)
                .doc("Legacy alias for `block`.")));
        // in_tag — block-tag membership.
        define("in_tag", List.of(
            new FieldSpec("tag", FormFieldSpec.Kind.STRING, true)
                .doc("Block tag the block must be in (e.g. minecraft:ice).")));
        // and — every nested block condition must match.
        define("and", List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested block conditions; all must match (evaluated against the same block).")));
        // or — at least one nested block condition must match.
        define("or", List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested block conditions; at least one must match.")));
        // offset — evaluate the nested condition at pos + (x, y, z).
        define("offset", List.of(
            new FieldSpec("condition", FormFieldSpec.Kind.REF, false)
                .ref(DOC)
                .doc("Nested block condition evaluated at the offset position; absent → matches all blocks (warned at load)."),
            new FieldSpec("x", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("X offset in blocks (default 0)."),
            new FieldSpec("y", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("Y offset in blocks (default 0)."),
            new FieldSpec("z", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("Z offset in blocks (default 0).")));
    }

    /** Canonical id → descriptor (insertion-ordered). */
    public static Map<ResourceLocation, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
