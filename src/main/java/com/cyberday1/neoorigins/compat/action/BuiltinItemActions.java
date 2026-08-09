package com.cyberday1.neoorigins.compat.action;

import com.cyberday1.neoorigins.NeoOrigins;
import com.cyberday1.neoorigins.compat.registry.ActionType;
import com.cyberday1.neoorigins.compat.registry.FieldSpec;
import com.cyberday1.neoorigins.power.schemaform.FormFieldSpec;
import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Editor-metadata registry for the {@code item_action} sub-shape — the
 * recurring nested object accepted by {@code equipped_item_action} and
 * {@code modify_inventory} (actions), i.e. Apoli's "item action". Third member
 * of the reusable ref-doc family (Step 3), mirroring {@link BuiltinItemConditions}:
 * its descriptors drive a generated {@code item_action.schema.json} that the
 * action use-sites reference via {@code $ref}, so all three editors render a
 * real type-picker sub-form instead of a raw-JSON box.
 *
 * <p><b>Editor-metadata ONLY.</b> These descriptors do not back any runtime
 * dispatch — item-action parsing stays verbatim in {@link ItemActionParser#parse}.
 * The {@link ActionType.Factory} here is a never-invoked passthrough (returns
 * {@link EntityAction#noop()}, not an {@link ItemAction}, since the registry
 * shape is shared with the action/condition generator), present only so the
 * descriptor reuses the same FieldSpec shape the schema generator already
 * consumes.
 *
 * <p><b>Shapes</b> (type-discriminated, mirroring {@code ItemActionParser}'s
 * switch on the canonicalized {@code type}):
 * <ul>
 *   <li>{@code and} — recursive combinator over {@code actions[]}, each element
 *       a nested {@code item_action}, run in order.</li>
 *   <li>{@code if_else} — branch on a nested {@code item_condition}; runs
 *       {@code if_action} or {@code else_action} (both nested {@code item_action}).</li>
 *   <li>{@code merge_nbt} — merge an SNBT subtree into the stack's components.</li>
 *   <li>{@code consume} — shrink the stack by {@code amount} (default 1).</li>
 *   <li>{@code damage} — damage the stack by {@code amount} (default 1).</li>
 *   <li>{@code set_count} — set the stack count outright (default 1).</li>
 *   <li>{@code remove_enchantment} - strip named enchantments from the stack,
 *       or knock {@code levels} off them, optionally resetting its anvil
 *       repair cost.</li>
 * </ul>
 */
public final class BuiltinItemActions {

    private BuiltinItemActions() {}

    /** The {@code item_action.schema.json} doc name used for self/element refs. */
    public static final String DOC = "item_action.schema.json";

    /** The sibling {@code item_condition.schema.json} doc (if_else's condition field). */
    private static final String ITEM_CONDITION_DOC = "item_condition.schema.json";

    /** Insertion-ordered so generation/audit output is deterministic. */
    private static final Map<Identifier, ActionType> DESCRIPTORS = new LinkedHashMap<>();

    /** Never-invoked passthrough: parsing lives in {@link ItemActionParser#parse}. */
    private static final ActionType.Factory PASSTHROUGH = (json, ctx) -> EntityAction.noop();

    private static void define(String path, List<FieldSpec> fields) {
        Identifier id = Identifier.fromNamespaceAndPath(NeoOrigins.MOD_ID, path);
        DESCRIPTORS.put(id, new ActionType(id, PASSTHROUGH, fields));
    }

    static {
        // and — run every nested item action in order.
        define("and", List.of(
            new FieldSpec("actions", FormFieldSpec.Kind.ARRAY, false)
                .itemsRef(DOC)
                .doc("Nested item actions, run in order against the same stack.")));
        // if_else — branch on a nested item condition.
        define("if_else", List.of(
            new FieldSpec("condition", FormFieldSpec.Kind.REF, false)
                .ref(ITEM_CONDITION_DOC)
                .doc("Item condition tested against the stack (absent → always true)."),
            new FieldSpec("if_action", FormFieldSpec.Kind.REF, false)
                .ref(DOC)
                .doc("Item action run when the condition matches."),
            new FieldSpec("else_action", FormFieldSpec.Kind.REF, false)
                .ref(DOC)
                .doc("Item action run when the condition does not match.")));
        // merge_nbt — merge an SNBT subtree into the stack's components.
        define("merge_nbt", List.of(
            new FieldSpec("nbt", FormFieldSpec.Kind.STRING, false)
                .doc("SNBT merged into the stack (e.g. {CustomModelData:3}); unknown keys land in custom_data.")));
        // consume — shrink the stack.
        define("consume", List.of(
            new FieldSpec("amount", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("Number of items to remove from the stack (default 1).")));
        // damage — damage the stack.
        define("damage", List.of(
            new FieldSpec("amount", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("Durability damage to apply (default 1)."),
            new FieldSpec("ignore_unbreaking", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                .doc("Apply damage even on items that would resist it (default false).")));
        // set_count — set the stack count outright.
        define("set_count", List.of(
            new FieldSpec("count", FormFieldSpec.Kind.INTEGER, false).def(1)
                .doc("New stack count (default 1).")));
        // remove_enchantment - strip named enchantments off the stack.
        // Both the documented singular key and the plural key packs actually
        // ship are declared, because the parser reads both.
        define("remove_enchantment", List.of(
            new FieldSpec("enchantment", FormFieldSpec.Kind.STRING, false)
                .doc("Single enchantment id to strip, e.g. minecraft:binding_curse."),
            new FieldSpec("enchantments", FormFieldSpec.Kind.ARRAY, false)
                // Optional namespace: collectEnchantmentIds runs the entry through
                // ResourceLocation.tryParse, which defaults a bare leaf to
                // minecraft:, so a namespace-required pattern would reject ids the
                // parser accepts. Also what marks this a scalar-string list, which
                // is what gives both editors a per-entry widget instead of a
                // raw-JSON box.
                .itemPattern("^(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+$")
                .doc("Enchantment ids to strip; the plural form of enchantment, and may be used instead of or alongside it."),
            new FieldSpec("levels", FormFieldSpec.Kind.INTEGER, false)
                .doc("Levels to subtract from each named enchantment instead of removing it outright; one already at or below this level is removed. Omit to always remove outright (no default: absent and 0 differ)."),
            new FieldSpec("reset_repair_cost", FormFieldSpec.Kind.BOOLEAN, false).def(false)
                .doc("Also clear the stack's accumulated anvil repair cost (default false). Also read from the enclosing equipped_item_action.")));
    }

    /** Canonical id → descriptor (insertion-ordered). */
    public static Map<Identifier, ActionType> descriptors() {
        return Collections.unmodifiableMap(DESCRIPTORS);
    }
}
