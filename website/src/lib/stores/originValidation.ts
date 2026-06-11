// Live draft validation, mapped to form fields.
//
// The JSON Preview tab has always run AJV over the serialized bundle, but
// its errors never reached the form editors — a user typing on the Powers
// tab had no idea a field was invalid until they switched tabs. This store
// closes that gap: it re-validates on every draft edit and translates each
// AJV error's `instancePath` into a (power index, top-level field key)
// address the form components can pin an inline message to.
//
// Mapping strategy (pragmatic, per the 2026-06-11 audit decisions):
//   - Power errors: validate each serialized power JSON; the FIRST segment
//     of `instancePath` (e.g. `/radius`, `/entity_action/effect` → `radius`,
//     `entity_action`) is the form field key. `required` errors at the root
//     map via `params.missingProperty`.
//   - Origin errors: same first-segment rule against the Identity-tab keys.
//   - Anything unmappable (root-level `oneOf` mismatches, empty pointers)
//     lands in a per-power / origin-level `unmapped` bucket the components
//     render as a summary block — never dropped silently.
//   - Draft-level id problems (blank/duplicate ids — not visible to AJV
//     because the wire JSON doesn't carry the filename) come from the same
//     `validateDraftIds` the export gate uses, keyed to `namespace` /
//     `path` / per-power `id`.
//
// AJV setup is async (schema fetch on first use, then cached), so the store
// is a `derived` with an async `set`. A monotonically increasing token
// drops stale results when edits outrun validation.

import { derived, type Readable } from 'svelte/store';
import { browser } from '$app/environment';
import { base } from '$app/paths';

import { draft, type OriginDraft } from './originDraft';
import { serializeOrigin } from '$lib/schema/originSerializer';
import { validateDraftIds } from '$lib/datapack/export';
import { getValidator, type ErrorObject } from '$lib/schema/ajv';

/** One inline-displayable problem. */
export interface InlineIssue {
	/** JSON pointer within the serialized object (or `/<draftField>`). */
	pointer: string;
	message: string;
	/** AJV keyword, or `'draft'` for id-gate issues. */
	keyword: string;
}

/** Issues for a single power card, keyed by top-level form field name. */
export interface PowerIssues {
	fields: Record<string, InlineIssue[]>;
	/** Errors with no mappable field — shown as a per-power summary. */
	unmapped: InlineIssue[];
}

export interface OriginValidationState {
	/**
	 * `loading` until the first pass finishes; `unavailable` when the AJV
	 * schemas can't be fetched/compiled (draft-level id issues still
	 * populate in that case — they don't need AJV).
	 */
	status: 'loading' | 'ready' | 'unavailable';
	errorText?: string;
	/** Origin-level issues keyed by Identity-tab field (name, icon, ...). */
	originFields: Record<string, InlineIssue[]>;
	/** Origin-level issues with no mappable field. */
	originUnmapped: InlineIssue[];
	/** Per-power issues, index-aligned with `draft.powers`. */
	powers: PowerIssues[];
}

const INITIAL: OriginValidationState = {
	status: 'loading',
	originFields: {},
	originUnmapped: [],
	powers: []
};

/** First `instancePath` segment, or `params.missingProperty` for root `required`. */
function fieldKeyOf(e: ErrorObject): string | null {
	if (e.instancePath && e.instancePath.length > 1) {
		const seg = e.instancePath.split('/')[1];
		return seg || null;
	}
	if (e.keyword === 'required') {
		const mp = (e.params as { missingProperty?: unknown } | undefined)?.missingProperty;
		return typeof mp === 'string' ? mp : null;
	}
	return null;
}

function toInline(e: ErrorObject): InlineIssue {
	return {
		pointer: e.instancePath || '(root)',
		message: e.message ?? '(no message)',
		keyword: e.keyword
	};
}

/** Append, deduping identical (keyword, message, pointer) triples — AJV's
 *  `allErrors` over big `oneOf` enums loves repeating itself. */
function push(bucket: InlineIssue[], issue: InlineIssue): void {
	const dup = bucket.some(
		(x) => x.keyword === issue.keyword && x.message === issue.message && x.pointer === issue.pointer
	);
	if (!dup) bucket.push(issue);
}

function pushField(map: Record<string, InlineIssue[]>, key: string, issue: InlineIssue): void {
	const bucket = (map[key] ??= []);
	push(bucket, issue);
}

async function runValidation(d: OriginDraft): Promise<OriginValidationState> {
	const state: OriginValidationState = {
		status: 'ready',
		originFields: {},
		originUnmapped: [],
		powers: d.powers.map(() => ({ fields: {}, unmapped: [] }))
	};

	// Draft-level id gate (synchronous; mirrors what export will refuse).
	for (const issue of validateDraftIds(d)) {
		const inline: InlineIssue = {
			pointer: `/${issue.field}`,
			message: issue.message,
			keyword: 'draft'
		};
		if (issue.scope === 'power' && issue.powerIndex !== undefined) {
			const slot = state.powers[issue.powerIndex];
			if (slot) pushField(slot.fields, issue.field, inline);
			else state.originUnmapped.push(inline);
		} else {
			pushField(state.originFields, issue.field, inline);
		}
	}

	// AJV pass over the serialized wire JSON.
	try {
		const bundle = serializeOrigin(d);
		const originValidator = await getValidator(`${base}/schemas/origin.schema.json`);
		const powerValidator = await getValidator(`${base}/schemas/power.schema.json`);

		if (!originValidator(bundle.origin)) {
			for (const e of originValidator.errors ?? []) {
				const key = fieldKeyOf(e);
				if (key) pushField(state.originFields, key, toInline(e));
				else push(state.originUnmapped, toInline(e));
			}
		}
		bundle.powers.forEach((p, i) => {
			const slot = state.powers[i];
			if (!slot) return;
			if (!powerValidator(p.json)) {
				for (const e of powerValidator.errors ?? []) {
					const key = fieldKeyOf(e);
					if (key) pushField(slot.fields, key, toInline(e));
					else push(slot.unmapped, toInline(e));
				}
			}
		});
	} catch (err) {
		state.status = 'unavailable';
		state.errorText = err instanceof Error ? err.message : String(err);
	}

	return state;
}

let token = 0;

/**
 * Live validation of the Origin draft, re-run on every edit. Subscribe
 * from IdentityTab / PowersTab to render inline field errors.
 */
export const originValidation: Readable<OriginValidationState> = derived(
	draft,
	($draft, set) => {
		if (!browser) {
			set(INITIAL);
			return;
		}
		const mine = ++token;
		void runValidation($draft).then((state) => {
			if (mine === token) set(state);
		});
	},
	INITIAL
);
