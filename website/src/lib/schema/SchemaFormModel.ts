// TS port of the in-game `SchemaFormModel.java`
// (src/main/java/com/cyberday1/neoorigins/power/schemaform/SchemaFormModel.java).
//
// Walks `power.schema.json` top-level `oneOf` and produces a
// `FormFieldSpec[]` for a given power type ID. Discriminated by `$comment`
// equaling the power type ID (the locked workaround documented in
// `planning/web_editor_scope.md` — off-the-shelf JSON-schema-form libs
// don't handle this discriminated-oneOf shape).
//
// MVP scope:
//   - Common-root properties (every power: `name`, `description`, `hidden`)
//     are emitted first, the matched branch's properties second.
//   - The `type` discriminator itself is NEVER emitted (the power-picker
//     drives that; it's not editable as a field).
//   - `$ref` is resolved ONE level deep, looking up the pointer inside the
//     same schema document (e.g. `#/$defs/foo`). Cross-document refs (e.g.
//     `condition.schema.json`) fall to RawJson — the recursive RefRow that
//     the Java side ships is a Phase-2 add per the scope doc.
//   - Nested `properties` of an OBJECT field fall to RawJson — same MVP
//     cutoff. Authors edit the inner JSON by hand.

import type {
	BooleanFieldSpec,
	EnumFieldSpec,
	FormFieldSpec,
	IntegerFieldSpec,
	NumberFieldSpec,
	RawJsonFieldSpec,
	StringFieldSpec
} from './FormFieldSpec.js';

// ── public API ──────────────────────────────────────────────────────────────

/**
 * Parse the field list for a single power type out of `power.schema.json`.
 *
 * @param schema    The full parsed `power.schema.json` document.
 * @param fieldDocs The full parsed `field_docs.json` document.
 * @param powerType The fully-qualified power id (e.g. `"neoorigins:starting_equipment"`).
 * @returns         A `FormFieldSpec[]` ordered as common-fields-then-branch-fields.
 *                  Returns `[]` if `powerType` has no structured `$comment`
 *                  branch (the fallback "type not in enum" branch — the same
 *                  power list the in-game creator drops to raw-JSON for).
 * @throws          `Error("power type not in schema enum: <id>")` if `powerType`
 *                  is not in the root `properties.type.enum` universe.
 */
export function parsePowerSchema(
	schema: object,
	fieldDocs: object,
	powerType: string
): FormFieldSpec[] {
	const root = schema as JsonObject;
	const docs = fieldDocs as FieldDocs;

	// Sanity: power type must appear in the schema's universe.
	const typeEnum = readTypeEnum(root);
	if (!typeEnum.includes(powerType)) {
		throw new Error(`power type not in schema enum: ${powerType}`);
	}

	// Common-root fields (skip `type` — driven by the power picker, not a row).
	const commonFields = commonRootFields(root, docs, powerType);

	// Find the matching structured branch by `$comment`.
	const branch = findStructuredBranch(root, powerType);
	if (!branch) return commonFields; // fallback branch → raw-JSON only

	// Branch fields, in schema-declared order, skipping `type` again.
	const branchProps = (branch.properties ?? {}) as JsonObject;
	const branchRequired = readRequiredSet(branch);
	const branchFields: FormFieldSpec[] = [];
	for (const [name, raw] of Object.entries(branchProps)) {
		if (name === 'type') continue;
		const propSchema = derefOneLevel(root, raw as JsonValue);
		branchFields.push(
			mapProperty(name, propSchema, branchRequired.has(name), docs, powerType)
		);
	}
	return [...commonFields, ...branchFields];
}

// ── internals ───────────────────────────────────────────────────────────────

type JsonValue = string | number | boolean | null | JsonObject | JsonValue[];
type JsonObject = { [k: string]: JsonValue };

/** `field_docs.json` shape: `{ "*": {field: doc}, "<powerId>": {field: doc} }`. */
type FieldDocs = { [powerOrStar: string]: { [field: string]: string } };

function readTypeEnum(root: JsonObject): string[] {
	const props = root['properties'] as JsonObject | undefined;
	const typeProp = props?.['type'] as JsonObject | undefined;
	const en = typeProp?.['enum'] as JsonValue[] | undefined;
	if (!Array.isArray(en)) return [];
	return en.filter((v): v is string => typeof v === 'string');
}

function readRequiredSet(o: JsonObject): Set<string> {
	const req = o['required'];
	if (!Array.isArray(req)) return new Set();
	return new Set(req.filter((v): v is string => typeof v === 'string'));
}

function commonRootFields(
	root: JsonObject,
	docs: FieldDocs,
	powerType: string
): FormFieldSpec[] {
	const props = (root['properties'] ?? {}) as JsonObject;
	const required = readRequiredSet(root);
	const out: FormFieldSpec[] = [];
	for (const [name, raw] of Object.entries(props)) {
		if (name === 'type') continue;
		out.push(mapProperty(name, raw as JsonValue, required.has(name), docs, powerType));
	}
	return out;
}

/**
 * Find the `oneOf` branch whose `$comment` equals (or starts with) `powerType`.
 *
 * The schema uses long `$comment` strings of the form
 * `"neoorigins:starting_equipment — grants one or more items …"` for
 * documentation, and the in-game Java side matches on `type.const`. We match
 * by checking that the `$comment` starts with `powerType` followed by a
 * non-id character (em-dash, space, or end of string), which handles both
 * the bare-id and documented-id forms.
 */
function findStructuredBranch(root: JsonObject, powerType: string): JsonObject | null {
	const oneOf = root['oneOf'];
	if (!Array.isArray(oneOf)) return null;
	for (const candidate of oneOf) {
		if (!isObject(candidate)) continue;
		const comment = candidate['$comment'];
		if (typeof comment !== 'string') continue;
		if (comment === powerType || comment.startsWith(powerType + ' ') ||
			comment.startsWith(powerType + '\u2014') ||
			comment.startsWith(powerType + ' \u2014')) {
			return candidate;
		}
	}
	return null;
}

/**
 * Resolve a `$ref` one level deep within the same document (`#/...`).
 * Returns the dereffed object so caller code can read `description`, `type`,
 * etc. without special-casing. Cross-document refs (no `#`) and recursive
 * resolution are left to the caller, which falls them to RawJson(REF).
 */
function derefOneLevel(root: JsonObject, value: JsonValue): JsonValue {
	if (!isObject(value)) return value;
	const ref = value['$ref'];
	if (typeof ref !== 'string' || !ref.startsWith('#/')) return value;
	const segs = ref
		.slice(2)
		.split('/')
		.map((s) => s.replace(/~1/g, '/').replace(/~0/g, '~'));
	let cur: JsonValue = root;
	for (const s of segs) {
		if (!isObject(cur)) return value; // give up — fall back to original
		cur = cur[s];
		if (cur === undefined) return value;
	}
	return cur;
}

function mapProperty(
	name: string,
	raw: JsonValue,
	required: boolean,
	docs: FieldDocs,
	powerType: string
): FormFieldSpec {
	const p = isObject(raw) ? raw : ({} as JsonObject);

	// Label + description: prefer field_docs by power type, then `*` (shared
	// fields like `name`/`description`), then schema `description`, then name.
	const docForType = docs[powerType]?.[name];
	const docForCommon = docs['*']?.[name];
	const schemaDesc = typeof p['description'] === 'string' ? (p['description'] as string) : '';
	const description = docForType ?? docForCommon ?? schemaDesc ?? '';
	const label = humanize(name);

	const base = {
		path: '/' + jsonPointerEscape(name),
		name,
		label,
		description,
		required
	} as const;

	// ENUM beats type: a `string` with an `enum` is rendered as a dropdown.
	if (Array.isArray(p['enum'])) {
		const options = (p['enum'] as JsonValue[]).filter(
			(v): v is string => typeof v === 'string'
		);
		const def = typeof p['default'] === 'string' ? (p['default'] as string) : null;
		const spec: EnumFieldSpec = { ...base, kind: 'ENUM', default: def, options };
		return spec;
	}

	// `oneOf` without a per-branch `$comment` discriminator → RawJson MIXED.
	// (e.g. the common `name` / `description` fields which are string | object.)
	if (Array.isArray(p['oneOf'])) {
		return rawJsonOf(base, 'MIXED', p['default']);
	}

	// Bare `$ref` that survived `derefOneLevel` is a cross-document ref
	// (e.g. action.schema.json / condition.schema.json) → RawJson REF.
	if (typeof p['$ref'] === 'string') {
		return rawJsonOf(base, 'REF', p['default']);
	}

	const t = p['type'];
	if (typeof t === 'string') {
		switch (t) {
			case 'boolean': {
				const def = typeof p['default'] === 'boolean' ? (p['default'] as boolean) : null;
				const spec: BooleanFieldSpec = { ...base, kind: 'BOOLEAN', default: def };
				return spec;
			}
			case 'integer': {
				const [min, max] = readNumericBounds(p);
				const def = typeof p['default'] === 'number' ? (p['default'] as number) : null;
				const spec: IntegerFieldSpec = { ...base, kind: 'INTEGER', default: def, min, max };
				return spec;
			}
			case 'number': {
				const [min, max] = readNumericBounds(p);
				const def = typeof p['default'] === 'number' ? (p['default'] as number) : null;
				const spec: NumberFieldSpec = { ...base, kind: 'NUMBER', default: def, min, max };
				return spec;
			}
			case 'string': {
				const pattern = typeof p['pattern'] === 'string' ? (p['pattern'] as string) : null;
				const def = typeof p['default'] === 'string' ? (p['default'] as string) : null;
				const spec: StringFieldSpec = { ...base, kind: 'STRING', default: def, pattern };
				return spec;
			}
			case 'array':
				return rawJsonOf(base, 'ARRAY', p['default']);
			case 'object':
				return rawJsonOf(base, 'OBJECT', p['default']);
			default:
				return rawJsonOf(base, 'UNKNOWN', p['default']);
		}
	}
	return rawJsonOf(base, 'UNKNOWN', p['default']);
}

function readNumericBounds(p: JsonObject): [number | null, number | null] {
	const min =
		typeof p['minimum'] === 'number'
			? (p['minimum'] as number)
			: typeof p['exclusiveMinimum'] === 'number'
				? (p['exclusiveMinimum'] as number)
				: null;
	const max =
		typeof p['maximum'] === 'number'
			? (p['maximum'] as number)
			: typeof p['exclusiveMaximum'] === 'number'
				? (p['exclusiveMaximum'] as number)
				: null;
	return [min, max];
}

function rawJsonOf(
	base: { path: string; name: string; label: string; description: string; required: boolean },
	reason: RawJsonFieldSpec['reason'],
	defaultValue: JsonValue | undefined
): RawJsonFieldSpec {
	const def =
		defaultValue === undefined
			? ''
			: typeof defaultValue === 'string'
				? defaultValue
				: JSON.stringify(defaultValue, null, 2);
	return { ...base, kind: 'RawJson', reason, default: def };
}

function isObject(v: JsonValue | undefined): v is JsonObject {
	return typeof v === 'object' && v !== null && !Array.isArray(v);
}

function jsonPointerEscape(s: string): string {
	return s.replace(/~/g, '~0').replace(/\//g, '~1');
}

/** Pretty-print a JSON property name (`grant_id` → `Grant id`). */
function humanize(name: string): string {
	const spaced = name.replace(/_/g, ' ').replace(/([a-z])([A-Z])/g, '$1 $2');
	return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}
