// Datapack `.zip` import — the inverse of `exportDatapack`.
//
// Reads a datapack zip produced by this editor (or a compatible
// hand-authored pack) back into an in-memory `OriginDraft`, so a user
// can round-trip: export, tweak the files by hand or share them, then
// re-open them in the editor. The layout `$lib/datapack/export.ts` writes and
// `originSerializer.ts` documents is the native one:
//
//   pack.mcmeta
//   data/<ns>/origins/origins/<localId>.json          (the origin)
//   data/<ns>/origins/origin_layers/<layerPath>.json  (layer-extension)
//   data/<ns>/origins/powers/<powerLocalId>.json       (one per power)
//
// Import ALSO accepts the Origins/Apoli layout, which drops the `origins/`
// prefix — `data/<ns>/origins/<id>.json`, `data/<ns>/origin_layers/…`,
// `data/<ns>/powers/…`. The mod reads both (every one of `OriginDataManager`,
// `LayerDataManager` and `PowerDataManager` pairs a native `FILE_CONVERTER`
// with a `COMPAT_CONVERTER`), so the editor has to as well: upstream packs
// use the compat layout exclusively, and reading only the native form made
// every third-party pack unimportable.
//
// Import is best-effort and lossy in the same places the serializer is
// lossy: component-form `name`/`description` are flattened to plain
// strings (the MVP editor has no translation UI), and a layer-extension
// file only records the layer's *path* segment — so the reconstructed
// `layerId` always uses the `neoorigins:` namespace. Anything we can't
// faithfully reconstruct is reported via `warnings` rather than thrown,
// so the user gets the draft plus a heads-up about what was approximated.
//
// Top-level origin keys with no editor UI (`tier_powers`, `spawn_location`,
// `required_mods`, `special`, `figura_model`, `figura_models`) are NOT
// dropped — they are stashed on `draft.extras` and written straight back
// out by `serializeOrigin`, so a round trip through the editor leaves them
// byte-identical rather than silently deleting them.
//
// Fatal problems (not a zip, or no origin file at all) throw `ImportError`.

import { unzipSync, strFromU8 } from 'fflate';

import { MAPPED_ORIGIN_KEYS } from '$lib/schema/originSerializer';
import type { OriginDraft, PowerDraft, TargetMcVersion } from '$lib/stores/originDraft';

/** Thrown when the zip can't be parsed into a draft at all. */
export class ImportError extends Error {
	constructor(message: string) {
		super(message);
		this.name = 'ImportError';
	}
}

export interface ImportResult {
	draft: OriginDraft;
	/** Inferred from `pack.mcmeta` (`pack_format` 84 → 26.1, else 1.21.1). */
	targetVersion: TargetMcVersion;
	/** Non-fatal approximations the caller should surface to the user. */
	warnings: string[];
}

// Baseline draft defaults. Kept inline (rather than importing `createDraft`
// from the store) so this module stays a pure data transform with no
// dependency on `$lib/stores/originDraft`, which pulls in `$app/environment`
// and therefore can't load outside SvelteKit (e.g. the tsx test runner).
// Must mirror `createDraft()` in originDraft.ts.
function blankDraft(): OriginDraft {
	return {
		namespace: 'neoorigins',
		path: '',
		layerId: 'neoorigins:origin',
		name: '',
		description: '',
		icon: '',
		impact: 'none',
		order: 0,
		unchoosable: false,
		hidden: false,
		powers: []
	};
}

// Two on-disk layouts are accepted, mirroring the mod's own loaders: each of
// `OriginDataManager`, `LayerDataManager` and `PowerDataManager` registers a
// native `FILE_CONVERTER` under `origins/…` AND a `COMPAT_CONVERTER` at the
// Origins/Apoli location one level up. Reading only the native form meant every
// real Origins pack — which is all of them, upstream never used the `origins/`
// prefix — failed import outright at the origin step.
//
//   native  data/<ns>/origins/origins/<id>.json        (what this editor writes)
//   compat  data/<ns>/origins/<id>.json                (Origins / Apoli packs)
//
// The compat origin pattern must not swallow the sibling directories that live
// alongside it under `origins/`, or `origins/powers/foo.json` would import as an
// origin named `powers/foo`.
const ORIGIN_RE = /^data\/([^/]+)\/origins\/origins\/(.+)\.json$/;
const ORIGIN_COMPAT_RE =
	/^data\/([^/]+)\/origins\/(?!origins\/|origin_layers\/|powers\/|mob_origins\/)(.+)\.json$/;
const LAYER_RE = /^data\/([^/]+)\/origins\/origin_layers\/(.+)\.json$/;
const LAYER_COMPAT_RE = /^data\/([^/]+)\/origin_layers\/(.+)\.json$/;

/** Keys matching either the native or the compat form, native first. */
function keysMatching(
	files: Record<string, Uint8Array>,
	native: RegExp,
	compat: RegExp
): { key: string; match: RegExpMatchArray }[] {
	const out: { key: string; match: RegExpMatchArray }[] = [];
	for (const re of [native, compat]) {
		for (const k of Object.keys(files)) {
			const m = k.match(re);
			if (m && !out.some((e) => e.key === k)) out.push({ key: k, match: m });
		}
	}
	return out;
}

const IMPACTS = ['none', 'low', 'medium', 'high'] as const;
type Impact = (typeof IMPACTS)[number];

function isImpact(v: unknown): v is Impact {
	return typeof v === 'string' && (IMPACTS as readonly string[]).includes(v);
}

/**
 * Flatten a `name`/`description` value into a plain string. The editor emits raw
 * strings, but a pack may use a text component: `{ "text": "..." }` (the common
 * literal form, and what the mod's own origins ship) or a translatable
 * `{ translate, fallback }`. `text` wins, then `fallback`, then the translate key
 * as a last resort.
 *
 * `flattened` is true only when information was actually dropped — a bare string
 * or a lone `text` key round-trips losslessly, so re-importing such a pack warns
 * about nothing. Mirrors `flattenText` in `./mobImport.ts`.
 */
function flattenText(v: unknown): { value: string; flattened: boolean } {
	if (typeof v === 'string') return { value: v, flattened: false };
	if (v && typeof v === 'object') {
		const o = v as Record<string, unknown>;
		const text = typeof o.text === 'string' ? o.text : undefined;
		const fallback = typeof o.fallback === 'string' ? o.fallback : undefined;
		const translate = typeof o.translate === 'string' ? o.translate : undefined;
		const value = text ?? fallback ?? translate ?? '';
		const keys = Object.keys(o);
		// Lossless iff the only key is a plain `text` — anything else
		// (translate, fallback, siblings like `color`/`extra`) loses data.
		const lossless = text !== undefined && keys.length === 1 && keys[0] === 'text';
		return { value, flattened: !lossless };
	}
	return { value: '', flattened: false };
}

function parseJson(files: Record<string, Uint8Array>, key: string): unknown {
	try {
		return JSON.parse(strFromU8(files[key]));
	} catch (e) {
		throw new ImportError(
			`Failed to parse ${key} as JSON: ${e instanceof Error ? e.message : String(e)}`
		);
	}
}

/**
 * Decompress a datapack `.zip` and reconstruct an `OriginDraft`.
 *
 * @throws {ImportError} if the bytes aren't a readable zip, or contain no
 *   `data/<ns>/origins/origins/<id>.json` origin file.
 */
export function importDatapack(bytes: Uint8Array): ImportResult {
	let files: Record<string, Uint8Array>;
	try {
		files = unzipSync(bytes);
	} catch (e) {
		throw new ImportError(
			`Not a readable .zip: ${e instanceof Error ? e.message : String(e)}`
		);
	}
	return buildDraft(files);
}

/**
 * Reconstruct an `OriginDraft` from an already-decompressed datapack file map
 * (path → bytes), in the layout {@link importDatapack} documents. Shared by
 * the `.zip` importer and the "Load vanilla template" loader so both go
 * through identical body→draft mapping and warning logic.
 *
 * @throws {ImportError} if there's no `data/<ns>/origins/origins/<id>.json`.
 */
export function buildDraft(files: Record<string, Uint8Array>): ImportResult {
	const warnings: string[] = [];

	// ── locate the origin file ───────────────────────────────────────────
	const originEntries = keysMatching(files, ORIGIN_RE, ORIGIN_COMPAT_RE);
	if (originEntries.length === 0) {
		throw new ImportError(
			'No origin file found (expected data/<namespace>/origins/origins/<id>.json ' +
				'or data/<namespace>/origins/<id>.json).'
		);
	}
	if (originEntries.length > 1) {
		warnings.push(
			`Datapack defines ${originEntries.length} origins; importing the first ` +
				`(${originEntries[0].key}). The editor edits one origin at a time.`
		);
	}
	const originKey = originEntries[0].key;
	const namespace = originEntries[0].match[1];
	const localId = originEntries[0].match[2];

	// ── target version from pack.mcmeta ──────────────────────────────────
	let targetVersion: TargetMcVersion = '1.21.1';
	if ('pack.mcmeta' in files) {
		const meta = parseJson(files, 'pack.mcmeta') as
			| { pack?: { pack_format?: unknown } }
			| undefined;
		const fmt = meta?.pack?.pack_format;
		if (fmt === 84) {
			targetVersion = '26.1';
		} else if (fmt === 48) {
			targetVersion = '1.21.1';
		} else {
			warnings.push(
				`Unrecognized pack_format ${JSON.stringify(fmt)}; defaulting target to MC 1.21.1.`
			);
		}
	} else {
		warnings.push('No pack.mcmeta found; defaulting target to MC 1.21.1.');
	}

	// ── origin body ──────────────────────────────────────────────────────
	const originJson = parseJson(files, originKey) as Record<string, unknown>;
	const draft: OriginDraft = blankDraft();
	draft.namespace = namespace;
	draft.path = localId;

	const name = flattenText(originJson.name);
	const description = flattenText(originJson.description);
	draft.name = name.value;
	draft.description = description.value;
	if (name.flattened || description.flattened) {
		warnings.push(
			'Component-form text was flattened to a plain string; any translate key, ' +
				'fallback or styling siblings were dropped.'
		);
	}

	if (typeof originJson.icon === 'string') draft.icon = originJson.icon;

	if (originJson.impact !== undefined) {
		if (isImpact(originJson.impact)) {
			draft.impact = originJson.impact;
		} else {
			warnings.push(
				`Unknown impact ${JSON.stringify(originJson.impact)}; defaulting to "none".`
			);
		}
	}

	if (typeof originJson.order === 'number') draft.order = originJson.order;
	draft.unchoosable = originJson.unchoosable === true;
	draft.hidden = originJson.hidden === true;

	// ── upgrades (optional) ──────────────────────────────────────────────
	if (Array.isArray(originJson.upgrades) && originJson.upgrades.length > 0) {
		const upgrades: NonNullable<OriginDraft['upgrades']> = [];
		for (const u of originJson.upgrades) {
			if (
				u &&
				typeof u === 'object' &&
				typeof (u as Record<string, unknown>).advancement === 'string' &&
				typeof (u as Record<string, unknown>).origin === 'string'
			) {
				const entry = u as { advancement: string; origin: string; announcement?: unknown };
				upgrades.push({
					advancement: entry.advancement,
					origin: entry.origin,
					...(typeof entry.announcement === 'string'
						? { announcement: entry.announcement }
						: {})
				});
			} else {
				warnings.push('Skipped a malformed upgrades entry (missing advancement/origin).');
			}
		}
		if (upgrades.length > 0) draft.upgrades = upgrades;
	}

	// ── keys the editor has no UI for ────────────────────────────────────
	//
	// `origin.schema.json` declares fifteen top-level properties and the
	// Identity tab covers nine. The rest — `tier_powers`, `spawn_location`,
	// `required_mods`, `special`, `figura_model`, `figura_models` — used to be
	// read past and dropped, so importing a pack and exporting it again wrote
	// a *different* origin with an empty warning list. Carry them verbatim
	// instead; the serializer writes them back out untouched.
	const extras: Record<string, unknown> = {};
	for (const [k, v] of Object.entries(originJson)) {
		if (MAPPED_ORIGIN_KEYS.includes(k)) continue;
		extras[k] = v;
	}
	const extraKeys = Object.keys(extras);
	if (extraKeys.length > 0) {
		draft.extras = extras;
		warnings.push(
			`Carried ${extraKeys.length} field${extraKeys.length === 1 ? '' : 's'} the editor ` +
				`has no UI for (${extraKeys.join(', ')}). They are preserved exactly on ` +
				`export, but you can only change them by hand.`
		);
	}

	// ── powers ───────────────────────────────────────────────────────────
	const powerRefs = Array.isArray(originJson.powers) ? originJson.powers : [];
	const powers: PowerDraft[] = [];
	// Refs with no file in the zip. These are legitimate — a pack may grant a
	// power that ships in the mod (`neoorigins:*`) or in another datapack — so
	// they are carried through to the export rather than dropped, or the round
	// trip would quietly strip grants and leave a weaker origin that still loads.
	const externalPowers: string[] = [];
	for (const ref of powerRefs) {
		if (typeof ref !== 'string' || !ref.includes(':')) {
			warnings.push(`Skipped malformed power reference ${JSON.stringify(ref)}.`);
			continue;
		}
		const colon = ref.indexOf(':');
		const powerNs = ref.slice(0, colon);
		const powerLocalId = ref.slice(colon + 1);
		const powerKey = [
			`data/${powerNs}/origins/powers/${powerLocalId}.json`,
			`data/${powerNs}/powers/${powerLocalId}.json`
		].find((k) => k in files);
		if (!powerKey) {
			externalPowers.push(ref);
			continue;
		}
		const powerJson = parseJson(files, powerKey) as Record<string, unknown>;
		const type = typeof powerJson.type === 'string' ? powerJson.type : '';
		if (!type) {
			warnings.push(`Power "${ref}" has no "type" field — skipped.`);
			continue;
		}
		if (powerNs !== namespace) {
			warnings.push(
				`Power "${ref}" lives in a different namespace than the origin; ` +
					`it will be re-exported under "${namespace}:${powerLocalId}".`
			);
		}
		const fields: Record<string, unknown> = {};
		for (const [k, v] of Object.entries(powerJson)) {
			if (k === 'type') continue;
			fields[k] = v;
		}
		powers.push({ id: powerLocalId, type, fields });
	}
	draft.powers = powers;
	if (externalPowers.length > 0) {
		draft.externalPowers = externalPowers;
		warnings.push(
			`${externalPowers.length} power reference` +
				`${externalPowers.length === 1 ? ' has' : 's have'} no power file in this ` +
				`datapack (${externalPowers.join(', ')}) — most likely built into the mod ` +
				`or another pack. They stay in the origin's power list on export, but you ` +
				`can't edit them here.`
		);
	}

	// `tier_powers` rides through in `extras`, but the powers it names are not
	// in the origin's own `powers` list, so nothing above loaded their files
	// and the exporter has nowhere to write them from. Adding them to
	// `draft.powers` would be worse than losing them: the serializer puts every
	// draft power into `powers`, which would grant the whole evolution chain at
	// tier zero. Name them instead so the author knows to copy those files
	// across by hand.
	if (Array.isArray(extras.tier_powers)) {
		const tierRefs = new Set<string>();
		for (const tier of extras.tier_powers) {
			if (!tier || typeof tier !== 'object') continue;
			for (const key of ['add', 'remove'] as const) {
				const list = (tier as Record<string, unknown>)[key];
				if (!Array.isArray(list)) continue;
				for (const ref of list) if (typeof ref === 'string') tierRefs.add(ref);
			}
		}
		for (const p of powers) tierRefs.delete(`${namespace}:${p.id}`);
		if (tierRefs.size > 0) {
			warnings.push(
				`tier_powers references ${tierRefs.size} power` +
					`${tierRefs.size === 1 ? '' : 's'} the editor does not load ` +
					`(${[...tierRefs].join(', ')}). The tier_powers entries are kept, but ` +
					`those power files are not, so copy them into the exported pack yourself.`
			);
		}
	}

	// ── layer id (from the layer-extension that lists this origin) ───────
	const fullOriginId = `${namespace}:${localId}`;
	const layerEntries = keysMatching(files, LAYER_RE, LAYER_COMPAT_RE);
	let matchedLayerPath: string | undefined;
	for (const { key, match } of layerEntries) {
		const ext = parseJson(files, key) as { origins?: unknown };
		if (Array.isArray(ext.origins) && ext.origins.includes(fullOriginId)) {
			matchedLayerPath = match[2];
			break;
		}
	}
	if (matchedLayerPath) {
		draft.layerId = `neoorigins:${matchedLayerPath}`;
	} else if (layerEntries.length > 0) {
		const guess = layerEntries[0].match[2];
		draft.layerId = `neoorigins:${guess}`;
		warnings.push(
			`No layer-extension file lists "${fullOriginId}"; assuming layer ` +
				`"neoorigins:${guess}" from ${layerEntries[0].key}.`
		);
	} else {
		warnings.push('No layer-extension file found; defaulting layer to "neoorigins:origin".');
	}

	return { draft, targetVersion, warnings };
}
