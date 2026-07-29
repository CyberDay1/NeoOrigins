// TS port of the in-game `FormFieldSpec.java` record
// (src/main/java/com/cyberday1/neoorigins/power/schemaform/FormFieldSpec.java).
//
// Tier-A field kinds (BOOLEAN / INTEGER / NUMBER / ENUM / STRING) are
// rendered with proper widgets. Everything else — OBJECT, ARRAY, REF, MIXED,
// UNKNOWN, oneOf without a `$comment` discriminator — falls back to the
// `RawJson` variant rendered as a `<textarea>` escape hatch. This mirrors
// the in-game creator's behaviour (~30% of power types lack a structured
// branch and edit as raw JSON).
//
// All variants share `path` (JSON pointer, e.g. `/grant_id`) so the form
// model can write back into a single draft object without per-widget
// glue, and `name` (the trailing JSON key — what the in-game label shows).
// The `label` and `description` strings come from `field_docs.json` if
// present, falling back to the property name + schema `description`.

/** Variants common to every field kind. */
interface FieldSpecBase {
	/** JSON pointer relative to the power root, e.g. `/grant_id`. */
	path: string;
	/** Trailing JSON key (what the in-game creator shows as the label). */
	name: string;
	/** Human-readable label — `field_docs.json` first, name fallback. */
	label: string;
	/** Schema `description` or `field_docs.json` long form; may be empty. */
	description: string;
	/** True if the matched schema branch lists this in `required`. */
	required: boolean;
}

export interface BooleanFieldSpec extends FieldSpecBase {
	kind: 'BOOLEAN';
	default: boolean | null;
}

export interface IntegerFieldSpec extends FieldSpecBase {
	kind: 'INTEGER';
	default: number | null;
	/** Inclusive lower bound, after collapsing `exclusiveMinimum`. */
	min: number | null;
	/** Inclusive upper bound. */
	max: number | null;
}

export interface NumberFieldSpec extends FieldSpecBase {
	kind: 'NUMBER';
	default: number | null;
	min: number | null;
	max: number | null;
}

export interface EnumFieldSpec extends FieldSpecBase {
	kind: 'ENUM';
	default: string | null;
	options: string[];
}

export interface StringFieldSpec extends FieldSpecBase {
	kind: 'STRING';
	default: string | null;
	/** Optional regex hint from schema `pattern` — UI displays as a hint. */
	pattern: string | null;
}

/**
 * A cross-document `$ref` into `action.schema.json` / `condition.schema.json`
 * (or a self-`$ref:"#"` inside one of those). Replaces the raw-JSON fallback
 * with a recursive sub-form (D4): a type picker over the referenced schema's
 * `type.enum`, then that branch's own `FormFieldSpec` list rendered inline.
 * The bound value is the nested action/condition OBJECT (`{type, …}`) — not a
 * stringified blob — so it serializes straight into the wire JSON. Mirrors the
 * in-game `RefRow` that the Java creator ships.
 */
export interface RefFieldSpec extends FieldSpecBase {
	kind: 'REF';
	/** Which sibling schema document this refs into. */
	refDoc: 'action' | 'condition' | 'block_condition' | 'item_condition' | 'item_action';
}

/**
 * An `array` whose `items` are a cross-document `$ref` into
 * `action.schema.json` / `condition.schema.json` (e.g. the `actions` list on
 * `neoorigins:and`). Rendered as an add/remove list of {@link RefFieldSpec}
 * sub-forms (D4 `ArrayRefRow`). The bound value is an array of nested
 * action/condition OBJECTs.
 */
export interface ArrayRefFieldSpec extends FieldSpecBase {
	kind: 'ARRAY_REF';
	/** Which sibling schema document each element refs into. */
	refDoc: 'action' | 'condition' | 'block_condition' | 'item_condition' | 'item_action';
	/**
	 * True when the schema shape is the Apoli "one or many" idiom —
	 * `{"oneOf":[{"$ref":X},{"type":"array","items":{"$ref":X}}]}` — rather than a
	 * plain `type:array`. Authoring is identical (an add/remove list), but the
	 * WIRE form differs: a single entry serializes back as a bare scalar object,
	 * matching what every built-in power JSON under
	 * `src/main/resources/data/neoorigins/powers/**` writes today (259 scalar
	 * occurrences, 0 arrays), so opening and re-saving an untouched file produces
	 * no diff. Two or more entries serialize as an array. See
	 * {@link asRefList} / {@link fromRefList}.
	 */
	scalarOrArray: boolean;
}

/**
 * An `array` whose `items` are scalar STRINGs (schema `items:{type:"string"}`,
 * no `$ref`) — e.g. the `biomes` list on a location condition. Rendered as an
 * add/remove list of text inputs (one per element) rather than the raw-JSON
 * fallback. `pattern` carries the optional `items.pattern` regex as a validation
 * hint, exactly like {@link StringFieldSpec.pattern}. The bound value is a
 * `string[]`. Entries are free-text resource-locations (never a closed enum),
 * so modded/datapack ids are supported by construction.
 */
export interface ArrayStringFieldSpec extends FieldSpecBase {
	kind: 'ARRAY_STRING';
	/** Optional regex hint from schema `items.pattern` — UI displays as a hint. */
	pattern: string | null;
}

/**
 * A nested object with a FIXED set of sub-fields (schema `type:object` with
 * inline `properties`) — e.g. an item stack (`{item, count}`), an effect
 * instance (`{effect, duration, amplifier, …}`), or the `resource` power's
 * `hud_render` block. Unlike {@link RefFieldSpec} there is NO type to pick:
 * the children are always the same. Rendered as an inline sub-form (no picker),
 * binding each child by its JSON key into the nested object value. Mirrors the
 * in-game `ObjectRow`. The bound value is the nested OBJECT (`{…}`) — not a
 * stringified blob — so it serializes straight into the wire JSON.
 */
export interface ObjectFieldSpec extends FieldSpecBase {
	kind: 'OBJECT';
	/** The fixed nested sub-fields, in schema-declared order. */
	children: FormFieldSpec[];
}

/**
 * Escape hatch for any non-Tier-A field: OBJECT, ARRAY, REF, MIXED, UNKNOWN,
 * or a `oneOf` without a per-branch `$comment` discriminator. The widget
 * renders a `<textarea>` and validates with `JSON.parse`. Authors edit
 * these by hand — same as the in-game creator's behaviour for these kinds.
 *
 * <p>Note D4 lifts the cross-document action/condition `$ref` cases out of
 * here into {@link RefFieldSpec} / {@link ArrayRefFieldSpec}; the `REF`/`ARRAY`
 * reasons remain for refs that target neither sibling schema (unknown
 * cross-document refs) and for same-document object/array shapes.
 */
export interface RawJsonFieldSpec extends FieldSpecBase {
	kind: 'RawJson';
	/** Why this fell back, for tooltips: 'OBJECT' | 'ARRAY' | 'REF' | 'MIXED' | 'UNKNOWN'. */
	reason: 'OBJECT' | 'ARRAY' | 'REF' | 'MIXED' | 'UNKNOWN';
	/** Default JSON value, stringified for the textarea. May be empty string. */
	default: string;
}

/**
 * Discriminated union over `kind`. Switch on `field.kind` for exhaustive
 * widget dispatch — see {@link FieldRow.svelte}.
 */
export type FormFieldSpec =
	| BooleanFieldSpec
	| IntegerFieldSpec
	| NumberFieldSpec
	| EnumFieldSpec
	| StringFieldSpec
	| RefFieldSpec
	| ArrayRefFieldSpec
	| ArrayStringFieldSpec
	| ObjectFieldSpec
	| RawJsonFieldSpec;

/** True when this field is one of the five Tier-A kinds (proper widget). */
export function isTierA(field: FormFieldSpec): boolean {
	return (
		field.kind === 'BOOLEAN' ||
		field.kind === 'INTEGER' ||
		field.kind === 'NUMBER' ||
		field.kind === 'ENUM' ||
		field.kind === 'STRING'
	);
}

/**
 * Widen an ARRAY_REF field's stored value into the list the editor edits.
 * Arrays pass through; a bare object (the scalar half of the "one or many"
 * idiom) becomes a one-element list; anything else (unset, `null`, a stray
 * scalar) becomes empty. Safe to call on a plain `type:array` field too.
 */
export function asRefList(value: unknown): unknown[] {
	if (Array.isArray(value)) return value;
	if (value !== null && typeof value === 'object') return [value];
	return [];
}

/**
 * Narrow an edited list back into the value stored on the draft / written to
 * the wire. Only a {@link ArrayRefFieldSpec.scalarOrArray} field with exactly
 * one PICKED entry collapses to that bare object — a lone unpicked (`null`)
 * slot stays an array so the row keeps rendering while the author picks a type.
 *
 * `scalarShaped` is the remembered authoring shape. Pass `false` for a field
 * the author actually wrote as an array, so that re-saving an untouched pack
 * does not silently rewrite it into the scalar form. It defaults to `true`
 * because that is the shape all 259 occurrences of these keys in the built-in
 * data use, so a freshly authored field matches the house convention.
 *
 * This mirrors `loadedAsScalar` in the in-game editor's ArrayRefWidget. The two
 * editors have to agree here, or the same edit produces different JSON
 * depending on which editor it was made in.
 */
export function fromRefList(
	field: ArrayRefFieldSpec,
	list: unknown[],
	scalarShaped: boolean = true
): unknown {
	if (
		field.scalarOrArray &&
		scalarShaped &&
		list.length === 1 &&
		list[0] !== null &&
		typeof list[0] === 'object'
	) {
		return list[0];
	}
	return list;
}

/** Default value appropriate for an empty / fresh form. */
export function emptyValueFor(field: FormFieldSpec): unknown {
	switch (field.kind) {
		case 'BOOLEAN':
			return field.default ?? false;
		case 'INTEGER':
		case 'NUMBER':
			return field.default ?? null;
		case 'ENUM':
			return field.default ?? (field.options[0] ?? '');
		case 'STRING':
			return field.default ?? '';
		case 'REF':
			// Unset until the author picks an action/condition type.
			return null;
		case 'ARRAY_REF':
		case 'ARRAY_STRING':
			return [];
		case 'OBJECT':
			// Empty object; children populate keys on edit. pruneForWire drops it
			// if untouched, so an optional nested object doesn't leak blanks.
			return {};
		case 'RawJson':
			return field.default;
	}
}
