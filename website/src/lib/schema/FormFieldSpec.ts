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
	refDoc: 'action' | 'condition';
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
	refDoc: 'action' | 'condition';
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
			return [];
		case 'RawJson':
			return field.default;
	}
}
