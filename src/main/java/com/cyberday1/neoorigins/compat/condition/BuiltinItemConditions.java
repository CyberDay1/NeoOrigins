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
 *   <li>{@code nbt} (alias {@code custom_data}) — legacy-NBT subtree match:
 *       the stack's data components are projected back into a pre-1.21
 *       legacy tag view merged over {@code minecraft:custom_data}.</li>
 *   <li>{@code enchantment} — stack-level enchantment level comparison.</li>
 *   <li>{@code ingredient} — vanilla-recipe-style item / tag match.</li>
 *   <li>{@code amount} — stack-count comparison.</li>
 *   <li>{@code name} — display-name string equality.</li>
 *   <li>{@code food} — item has a food component.</li>
 *   <li>{@code meat} — food tagged raw or cooked meat.</li>
 *   <li>{@code armor_value} — armour conferred by this stack.</li>
 *   <li>{@code harvest_level} — tool tier, recovered from the tier tags that
 *       replaced numeric harvest levels in 1.20.5.</li>
 *   <li>{@code durability} — remaining durability on the stack.</li>
 *   <li>{@code constant} — fixed {@code value}.</li>
 *   <li>{@code not} — single nested {@code item_condition} negated (explicit
 *       cross-doc {@code ref} so the name-heuristic resolver routes to this
 *       doc — not the entity condition doc — when nested).</li>
 *   <li>{@code and} / {@code or} — recursive combinators over
 *       {@code conditions[]}, each element a nested {@code item_condition}
 *       ({@code all_of} / {@code any_of} are the Apoli 2.9+ renames — aliases).</li>
 * </ul>
 */
public final class BuiltinItemConditions {

    private BuiltinItemConditions() {}

    /** The {@code item_condition.schema.json} doc name used for self/element refs. */
    public static final String DOC = "item_condition.schema.json";

    /** Insertion-ordered so generation/audit output is deterministic. */
    private static final Map<Identifier, ConditionType> DESCRIPTORS = new LinkedHashMap<>();

    /** Never-invoked passthrough: parsing lives in {@link ItemConditionParser#parse}. */
    private static final ConditionType.Factory PASSTHROUGH = (json, ctx) -> EntityCondition.alwaysTrue();

    private static void define(String path, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields));
    }

    private static void define(String path, List<String> aliasPaths, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        List<Identifier> aliases = aliasPaths.stream()
            .map(p -> Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, p))
            .toList();
        DESCRIPTORS.put(id, new ConditionType(id, PASSTHROUGH, fields, aliases));
    }

    static {
        // empty — true when the stack is empty.
        define("empty", List.of());
        // nbt / custom_data — legacy-NBT subtree match (components projected
        // back into the pre-1.21 tag view, merged over custom_data).
        define("nbt", List.of("custom_data"), List.of(
            new FieldSpec("nbt", FormFieldSpec.Kind.STRING, true)
                .doc("SNBT subtree the stack's legacy-view NBT must contain (e.g. {foo:1} or {Potion:\"minecraft:swiftness\"}). "
                    + "Data components are projected back into the pre-1.21 layout (Potion, Enchantments, display.Name, ...) merged over minecraft:custom_data.")));
        // enchantment — stack-level enchantment level comparison.
        define("enchantment", List.of(
            new FieldSpec("enchantment", FormFieldSpec.Kind.STRING, true)
                .doc("Enchantment id checked on the stack itself (e.g. minecraft:sharpness)."),
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the enchantment level (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("Enchantment level threshold (default 1).")));
        // ingredient — vanilla-recipe-style item / tag match (top-level or nested
        // under `ingredient`; the nested form also accepts vanilla's union/array
        // shape — [{tag:...},{item:...}] — matching when any entry matches).
        define("ingredient", List.of(
            new FieldSpec("item", FormFieldSpec.Kind.STRING, false)
                .doc("Exact item id to match (e.g. minecraft:diamond)."),
            new FieldSpec("tag", FormFieldSpec.Kind.STRING, false)
                .doc("Item tag the stack must be in (e.g. minecraft:planks). "
                    + "May also be nested under an `ingredient` key, including vanilla's "
                    + "array form ([{\"tag\": ...}, {\"item\": ...}]) which matches when any entry matches.")));
        // amount — stack-count comparison.
        define("amount", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the stack count (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("Stack count threshold (default 1).")));
        // name — display-name string equality.
        define("name", List.of(
            new FieldSpec("name", FormFieldSpec.Kind.STRING, true)
                .doc("Exact display-name text the stack's hover name must equal (custom name, else default item name).")));
        // food — item has a food component (no fields).
        define("food", List.of());
        // meat — food tagged as raw or cooked meat (no fields).
        define("meat", List.of());
        // armor_value — armour this stack confers, not the wearer's total.
        define("armor_value", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the stack's armour value (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.NUMBER, false).def(0)
                .doc("Armour-value threshold. Counts only flat minecraft:armor bonuses on the "
                    + "stack, so anything that grants no armour reads 0. Note the entity condition "
                    + "of the same name measures the wearer's total instead.")));
        // harvest_level — tool tier, in Apoli's numbering.
        define("harvest_level", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against the tool tier (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("Tier threshold: wood/gold 0, stone 1, iron 2, diamond 3, netherite 4. "
                    + "Minecraft dropped numeric harvest levels in 1.20.5, so the tier is read back "
                    + "from the tier tag on the tool; anything that is not a tool reads 0.")));
        // durability — remaining durability on the stack.
        define("durability", List.of(
            new FieldSpec("comparison", FormFieldSpec.Kind.ENUM, false)
                .options("==", "!=", ">", ">=", "<", "<=").def(">=")
                .doc("Comparison operator against remaining durability (default >=)."),
            new FieldSpec("compare_to", FormFieldSpec.Kind.INTEGER, false).def(0)
                .doc("Remaining-durability threshold: max damage minus current damage. "
                    + "Items that cannot take damage read 0.")));
        // constant — fixed outcome, mostly used to stub a branch out.
        define("constant", List.of(
            new FieldSpec("value", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                .doc("The value this condition always returns.")));
        // not — single nested item condition, negated.
        define("not", List.of(
            new FieldSpec("condition", FormFieldSpec.Kind.REF, false)
                .ref(DOC)
                .doc("Nested item condition that must NOT match.")));
        // and — every nested item condition must match. all_of is the
        // Apoli 2.9+ rename (same shape) — cf. the entity-side alias.
        define("and", List.of("all_of"), List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested item conditions; all must match (evaluated against the same stack).")));
        // or — at least one nested item condition must match. any_of is the
        // Apoli 2.9+ rename (same shape).
        define("or", List.of("any_of"), List.of(
            new FieldSpec("conditions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested item conditions; at least one must match.")));
    }

    /** Canonical id → descriptor (insertion-ordered). */
    public static Map<Identifier, ConditionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
