package com.cyberday1.neoorigins.compat.condition;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ConditionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.Identifier;

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
 *   <li>{@code fluid} — the fluid at the position rather than the block, via a nested
 *       {@code fluid_condition}. Not the same test as {@code block}: a waterlogged slab
 *       reads as {@code minecraft:oak_slab} as a block and {@code minecraft:water} as a
 *       fluid, and a "am I wet" power means the latter.</li>
 *   <li>{@code light_level} — the light reaching the tested position, compared
 *       numerically; {@code exposed_to_sky} — nothing between it and the sky;
 *       {@code movement_blocking} — the block obstructs movement.</li>
 *   <li>{@code and} / {@code or} (Apoli 2.9+ spellings {@code all_of} / {@code any_of},
 *       carried as aliases) — recursive combinators over {@code conditions[]},
 *       each element a nested {@code block_condition} (explicit cross-doc
 *       {@code itemsRef} rather than {@code "#"}, so the in-game editor's
 *       name-heuristic ref resolver routes the list to this doc — not the entity
 *       condition doc — when nested).</li>
 *   <li>{@code offset} — structural wrapper: evaluates the nested
 *       {@code condition} at the tested position shifted by {@code x}/{@code y}/
 *       {@code z} (parsed in {@code OriginsCompatPowerLoader.compileBlockPredicate}).</li>
 *   <li>{@code block_state} — one blockstate property, matched by value/enum or
 *       compared numerically.</li>
 *   <li>{@code height} — the tested block's own Y level.</li>
 *   <li>{@code adjacent} — counts the six face neighbours matching a nested
 *       {@code adjacent_condition}.</li>
 * </ul>
 */
public final class BuiltinBlockConditions {

    private BuiltinBlockConditions() {}

    /** The {@code block_condition.schema.json} doc name used for self/element refs. */
    public static final String DOC = "block_condition.schema.json";

    /** Insertion-ordered so generation/audit output is deterministic. */
    private static final Map<Identifier, ConditionType> DESCRIPTORS = new LinkedHashMap<>();

    /** Never-invoked passthrough: parsing lives in {@link ConditionParser#parseOnBlock}. */
    private static final ConditionType.Factory PASSTHROUGH = (json, ctx) -> EntityCondition.alwaysTrue();

    private static void define(String path, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields));
    }

    /**
     * Define a descriptor that also answers to {@code aliasPaths}. Only the
     * canonical id is counted toward the type total; the aliases ride along so the
     * generated schema's discriminator accepts them, which is what stops an editor
     * marking a working file invalid.
     */
    private static void define(String path, List<String> aliasPaths, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<Identifier> aliases = aliasPaths.stream()
            .map(p -> Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields, aliases));
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
        // fluid — the fluid occupying the position, not the block sitting in it.
        define("fluid", List.of(
            new FieldSpec("fluid_condition", FormFieldSpec.Kind.OBJECT, true)
                .doc("Condition on the fluid at the tested position. Types: `in_tag` (with `tag`), "
                   + "`fluid` (with `fluid`/`id`), `empty`, `still`, `constant` (with `value`), "
                   + "and `and`/`or` over `conditions[]`. Every node also honours `inverted`.")));
        // light_level — the light reaching the tested position.
        define("light_level", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the light level (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("Light level to compare against, 0..15 (default 0)."),
            new FieldSpec("light_type", FormFieldSpec.Kind.ENUM, false)
                .options("sky", "block", "any").def("any")
                .doc("Which light to read (default any — the effective local brightness).")));
        // exposed_to_sky — nothing between the tested position and the sky.
        define("exposed_to_sky", List.of());
        // movement_blocking — the block obstructs movement.
        define("movement_blocking", List.of());
        // and — every nested block condition must match.
        // `all_of` is the Apoli 2.9+ rename, accepted by ConditionParser's block
        // combinator arm; declared here so the editors stop rejecting a file that
        // the loader has always read.
        define("and", List.of("all_of"), List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested block conditions; all must match (evaluated against the same block).")));
        // or — at least one nested block condition must match. `any_of` as above.
        define("or", List.of("any_of"), List.of(
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
        // block_state — one blockstate property of the tested block.
        define("block_state", List.of(
            new FieldSpec("property", FormFieldSpec.Kind.STRING, true)
                .doc("Blockstate property name (e.g. waterlogged, facing, level). A block without it never matches."),
            new FieldSpec("value", FormFieldSpec.Kind.STRING, false)
                .doc("Required property value, or a list of accepted values. Booleans and numbers may be written unquoted."),
            new FieldSpec("enum", FormFieldSpec.Kind.STRING, false)
                .doc("Origins spelling of `value` for named properties (e.g. \"south\"); a list is also accepted."),
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=")
                .doc("For numeric properties: compare the value instead of matching it. Overrides value/enum."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                .doc("Number the property value is compared against (default 0).")));
        // height — the tested block's own Y level.
        define("height", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the block's Y coordinate (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.NUMBER, false).def(0.0)
                .doc("Y level to compare against (default 0; 63 is sea level).")));
        // adjacent — count the six face neighbours matching a nested condition.
        define("adjacent", List.of(
            new FieldSpec("adjacent_condition", FormFieldSpec.Kind.REF, true)
                .ref(DOC)
                .doc("Block condition tested against each of the six face neighbours."),
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the matching-neighbour count (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.NUMBER, false).def(1.0)
                .doc("Neighbour-count threshold, 0..6 (default 1 — i.e. \"any matching neighbour\").")));
    }

    /** Canonical id → descriptor (insertion-ordered). */
    public static Map<Identifier, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
