// Mob-origin datapack `.zip` import — inverse of `exportMobDatapack`.
//
// Reads a mob-origin datapack zip (produced by this editor or a compatible
// hand-authored pack) back into a `MobOriginDraft`, so a user can round-trip:
// export, tweak, re-open. Best-effort and lossy in the same places the
// serializer is lossy: component-form text is flattened, and an unknown
// `can_see_sky`/enum value is reported via `warnings` rather than thrown.
//
// Fatal problems (not a zip, or no mob-origin file) throw `MobImportError`.

import { unzipSync, strFromU8 } from 'fflate';

import type { MobOriginDraft, DropRow, TargetMcVersion } from '$lib/stores/mobOriginDraft';

/** Thrown when the zip can't be parsed into a draft at all. */
export class MobImportError extends Error {
	constructor(message: string) {
		super(message);
		this.name = 'MobImportError';
	}
}

export interface MobImportResult {
	draft: MobOriginDraft;
	targetVersion: TargetMcVersion;
	warnings: string[];
}

// Baseline defaults — must mirror `createMobDraft()` in mobOriginDraft.ts.
// Kept inline so this module stays a pure transform with no `$app` dependency
// (loadable from the tsx test runner).
function blankDraft(): MobOriginDraft {
	return {
		namespace: 'neoorigins',
		path: '',
		name: '',
		description: '',
		icon: 'minecraft:zombie_spawn_egg',
		targetEntityType: '',
		targetEntityTag: '',
		targetEntityTypes: [],
		powers: [],
		spawnRulesEnabled: false,
		weight: 0.25,
		timeOfDay: 'any',
		mutexGroup: '',
		replace: false,
		yRangeEnabled: false,
		yRangeMin: -64,
		yRangeMax: 320,
		lightRangeEnabled: false,
		lightRangeMin: 0,
		lightRangeMax: 15,
		spawnReasons: [],
		locationDimension: '',
		locationBiome: '',
		locationBiomeTag: '',
		locationBiomes: [],
		locationStructure: '',
		locationStructureTag: '',
		locationAllowWaterSurface: false,
		locationAllowOceanFloor: false,
		locationMinYEnabled: false,
		locationMinY: -64,
		locationMaxYEnabled: false,
		locationMaxY: 320,
		locationCanSeeSky: 'any',
		dropsEnabled: false,
		dropMode: 'additive',
		dropStrategy: 'independent_chance',
		dropPoolRolls: 1,
		dropEntries: []
	};
}

const MOB_ORIGIN_RE = /^data\/([^/]+)\/origins\/mob_origins\/(.+)\.json$/;

/**
 * Flatten a `name`/`description` value into a plain string. `flattened` is
 * true only when information was actually dropped — a bare string or the
 * canonical editor form `{ "text": "..." }` (text the sole key) round-trips
 * losslessly, so re-importing an editor-authored pack warns about nothing.
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
		throw new MobImportError(
			`Failed to parse ${key} as JSON: ${e instanceof Error ? e.message : String(e)}`
		);
	}
}

function asString(v: unknown): string | undefined {
	return typeof v === 'string' ? v : undefined;
}

/** Decode an IntRange (bare number or {min,max}) into [min, max]. */
function readIntRange(v: unknown, fallback: [number, number]): [number, number] {
	if (typeof v === 'number') return [v, v];
	if (v && typeof v === 'object') {
		const o = v as Record<string, unknown>;
		const min = typeof o.min === 'number' ? o.min : fallback[0];
		const max = typeof o.max === 'number' ? o.max : fallback[1];
		return [min, max];
	}
	return fallback;
}

/**
 * Decompress a mob-origin datapack `.zip` and reconstruct a `MobOriginDraft`.
 *
 * @throws {MobImportError} if the bytes aren't a readable zip or contain no
 *   `data/<ns>/origins/mob_origins/<id>.json` file.
 */
export function importMobDatapack(bytes: Uint8Array): MobImportResult {
	const warnings: string[] = [];

	let files: Record<string, Uint8Array>;
	try {
		files = unzipSync(bytes);
	} catch (e) {
		throw new MobImportError(
			`Not a readable .zip: ${e instanceof Error ? e.message : String(e)}`
		);
	}

	// ── locate the mob-origin file ────────────────────────────────────────
	const keys = Object.keys(files).filter((k) => MOB_ORIGIN_RE.test(k));
	if (keys.length === 0) {
		throw new MobImportError(
			'No mob-origin file found (expected data/<namespace>/origins/mob_origins/<id>.json).'
		);
	}
	if (keys.length > 1) {
		warnings.push(
			`Datapack defines ${keys.length} mob origins; importing the first (${keys[0]}). ` +
				`The editor edits one at a time.`
		);
	}
	const key = keys[0];
	const m = key.match(MOB_ORIGIN_RE)!;
	const namespace = m[1];
	const localId = m[2];

	// ── target version from pack.mcmeta ───────────────────────────────────
	let targetVersion: TargetMcVersion = '1.21.1';
	if ('pack.mcmeta' in files) {
		const meta = parseJson(files, 'pack.mcmeta') as { pack?: { pack_format?: unknown } } | undefined;
		const fmt = meta?.pack?.pack_format;
		if (fmt === 84) targetVersion = '26.1';
		else if (fmt === 48) targetVersion = '1.21.1';
		else warnings.push(`Unrecognized pack_format ${JSON.stringify(fmt)}; defaulting to MC 1.21.1.`);
	} else {
		warnings.push('No pack.mcmeta found; defaulting target to MC 1.21.1.');
	}

	// ── body ──────────────────────────────────────────────────────────────
	const json = parseJson(files, key) as Record<string, unknown>;
	const draft = blankDraft();
	draft.namespace = namespace;
	draft.path = localId;

	const name = flattenText(json.name);
	const description = flattenText(json.description);
	draft.name = name.value;
	draft.description = description.value;
	if (name.flattened || description.flattened) {
		warnings.push(
			'Component-form text was flattened to a plain string; any translate key, ' +
				'fallback or styling siblings were dropped.'
		);
	}

	if (typeof json.icon === 'string') draft.icon = json.icon;

	// ── target ────────────────────────────────────────────────────────────
	if (json.target && typeof json.target === 'object') {
		const t = json.target as Record<string, unknown>;
		if (Array.isArray(t.entity_types)) {
			draft.targetEntityTypes = t.entity_types.filter((x): x is string => typeof x === 'string');
		} else if (typeof t.entity_tag === 'string') {
			draft.targetEntityTag = t.entity_tag;
		} else if (typeof t.entity_type === 'string') {
			draft.targetEntityType = t.entity_type;
		} else {
			warnings.push('Mob origin has no usable target (entity_type/entity_tag/entity_types).');
		}
	} else {
		warnings.push('Mob origin has no target block.');
	}

	// ── spawn rules ───────────────────────────────────────────────────────
	if (json.spawn_rules && typeof json.spawn_rules === 'object') {
		const s = json.spawn_rules as Record<string, unknown>;
		draft.spawnRulesEnabled = true;
		if (typeof s.weight === 'number') draft.weight = s.weight;
		const tod = asString(s.time_of_day);
		if (tod === 'day' || tod === 'night' || tod === 'any') draft.timeOfDay = tod;
		if (Array.isArray(s.spawn_reasons)) {
			draft.spawnReasons = s.spawn_reasons.filter((x): x is string => typeof x === 'string');
		}
		if (typeof s.mutex_group === 'string') draft.mutexGroup = s.mutex_group;
		draft.replace = s.replace === true;
		if (s.y_range !== undefined) {
			draft.yRangeEnabled = true;
			[draft.yRangeMin, draft.yRangeMax] = readIntRange(s.y_range, [draft.yRangeMin, draft.yRangeMax]);
		}
		if (s.light_range !== undefined) {
			draft.lightRangeEnabled = true;
			[draft.lightRangeMin, draft.lightRangeMax] = readIntRange(s.light_range, [
				draft.lightRangeMin,
				draft.lightRangeMax
			]);
		}
		if (s.location && typeof s.location === 'object') {
			const l = s.location as Record<string, unknown>;
			if (typeof l.dimension === 'string') draft.locationDimension = l.dimension;
			if (typeof l.biome === 'string') draft.locationBiome = l.biome;
			if (typeof l.biome_tag === 'string') draft.locationBiomeTag = l.biome_tag;
			if (Array.isArray(l.biomes)) {
				draft.locationBiomes = l.biomes.filter((x): x is string => typeof x === 'string');
			}
			if (typeof l.structure === 'string') draft.locationStructure = l.structure;
			if (typeof l.structure_tag === 'string') draft.locationStructureTag = l.structure_tag;
			draft.locationAllowWaterSurface = l.allow_water_surface === true;
			draft.locationAllowOceanFloor = l.allow_ocean_floor === true;
			if (typeof l.min_y === 'number') {
				draft.locationMinYEnabled = true;
				draft.locationMinY = l.min_y;
			}
			if (typeof l.max_y === 'number') {
				draft.locationMaxYEnabled = true;
				draft.locationMaxY = l.max_y;
			}
			if (typeof l.can_see_sky === 'boolean') {
				draft.locationCanSeeSky = l.can_see_sky ? 'true' : 'false';
			}
		}
	}

	// ── drops ─────────────────────────────────────────────────────────────
	if (json.drops && typeof json.drops === 'object') {
		const dr = json.drops as Record<string, unknown>;
		draft.dropsEnabled = true;
		if (dr.mode === 'replace') draft.dropMode = 'replace';
		if (dr.strategy === 'weighted_pool') draft.dropStrategy = 'weighted_pool';
		if (typeof dr.pool_rolls === 'number') draft.dropPoolRolls = dr.pool_rolls;
		if (Array.isArray(dr.entries)) {
			const rows: DropRow[] = [];
			for (const raw of dr.entries) {
				if (!raw || typeof raw !== 'object') continue;
				const e = raw as Record<string, unknown>;
				const item = typeof e.item === 'string' ? e.item : '';
				if (!item) {
					warnings.push('Skipped a drop entry with no item.');
					continue;
				}
				const [cMin, cMax] = readIntRange(e.count, [1, 1]);
				rows.push({
					item,
					countMin: cMin,
					countMax: cMax,
					chance: typeof e.chance === 'number' ? e.chance : 1.0,
					rolls: typeof e.rolls === 'number' ? e.rolls : 1,
					weight: typeof e.weight === 'number' ? e.weight : 1
				});
			}
			draft.dropEntries = rows;
		}
	}

	// ── powers ────────────────────────────────────────────────────────────
	const powerRefs = Array.isArray(json.powers) ? json.powers : [];
	for (const ref of powerRefs) {
		if (typeof ref !== 'string' || !ref.includes(':')) {
			warnings.push(`Skipped malformed power reference ${JSON.stringify(ref)}.`);
			continue;
		}
		const colon = ref.indexOf(':');
		const powerNs = ref.slice(0, colon);
		const powerLocalId = ref.slice(colon + 1);
		const powerKey = `data/${powerNs}/origins/powers/${powerLocalId}.json`;
		if (!(powerKey in files)) {
			warnings.push(
				`Power "${ref}" has no power file in the datapack — skipped ` +
					`(likely a built-in or external power the editor can't reconstruct).`
			);
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
				`Power "${ref}" lives in a different namespace than the mob origin; ` +
					`it will be re-exported under "${namespace}:${powerLocalId}".`
			);
		}
		const fields: Record<string, unknown> = {};
		for (const [k, v] of Object.entries(powerJson)) {
			if (k === 'type') continue;
			fields[k] = v;
		}
		draft.powers.push({ id: powerLocalId, type, fields });
	}

	return { draft, targetVersion, warnings };
}
