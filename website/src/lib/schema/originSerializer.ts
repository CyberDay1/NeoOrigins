// Origin draft → datapack-shape JSON serializer.
//
// Shared by the JSON Preview tab (task #14) and the datapack export
// (task #15). The serializer's job is to lower the editor's in-memory
// `OriginDraft` into the wire-format JSON the in-game mod loads from a
// datapack, exactly as documented in `docs/schema/origin.schema.json`
// and `docs/schema/power.schema.json`.
//
// Key MVP decisions (locked in `planning/web_editor_scope.md`):
//   - No translation editor — `name` and `description` are emitted as
//     raw strings, not `{translate, fallback}` components. Authors who
//     want translations can post-process the JSON.
//   - Empty-string fields are omitted entirely (not serialized as `""`).
//   - `impact` is stored lowercase in the draft (matches the wire
//     format directly) and emitted as-is, omitting the default `'none'`.
//   - Origin `id` is split on `:` for namespace + local id. If the user
//     forgets the namespace half, we default to `neoorigins`.
//   - Per-power JSON paths follow the schema description:
//     `data/<namespace>/origins/powers/<powerLocalId>.json`.
//     The origin's `powers[]` is the fully-qualified power id list
//     prefixed with the origin's namespace.

import type { OriginDraft, PowerDraft } from '$lib/stores/originDraft';

// ── public output shape ─────────────────────────────────────────────────────

export interface SerializedOrigin {
	/** Display name. Raw string for MVP — Phase 2 may emit `{translate}`. */
	name?: { translate?: string; fallback?: string } | string;
	description?: { translate?: string; fallback?: string } | string;
	/** Item id (`minecraft:diamond`) or short text glyph. */
	icon?: string;
	/** Lowercase on the wire (matches the draft). */
	impact?: 'none' | 'low' | 'medium' | 'high';
	order?: number;
	/** Hidden from origin selection. Omitted when false. */
	unchoosable?: boolean;
	/** Excluded from listings (developer/testing). Omitted when false. */
	hidden?: boolean;
	/** Fully-qualified power IDs (e.g. `mypack:flight`). */
	powers?: string[];
}

export interface SerializedPower {
	type: string;
	[k: string]: unknown;
}

export interface SerializedPowerEntry {
	/** Local id within the origin's namespace (no `<ns>:` prefix). */
	id: string;
	/** Fully-qualified id (`<ns>:<localId>`). */
	fullId: string;
	json: SerializedPower;
	/** `data/<namespace>/origins/powers/<localId>.json`. */
	path: string;
}

export interface SerializedDatapackBundle {
	/** Origin namespace (e.g. `mypack`). */
	namespace: string;
	/** Origin local id (e.g. `wizard`). */
	localId: string;
	origin: SerializedOrigin;
	/** `data/<namespace>/origins/origins/<localId>.json`. */
	originPath: string;
	powers: SerializedPowerEntry[];
}

// ── implementation ──────────────────────────────────────────────────────────

const DEFAULT_NAMESPACE = 'neoorigins';

/**
 * Split a namespaced id into `[namespace, localId]`. If no `:` is
 * present, returns `[DEFAULT_NAMESPACE, raw]`. Either half may end up
 * empty — callers should treat that as a draft-not-ready state, not
 * substitute a fake id.
 */
export function splitId(raw: string): [string, string] {
	const idx = raw.indexOf(':');
	if (idx < 0) return [DEFAULT_NAMESPACE, raw];
	return [raw.slice(0, idx), raw.slice(idx + 1)];
}

function serializePower(
	power: PowerDraft,
	namespace: string
): SerializedPowerEntry {
	// PowerDraft.id is the LOCAL id (per task #13's design — the powers
	// store doesn't carry the namespace; it inherits from the origin).
	const localId = power.id;
	const fullId = `${namespace}:${localId}`;

	// Spread the form-driven fields under `type`. Drop empty strings —
	// same MVP rule as origin fields. `null` and `0` are preserved
	// (those are meaningful values, not user-forgot-to-fill blanks).
	const json: SerializedPower = { type: power.type };
	for (const [k, v] of Object.entries(power.fields)) {
		if (v === '' || v === undefined) continue;
		json[k] = v;
	}

	return {
		id: localId,
		fullId,
		json,
		path: `data/${namespace}/origins/powers/${localId}.json`
	};
}

/**
 * Lower an `OriginDraft` into datapack JSON. See module docstring for
 * the locked MVP decisions.
 */
export function serializeOrigin(draft: OriginDraft): SerializedDatapackBundle {
	const [namespace, localId] = splitId(draft.id);

	const powers = draft.powers.map((p) => serializePower(p, namespace));

	const origin: SerializedOrigin = {};
	if (draft.name) origin.name = draft.name;
	if (draft.description) origin.description = draft.description;
	if (draft.icon) origin.icon = draft.icon;
	// `impact === 'none'` is the default the in-game side assumes when
	// the field is absent; omit it to keep the JSON minimal. Anything
	// non-default is emitted as-is (already lowercase in the draft).
	if (draft.impact && draft.impact !== 'none') {
		origin.impact = draft.impact;
	}
	if (draft.order !== 0) origin.order = draft.order;
	// Boolean flags: only emit when true (false is the schema default).
	if (draft.unchoosable) origin.unchoosable = true;
	if (draft.hidden) origin.hidden = true;
	// `powers` is REQUIRED by origin.schema.json — always emit, even if
	// empty (the schema allows an empty array, the mod tolerates it).
	origin.powers = powers.map((p) => p.fullId);

	return {
		namespace,
		localId,
		origin,
		originPath: `data/${namespace}/origins/origins/${localId}.json`,
		powers
	};
}
