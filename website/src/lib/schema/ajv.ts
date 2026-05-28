// AJV (draft-2020) singleton + lazy schema loader.
//
// The web editor uses AJV exclusively for the "validate" pass on the
// JSON Preview tab — schema-driven form rendering is handled by
// `SchemaFormModel.ts` (the in-game discriminated-`oneOf` walker, not AJV).
//
// Schemas live at `${base}/schemas/*.json` (mirrored from `docs/schema/`
// by the pages workflow + the dev `static/schemas/` copy). We fetch the
// requested URL on demand, compile it, and cache the resulting validator
// by URL. Sibling `$ref`s (e.g. `condition.schema.json`) are pre-registered
// alongside the requested schema so AJV's resolver can find them under
// their `$id`.

import Ajv2020, { type ValidateFunction, type ErrorObject } from 'ajv/dist/2020';
import addFormats from 'ajv-formats';

// Single shared AJV instance across the whole app — schemas registered
// once stay cached for the page's lifetime. `strict: false` because the
// hand-written NeoOrigins schemas use a few non-standard keywords
// (`$comment` as a discriminator, JSON-Schema-isms AJV's strict mode
// rejects) and `allErrors` so the preview tab can show the full list.
const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);

const validatorCache = new Map<string, Promise<ValidateFunction>>();
const schemaDocCache = new Map<string, Promise<unknown>>();

// Sibling schemas referenced via `$ref: "condition.schema.json"` etc.
// Whenever we fetch any of these, we eagerly fetch the others so AJV's
// ref resolver always finds them registered under their `$id`.
const SIBLING_SCHEMAS = [
	'power.schema.json',
	'origin.schema.json',
	'condition.schema.json',
	'action.schema.json',
	'origin_layer.schema.json'
] as const;

function dirOf(url: string): string {
	const slash = url.lastIndexOf('/');
	return slash >= 0 ? url.slice(0, slash + 1) : '';
}

async function fetchSchema(url: string): Promise<unknown> {
	let p = schemaDocCache.get(url);
	if (!p) {
		p = (async () => {
			const res = await fetch(url);
			if (!res.ok) {
				throw new Error(`failed to fetch schema ${url}: ${res.status} ${res.statusText}`);
			}
			return (await res.json()) as unknown;
		})();
		schemaDocCache.set(url, p);
	}
	return p;
}

function addSchemaIfMissing(doc: unknown): void {
	if (!doc || typeof doc !== 'object') return;
	const id =
		(doc as { $id?: unknown }).$id !== undefined
			? String((doc as { $id?: unknown }).$id)
			: undefined;
	if (id && ajv.getSchema(id)) return;
	try {
		ajv.addSchema(doc as object);
	} catch (e) {
		// Adding the same $id twice throws; ignore — getSchema check above
		// guards the common case but a race in fetch() may slip through.
		if (!String(e).includes('already exists')) throw e;
	}
}

/**
 * Resolve (or compile) a validator for the schema at `schemaUrl`.
 *
 * Caches both the validator and the raw schema doc. On first call for a
 * given directory, pre-registers every sibling schema in the same folder
 * so cross-file `$ref`s resolve without an extra round-trip.
 */
export async function getValidator(schemaUrl: string): Promise<ValidateFunction> {
	let p = validatorCache.get(schemaUrl);
	if (!p) {
		p = (async () => {
			const dir = dirOf(schemaUrl);
			// Pre-register siblings (best-effort — missing schemas are fine,
			// they just mean the user opted out of that ref target).
			await Promise.all(
				SIBLING_SCHEMAS.map(async (name) => {
					try {
						const doc = await fetchSchema(dir + name);
						addSchemaIfMissing(doc);
					} catch {
						/* sibling absent — ignore */
					}
				})
			);
			const doc = await fetchSchema(schemaUrl);
			addSchemaIfMissing(doc);
			// If we just added it via $id, compile via getSchema; otherwise
			// fall through to ajv.compile() with the raw doc.
			const id =
				doc && typeof doc === 'object' && (doc as { $id?: unknown }).$id !== undefined
					? String((doc as { $id?: unknown }).$id)
					: undefined;
			if (id) {
				const v = ajv.getSchema(id);
				if (v) return v;
			}
			return ajv.compile(doc as object);
		})();
		validatorCache.set(schemaUrl, p);
	}
	return p;
}

export type { ValidateFunction, ErrorObject };
export { ajv };
