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
//     same schema document (e.g. `#/$defs/foo`).
//   - D4: cross-document refs to `action.schema.json` / `condition.schema.json`
//     (and the self-`$ref:"#"` inside those documents) now map to REF /
//     ARRAY_REF field specs, which the recursive RefRow / ArrayRefRow render
//     as nested sub-forms. {@link parseRefSchema} walks an action/condition
//     document for a chosen type exactly the way {@link parsePowerSchema}
//     walks the power document — they share one discriminated-oneOf core.
//     Cross-document refs that target NEITHER sibling schema still fall to
//     RawJson(REF).
//   - The Apoli scalar-or-array idiom (`oneOf: [{$ref}, {array of the same
//     $ref}]`, e.g. `condition_passive.condition`) is ALSO an ARRAY_REF — flagged
//     `scalarOrArray` so a single entry serializes back as a bare object. Every
//     other `oneOf` (string | object unions) still falls to RawJson(MIXED).
//   - An OBJECT field with a FIXED set of inline `properties` (e.g. an item
//     stack, an effect instance, hud_render) maps to an OBJECT spec whose
//     `children` are the parsed sub-fields, rendered inline as a labeled
//     sub-form (no type picker). Free-form objects (no `properties`) still
//     fall to RawJson.

import type {
	ArrayRefFieldSpec,
	ArrayStringFieldSpec,
	BooleanFieldSpec,
	EnumFieldSpec,
	FormFieldSpec,
	IntegerFieldSpec,
	NumberFieldSpec,
	ObjectFieldSpec,
	RawJsonFieldSpec,
	RefFieldSpec,
	StringFieldSpec
} from './FormFieldSpec.js';

/** Which schema document a field's `$ref` resolves into ({@link SelfDoc} drives `#`). */
export type RefDoc = 'action' | 'condition' | 'block_condition' | 'item_condition' | 'item_action';

/** The document a discriminated walk is rooted in, so `$ref:"#"` self-refs resolve. */
type SelfDoc = 'power' | RefDoc;

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
	return parseDiscriminated(schema, fieldDocs, powerType, 'power', 'power type');
}

/**
 * Parse the field list for a chosen action/condition type out of
 * `action.schema.json` / `condition.schema.json`. The recursive RefRow calls
 * this when the author picks a type, then renders the returned specs inline.
 *
 * @param refDoc    Which sibling schema `schema` is (`'action'` | `'condition'`).
 *                  Drives how the document's own `$ref:"#"` self-refs resolve.
 * @param schema    The full parsed action/condition schema document.
 * @param fieldDocs The full parsed `field_docs.json` document.
 * @param typeId    The fully-qualified action/condition id (e.g. `"neoorigins:damage"`).
 * @returns         The chosen branch's `FormFieldSpec[]` (no common-root fields —
 *                  action/condition roots carry only the `type` discriminator).
 *                  `[]` for a type with no structured `oneOf` branch.
 * @throws          `Error` if `typeId` is not in the document's `type.enum`.
 */
export function parseRefSchema(
	refDoc: RefDoc,
	schema: object,
	fieldDocs: object,
	typeId: string
): FormFieldSpec[] {
	return parseDiscriminated(schema, fieldDocs, typeId, refDoc, `${refDoc} type`);
}

/** The de-duplicated `type.enum` universe of a power/action/condition schema. */
export function refTypeOptions(schema: object): string[] {
	const seen = new Set<string>();
	const out: string[] = [];
	for (const id of readTypeEnum(schema as JsonObject)) {
		if (seen.has(id)) continue;
		seen.add(id);
		out.push(id);
	}
	return out;
}

/**
 * Shared discriminated-oneOf walk behind {@link parsePowerSchema} and
 * {@link parseRefSchema}. Emits common-root fields (everything but `type`)
 * then the matched branch's fields. `selfDoc` lets `$ref:"#"` self-refs and
 * cross-document action/condition refs resolve to REF / ARRAY_REF specs.
 */
function parseDiscriminated(
	schema: object,
	fieldDocs: object,
	typeId: string,
	selfDoc: SelfDoc,
	label: string
): FormFieldSpec[] {
	const root = schema as JsonObject;
	const docs = fieldDocs as FieldDocs;

	// Sanity: the type must appear in the schema's universe.
	const typeEnum = readTypeEnum(root);
	if (!typeEnum.includes(typeId)) {
		throw new Error(`${label} not in schema enum: ${typeId}`);
	}

	// Common-root fields (skip `type` — driven by the picker, not a row). For
	// action/condition documents the root carries only `type`, so this is empty.
	const commonFields = commonRootFields(root, docs, typeId, selfDoc);

	// Find the matching structured branch.
	const branch = findStructuredBranch(root, typeId);
	if (!branch) return commonFields; // fallback branch → raw-JSON only

	// Branch fields, in schema-declared order, skipping `type` again.
	const branchProps = (branch.properties ?? {}) as JsonObject;
	const branchRequired = readRequiredSet(branch);
	const branchFields: FormFieldSpec[] = [];
	for (const [name, raw] of Object.entries(branchProps)) {
		if (name === 'type') continue;
		const propSchema = derefOneLevel(root, raw as JsonValue);
		branchFields.push(
			mapProperty(root, name, propSchema, branchRequired.has(name), docs, typeId, selfDoc)
		);
	}
	// Branch fields shadow same-named common-root fields (e.g. `neoorigins:resource`
	// declares its own `hidden`) — keep the branch spec, drop the common one, so
	// keyed each-blocks never see duplicate field paths.
	const branchNames = new Set(branchFields.map((f) => f.name));
	return [...commonFields.filter((f) => !branchNames.has(f.name)), ...branchFields];
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
	powerType: string,
	selfDoc: SelfDoc
): FormFieldSpec[] {
	const props = (root['properties'] ?? {}) as JsonObject;
	const required = readRequiredSet(root);
	const out: FormFieldSpec[] = [];
	for (const [name, raw] of Object.entries(props)) {
		if (name === 'type') continue;
		out.push(
			mapProperty(root, name, raw as JsonValue, required.has(name), docs, powerType, selfDoc)
		);
	}
	return out;
}

/**
 * Find the `oneOf` branch for `typeId`.
 *
 * Two discriminator shapes coexist: the generated `power.schema.json` keys each
 * branch's `type` as a `const` and carries a bare-id `$comment`, while the
 * hand-written `action.schema.json` / `condition.schema.json` key `type` as an
 * `enum` (so one branch covers a verb plus its `apace:` aliases) under a long
 * grouped `$comment` (e.g. `"neoorigins:and / … — combinators"`). We match on
 * EITHER the branch's own `type` const/enum containing `typeId` (authoritative)
 * OR the `$comment` starting with `typeId` followed by a non-id boundary
 * (em-dash, space, or end) — the latter preserves the original power matching.
 */
function findStructuredBranch(root: JsonObject, typeId: string): JsonObject | null {
	const oneOf = root['oneOf'];
	if (!Array.isArray(oneOf)) return null;
	for (const candidate of oneOf) {
		if (!isObject(candidate)) continue;
		if (branchTypeMatches(candidate, typeId)) return candidate;
		const comment = candidate['$comment'];
		if (typeof comment !== 'string') continue;
		if (comment === typeId || comment.startsWith(typeId + ' ') ||
			comment.startsWith(typeId + '\u2014') ||
			comment.startsWith(typeId + ' \u2014')) {
			return candidate;
		}
	}
	return null;
}

/** True when a branch's `properties.type` pins `typeId` via `const` or `enum`. */
function branchTypeMatches(branch: JsonObject, typeId: string): boolean {
	const props = branch['properties'];
	if (!isObject(props)) return false;
	const typeProp = props['type'];
	if (!isObject(typeProp)) return false;
	if (typeProp['const'] === typeId) return true;
	const en = typeProp['enum'];
	return Array.isArray(en) && en.includes(typeId);
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

/**
 * Classify a `$ref` string into which sibling schema document it targets, for
 * the recursive RefRow. `"action.schema.json"` → `'action'`,
 * `"condition.schema.json"` → `'condition'`, and a self-`"#"` resolves to the
 * document currently being walked (`selfDoc`, when that is action/condition —
 * the power document has no self-refs). Same-document pointer refs (`#/...`)
 * are already dereffed by {@link derefOneLevel} before this runs, and any other
 * ref (unknown cross-document) returns `null` so the caller keeps RawJson(REF).
 */
function refDocOf(ref: string, selfDoc: SelfDoc): RefDoc | null {
	if (ref === '#') return selfDoc === 'power' ? null : selfDoc;
	if (ref === 'item_action.schema.json') return 'item_action';
	if (ref === 'action.schema.json') return 'action';
	// `block_condition.schema.json` / `item_condition.schema.json` must be matched
	// before any substring-style check for `condition.schema.json` — each is its
	// own document.
	if (ref === 'block_condition.schema.json') return 'block_condition';
	if (ref === 'item_condition.schema.json') return 'item_condition';
	if (ref === 'condition.schema.json') return 'condition';
	return null;
}

/**
 * Detect the Apoli scalar-or-array idiom — "accept one action/condition, or a
 * list of them" — spelled in the generated schema as
 * `{"oneOf":[{"$ref":X},{"type":"array","items":{"$ref":X}}]}`.
 *
 * Structural, not name-driven: EVERY branch must be either a bare `$ref` or an
 * `array` of `$ref`, at least one of each must be present, and all of them must
 * resolve (via {@link refDocOf}) to the SAME sibling document. Any other mix —
 * a `string | object` union, a `$ref` paired with a scalar type, two different
 * ref targets — returns `null` so the caller keeps the RawJson(MIXED) fallback.
 *
 * @returns The shared {@link RefDoc}, or `null` when the shape doesn't match.
 */
function scalarOrArrayRefDoc(branches: JsonValue[], selfDoc: SelfDoc): RefDoc | null {
	if (branches.length < 2) return null;
	let shared: RefDoc | null = null;
	let sawScalar = false;
	let sawArray = false;
	for (const branch of branches) {
		if (!isObject(branch)) return null;
		let ref: string;
		if (typeof branch['$ref'] === 'string') {
			ref = branch['$ref'] as string;
			sawScalar = true;
		} else if (branch['type'] === 'array') {
			const items = branch['items'];
			if (!isObject(items) || typeof items['$ref'] !== 'string') return null;
			ref = items['$ref'] as string;
			sawArray = true;
		} else {
			return null; // a non-ref branch (string, number, free object) → MIXED
		}
		const doc = refDocOf(ref, selfDoc);
		if (!doc) return null; // unknown cross-document ref → MIXED
		if (shared !== null && shared !== doc) return null; // heterogeneous union → MIXED
		shared = doc;
	}
	return sawScalar && sawArray ? shared : null;
}

function mapProperty(
	root: JsonObject,
	name: string,
	raw: JsonValue,
	required: boolean,
	docs: FieldDocs,
	powerType: string,
	selfDoc: SelfDoc
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

	// `oneOf` — two shapes reach here.
	//
	// 1. The Apoli "one or many" idiom,
	//    `{"oneOf":[{"$ref":X},{"type":"array","items":{"$ref":X}}]}` — every
	//    branch targets the SAME sibling action/condition document, so this is
	//    an ARRAY_REF flagged `scalarOrArray`: the author gets the recursive
	//    sub-form picker and can add one entry or many. This MUST be tested
	//    before the bare-`$ref` branch below, which the shape never reaches
	//    (there is no top-level `$ref` key here, only nested in `oneOf`).
	// 2. Anything else — e.g. the common `name` / `description` fields, which are
	//    string | object — has no per-branch `$comment` discriminator and stays
	//    a RawJson MIXED textarea.
	if (Array.isArray(p['oneOf'])) {
		const oneOfRefDoc = scalarOrArrayRefDoc(p['oneOf'] as JsonValue[], selfDoc);
		if (oneOfRefDoc) {
			const spec: ArrayRefFieldSpec = {
				...base,
				kind: 'ARRAY_REF',
				refDoc: oneOfRefDoc,
				scalarOrArray: true
			};
			return spec;
		}
		return rawJsonOf(base, 'MIXED', p['default']);
	}

	// Bare `$ref` that survived `derefOneLevel` is a cross-document ref
	// (action.schema.json / condition.schema.json) or a self-`$ref:"#"`. When
	// it targets a sibling action/condition document → REF (recursive sub-form);
	// anything else stays a RawJson(REF) escape hatch.
	if (typeof p['$ref'] === 'string') {
		const refDoc = refDocOf(p['$ref'] as string, selfDoc);
		if (refDoc) {
			const spec: RefFieldSpec = { ...base, kind: 'REF', refDoc };
			return spec;
		}
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
			case 'array': {
				// An `array` of cross-document action/condition refs (e.g. the
				// `actions` list on `neoorigins:and`) → ARRAY_REF, rendered as an
				// add/remove list of RefRow sub-forms. Other arrays stay RawJson.
				const items = p['items'];
				if (isObject(items) && typeof items['$ref'] === 'string') {
					const refDoc = refDocOf(items['$ref'] as string, selfDoc);
					if (refDoc) {
						const spec: ArrayRefFieldSpec = {
							...base,
							kind: 'ARRAY_REF',
							refDoc,
							scalarOrArray: false
						};
						return spec;
					}
				}
				// A scalar-string list (`items:{type:"string"}`, no `$ref`) →
				// ARRAY_STRING: an add/remove list of text inputs. `items.pattern`
				// carries the per-element validation hint (e.g. resource-location).
				if (isObject(items) && items['type'] === 'string') {
					const pattern = typeof items['pattern'] === 'string' ? (items['pattern'] as string) : null;
					const spec: ArrayStringFieldSpec = { ...base, kind: 'ARRAY_STRING', pattern };
					return spec;
				}
				return rawJsonOf(base, 'ARRAY', p['default']);
			}
			case 'object': {
				// An object with a FIXED set of inline `properties` (e.g. an item
				// stack, an effect instance, or hud_render) → OBJECT sub-form: parse
				// each child property the same way, render inline (no type picker).
				// Objects without `properties` (free-form maps) stay RawJson.
				const objProps = p['properties'];
				if (isObject(objProps) && Object.keys(objProps).length > 0) {
					const childRequired = readRequiredSet(p);
					const children: FormFieldSpec[] = [];
					for (const [childName, childRaw] of Object.entries(objProps)) {
						if (childName === 'type') continue;
						const childSchema = derefOneLevel(root, childRaw as JsonValue);
						children.push(
							mapProperty(
								root,
								childName,
								childSchema,
								childRequired.has(childName),
								docs,
								powerType,
								selfDoc
							)
						);
					}
					const spec: ObjectFieldSpec = { ...base, kind: 'OBJECT', children };
					return spec;
				}
				return rawJsonOf(base, 'OBJECT', p['default']);
			}
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
