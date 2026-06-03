// Mob-origin draft → datapack-shape JSON serializer.
//
// Lowers the editor's in-memory `MobOriginDraft` into the wire-format JSON
// the mod loads from `data/<ns>/origins/mob_origins/<name>.json`, matching
// the mod's `MobCustomPackSerializer` byte-for-byte (so a pack authored in
// the web editor and one authored in-game are indistinguishable):
//
//   - `id` is OMITTED — MobOriginDataManager injects it from the path.
//   - `name` / `description` are emitted as component objects (`{text: ...}`)
//     always (even when empty), exactly as the in-game serializer does.
//   - `icon` is the item-id string, always emitted.
//   - `target` is always emitted; an unconfigured target is `{}` (the JSON
//     preview will then flag a schema error, prompting the user to set one).
//   - `spawn_rules` only when enabled; `drops` only when enabled AND at least
//     one entry row exists.
//   - power bodies reuse the player serializer's `serializePower` (the mod
//     delegates mob power shaping to the same `CustomPackSerializer.powerJson`).

import type { MobOriginDraft } from '$lib/stores/mobOriginDraft';
import { serializePower, type SerializedPowerEntry } from '$lib/schema/originSerializer';

// ── public output shape ──────────────────────────────────────────────────

export interface SerializedTarget {
	entity_type?: string;
	entity_tag?: string;
	entity_types?: string[];
}

export interface SerializedIntRange {
	min: number;
	max: number;
}

export interface SerializedLocation {
	dimension?: string;
	biome?: string;
	biome_tag?: string;
	biomes?: string[];
	structure?: string;
	structure_tag?: string;
	allow_water_surface?: boolean;
	allow_ocean_floor?: boolean;
	min_y?: number;
	max_y?: number;
	can_see_sky?: boolean;
}

export interface SerializedSpawnRules {
	weight: number;
	time_of_day?: string;
	spawn_reasons?: string[];
	mutex_group?: string;
	replace?: boolean;
	y_range?: SerializedIntRange;
	light_range?: SerializedIntRange;
	location?: SerializedLocation;
}

export interface SerializedDropEntry {
	item: string;
	count: number | SerializedIntRange;
	chance?: number;
	rolls?: number;
	weight?: number;
}

export interface SerializedDrops {
	mode?: string;
	strategy?: string;
	pool_rolls?: number;
	entries: SerializedDropEntry[];
}

export interface SerializedMobOrigin {
	name: { text: string };
	description: { text: string };
	icon: string;
	target: SerializedTarget;
	powers: string[];
	spawn_rules?: SerializedSpawnRules;
	drops?: SerializedDrops;
}

export interface SerializedMobOriginBundle {
	namespace: string;
	localId: string;
	mobOrigin: SerializedMobOrigin;
	/** `data/<namespace>/origins/mob_origins/<localId>.json`. */
	mobOriginPath: string;
	powers: SerializedPowerEntry[];
}

// ── implementation ─────────────────────────────────────────────────────────

function targetJson(d: MobOriginDraft): SerializedTarget {
	const t: SerializedTarget = {};
	const types = d.targetEntityTypes.map((s) => s.trim()).filter((s) => s !== '');
	if (types.length > 0) {
		t.entity_types = types;
	} else if (d.targetEntityTag && d.targetEntityTag.trim() !== '') {
		t.entity_tag = d.targetEntityTag.trim();
	} else if (d.targetEntityType && d.targetEntityType.trim() !== '') {
		t.entity_type = d.targetEntityType.trim();
	}
	return t;
}

/** Returns `undefined` when no location sub-field is set (codec → empty). */
function locationJson(d: MobOriginDraft): SerializedLocation | undefined {
	const l: SerializedLocation = {};
	let any = false;
	if (d.locationDimension.trim() !== '') {
		l.dimension = d.locationDimension.trim();
		any = true;
	}
	if (d.locationBiome.trim() !== '') {
		l.biome = d.locationBiome.trim();
		any = true;
	}
	if (d.locationBiomeTag.trim() !== '') {
		l.biome_tag = d.locationBiomeTag.trim();
		any = true;
	}
	const biomes = d.locationBiomes.map((b) => b.trim()).filter((b) => b !== '');
	if (biomes.length > 0) {
		l.biomes = biomes;
		any = true;
	}
	if (d.locationStructure.trim() !== '') {
		l.structure = d.locationStructure.trim();
		any = true;
	}
	if (d.locationStructureTag.trim() !== '') {
		l.structure_tag = d.locationStructureTag.trim();
		any = true;
	}
	if (d.locationAllowWaterSurface) {
		l.allow_water_surface = true;
		any = true;
	}
	if (d.locationAllowOceanFloor) {
		l.allow_ocean_floor = true;
		any = true;
	}
	if (d.locationMinYEnabled) {
		l.min_y = d.locationMinY;
		any = true;
	}
	if (d.locationMaxYEnabled) {
		l.max_y = d.locationMaxY;
		any = true;
	}
	if (d.locationCanSeeSky !== 'any') {
		l.can_see_sky = d.locationCanSeeSky === 'true';
		any = true;
	}
	return any ? l : undefined;
}

function spawnRulesJson(d: MobOriginDraft): SerializedSpawnRules {
	const s: SerializedSpawnRules = { weight: d.weight };
	if (d.timeOfDay !== 'any') s.time_of_day = d.timeOfDay;
	if (d.spawnReasons.length > 0) s.spawn_reasons = [...d.spawnReasons];
	if (d.mutexGroup && d.mutexGroup.trim() !== '') s.mutex_group = d.mutexGroup.trim();
	if (d.replace) s.replace = true;
	if (d.yRangeEnabled) s.y_range = { min: d.yRangeMin, max: d.yRangeMax };
	if (d.lightRangeEnabled) s.light_range = { min: d.lightRangeMin, max: d.lightRangeMax };
	const loc = locationJson(d);
	if (loc) s.location = loc;
	return s;
}

function dropsJson(d: MobOriginDraft): SerializedDrops {
	const drops: SerializedDrops = { entries: [] };
	if (d.dropMode !== 'additive') drops.mode = d.dropMode;
	const weighted = d.dropStrategy === 'weighted_pool';
	if (weighted) {
		drops.strategy = 'weighted_pool';
		drops.pool_rolls = Math.max(0, d.dropPoolRolls);
	}
	for (const r of d.dropEntries) {
		if (!r.item || r.item.trim() === '') continue;
		const e: SerializedDropEntry = {
			item: r.item.trim(),
			// {min,max} collapses to a bare number when equal.
			count:
				r.countMin === r.countMax
					? r.countMin
					: { min: Math.min(r.countMin, r.countMax), max: Math.max(r.countMin, r.countMax) }
		};
		if (weighted) {
			if (r.weight !== 1) e.weight = r.weight;
		} else {
			if (r.chance !== 1.0) e.chance = r.chance;
			if (r.rolls !== 1) e.rolls = r.rolls;
		}
		drops.entries.push(e);
	}
	return drops;
}

/**
 * Lower a `MobOriginDraft` into datapack JSON. See module docstring for the
 * parity decisions.
 */
export function serializeMobOrigin(draft: MobOriginDraft): SerializedMobOriginBundle {
	const namespace = draft.namespace;
	const localId = draft.path;

	const powers = draft.powers.map((p) => serializePower(p, namespace));

	const mobOrigin: SerializedMobOrigin = {
		name: { text: draft.name ?? '' },
		description: { text: draft.description ?? '' },
		icon: draft.icon,
		target: targetJson(draft),
		powers: powers.map((p) => p.fullId)
	};
	if (draft.spawnRulesEnabled) mobOrigin.spawn_rules = spawnRulesJson(draft);
	if (draft.dropsEnabled && draft.dropEntries.length > 0) mobOrigin.drops = dropsJson(draft);

	return {
		namespace,
		localId,
		mobOrigin,
		mobOriginPath: `data/${namespace}/origins/mob_origins/${localId}.json`,
		powers
	};
}
