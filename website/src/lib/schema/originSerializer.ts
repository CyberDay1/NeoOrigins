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
//   - Origin id is stored as two independent fields on the draft —
//     `namespace` (defaults to `neoorigins`) and `path` — mirroring the
//     in-game Java editor's `idPath` + `CUSTOM_NAMESPACE` split. We read
//     each half directly; no `:` splitting needed.
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
	/**
	 * Optional progression chain. Emitted only when the draft has a
	 * non-empty `upgrades` array. Shape matches `origin.schema.json`.
	 */
	upgrades?: Array<{ advancement: string; origin: string; announcement?: string }>;
	/**
	 * Passthrough for `OriginDraft.extras` — origin-JSON keys the editor has
	 * no UI for (`tier_powers`, `spawn_location`, `required_mods`, ...). The
	 * index signature is what lets them be written back out; nothing in the
	 * editor reads them.
	 */
	[k: string]: unknown;
}

/**
 * Top-level origin-JSON keys this serializer produces from mapped draft
 * fields. Anything outside this set is unknown to the editor UI and rides
 * along in `OriginDraft.extras` instead of being dropped — see
 * `$lib/datapack/import.ts`, which imports this set to decide what to carry.
 *
 * Keep in step with `serializeOrigin` below: a key that moves from extras to
 * a real editor field must be added here, or the importer will keep stuffing
 * it into extras and the serializer will then refuse to emit it (mapped
 * fields win) and the value is lost.
 */
export const MAPPED_ORIGIN_KEYS: readonly string[] = [
	'name',
	'description',
	'icon',
	'impact',
	'order',
	'unchoosable',
	'hidden',
	'powers',
	'upgrades'
] as const;

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
	/**
	 * Layer-extension file path, e.g.
	 * `data/<userNamespace>/origins/origin_layers/<layerPath>.json`.
	 *
	 * The layer-extension file lives under the USER'S pack namespace,
	 * not `neoorigins:` — the loader's `LayerDataManager` merges
	 * same-path layer files across namespaces back onto the canonical
	 * layer (see `LayerDataManager.mergeForeignSamePathLayers`). This
	 * matches what `examples/custom_class/` ships.
	 */
	layerExtensionPath: string;
	/**
	 * Contents of the layer-extension file. `replace: false` is the
	 * additive default — without it, the user's pack would wipe the
	 * vanilla layer entries instead of adding to them.
	 */
	layerExtension: { replace: boolean; origins: string[] };
}

// ── implementation ──────────────────────────────────────────────────────────

/**
 * Recursively drop "unset" values for the wire JSON: empty string, `null`,
 * `undefined`, empty array, and empty object collapse to `undefined` (the
 * caller omits the key). `0`, `false`, non-empty strings, and populated
 * arrays/objects are kept. Used to keep nested action/condition sub-forms
 * (D4 RefRow / ArrayRefRow) from leaking blank optional fields or unpicked
 * array slots into the exported datapack.
 */
function pruneForWire(v: unknown): unknown {
	if (v === '' || v === null || v === undefined) return undefined;
	if (Array.isArray(v)) {
		const out = v.map(pruneForWire).filter((x) => x !== undefined);
		return out.length > 0 ? out : undefined;
	}
	if (typeof v === 'object') {
		const out: Record<string, unknown> = {};
		for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
			const pruned = pruneForWire(val);
			if (pruned !== undefined) out[k] = pruned;
		}
		return Object.keys(out).length > 0 ? out : undefined;
	}
	return v;
}

export function serializePower(
	power: PowerDraft,
	namespace: string
): SerializedPowerEntry {
	// PowerDraft.id is the LOCAL id (per task #13's design — the powers
	// store doesn't carry the namespace; it inherits from the origin).
	const localId = power.id;
	const fullId = `${namespace}:${localId}`;

	// Spread the form-driven fields under `type`, pruning unset values. Empty
	// strings, `null`, `undefined`, and empty arrays/objects are dropped (a
	// blank field is "not authored", not a meaningful value); `0` and `false`
	// are kept. The prune recurses into the nested action/condition OBJECTs that
	// the D4 RefRow / ArrayRefRow produce so e.g. an unfilled optional sub-field
	// or an unpicked array slot doesn't leak into the wire JSON. RawJson values
	// are plain strings and pass through atomically.
	const json: SerializedPower = { type: power.type };
	for (const [k, v] of Object.entries(power.fields)) {
		const pruned = pruneForWire(v);
		if (pruned === undefined) continue;
		json[k] = pruned;
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
	// Read the two halves directly off the draft — no `:` splitting. The
	// draft model mirrors the in-game Java editor, which stores the path
	// segment alongside a separately-managed namespace.
	const namespace = draft.namespace;
	const localId = draft.path;

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
	// `hidden` folds into `unchoosable`: the mod has no native origin-level
	// `hidden` — a top-level `hidden` key trips OriginsFormatDetector, routing
	// the file through the Origins-compat translator, which (a) maps hidden →
	// unchoosable anyway and (b) DROPS native-only fields it doesn't whitelist
	// (e.g. `tier_powers`, `required_mods`). Emitting `unchoosable` directly is
	// semantics-identical and keeps the file on the native parse path.
	if (draft.unchoosable || draft.hidden) origin.unchoosable = true;
	// `powers` is REQUIRED by origin.schema.json — always emit, even if
	// empty (the schema allows an empty array, the mod tolerates it).
	// `externalPowers` are grants the importer could not find a file for
	// (mod built-ins, other packs); re-emitting them keeps the round trip from
	// silently revoking abilities on a pack that still loads without them.
	origin.powers = [
		...powers.map((p) => p.fullId),
		...(draft.externalPowers ?? [])
	];

	// `upgrades` is optional in the schema — emit only when authored.
	if (draft.upgrades && draft.upgrades.length > 0) {
		origin.upgrades = draft.upgrades.map((u) => {
			const entry: { advancement: string; origin: string; announcement?: string } = {
				advancement: u.advancement,
				origin: u.origin
			};
			if (u.announcement && u.announcement !== '') {
				entry.announcement = u.announcement;
			}
			return entry;
		});
	}

	// Keys the editor has no UI for, carried over from an import. Written
	// last so the Identity fields still read at the top of the JSON Preview,
	// and skipped whenever the key is one the editor owns — a mapped field
	// always wins over a stale extras entry.
	if (draft.extras) {
		for (const [k, v] of Object.entries(draft.extras)) {
			if (MAPPED_ORIGIN_KEYS.includes(k)) continue;
			if (v === undefined) continue;
			origin[k] = v;
		}
	}

	// Layer-extension file path uses the path-portion of the layer id
	// (e.g. `neoorigins:class` → `class.json`). The file itself lives
	// under the USER'S namespace so the loader's same-path merger folds
	// it onto the canonical layer.
	const layerPath = draft.layerId.includes(':')
		? draft.layerId.split(':', 2)[1]
		: draft.layerId;

	return {
		namespace,
		localId,
		origin,
		originPath: `data/${namespace}/origins/origins/${localId}.json`,
		powers,
		layerExtensionPath: `data/${namespace}/origins/origin_layers/${layerPath}.json`,
		layerExtension: {
			replace: false,
			origins: [`${namespace}:${localId}`]
		}
	};
}
