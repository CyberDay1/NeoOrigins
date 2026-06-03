// Round-trip test for `importMobDatapack` — the inverse of `exportMobDatapack`.
// Run with:
//
//   npm run check:mob
//
// (under the hood: `tsx src/lib/datapack/__tests__/mobImport.test.ts`).
//
// Framework-free — same shape as `check:import`. The core property under test
// is round-trip fidelity: a mob-origin draft exported and re-imported must
// reproduce the original draft (modulo the documented lossy spots), with no
// warnings for a clean editor-produced pack.

import { zipSync } from 'fflate';

import { exportMobDatapack } from '../mobExport.js';
import { importMobDatapack, MobImportError } from '../mobImport.js';
import type { MobOriginDraft } from '../../stores/mobOriginDraft.js';

let failed = 0;
let passed = 0;

function check(label: string, fn: () => void | Promise<void>): Promise<void> {
	return Promise.resolve()
		.then(fn)
		.then(() => {
			passed++;
			console.log(`  pass  ${label}`);
		})
		.catch((e: unknown) => {
			failed++;
			console.error(`  FAIL  ${label}`);
			console.error('         ' + (e instanceof Error ? e.message : String(e)));
		});
}

function assert(cond: unknown, msg: string): asserts cond {
	if (!cond) throw new Error(msg);
}

function deepEqual(a: unknown, b: unknown): boolean {
	if (a === b) return true;
	if (typeof a !== typeof b) return false;
	if (a === null || b === null) return a === b;
	if (Array.isArray(a) || Array.isArray(b)) {
		if (!Array.isArray(a) || !Array.isArray(b) || a.length !== b.length) return false;
		return a.every((x, i) => deepEqual(x, b[i]));
	}
	if (typeof a === 'object') {
		const ao = a as Record<string, unknown>;
		const bo = b as Record<string, unknown>;
		const ak = Object.keys(ao);
		const bk = Object.keys(bo);
		if (ak.length !== bk.length) return false;
		return ak.every((k) => k in bo && deepEqual(ao[k], bo[k]));
	}
	return false;
}

/** A minimal but valid draft: only the required identity + target. */
function minimalDraft(): MobOriginDraft {
	return {
		namespace: 'mypack',
		path: 'wraith',
		name: 'Wraith',
		description: '',
		icon: 'minecraft:zombie_spawn_egg',
		targetEntityType: 'minecraft:zombie',
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

/**
 * A fully-loaded draft where every set value is non-default so the serializer
 * emits it and import restores it. Drops use the weighted strategy (so `weight`
 * is the meaningful field; `chance`/`rolls` stay at their defaults that the
 * serializer omits and import re-defaults to — keeping the round-trip exact).
 */
function fullDraft(): MobOriginDraft {
	const d = minimalDraft();
	d.path = 'spectre';
	d.name = 'Spectre';
	d.description = 'A spectral undead.';
	d.icon = 'minecraft:wither_skeleton_skull';
	d.powers = [
		{
			id: 'spectre_kit',
			type: 'neoorigins:starting_equipment',
			fields: {
				grant_id: 'mypack:spectre_kit',
				item: 'minecraft:iron_sword',
				count: 1
			}
		}
	];
	d.spawnRulesEnabled = true;
	d.weight = 0.5;
	d.timeOfDay = 'night';
	d.mutexGroup = 'undead';
	d.replace = true;
	d.yRangeEnabled = true;
	d.yRangeMin = 0;
	d.yRangeMax = 64;
	d.lightRangeEnabled = true;
	d.lightRangeMin = 0;
	d.lightRangeMax = 7;
	d.spawnReasons = ['natural', 'spawner'];
	d.locationDimension = 'minecraft:overworld';
	d.locationBiome = 'minecraft:plains';
	d.locationBiomeTag = 'minecraft:is_forest';
	d.locationBiomes = ['minecraft:swamp', 'minecraft:dark_forest'];
	d.locationStructure = 'minecraft:village_plains';
	d.locationStructureTag = 'minecraft:village';
	d.locationAllowWaterSurface = true;
	d.locationAllowOceanFloor = true;
	d.locationMinYEnabled = true;
	d.locationMinY = 10;
	d.locationMaxYEnabled = true;
	d.locationMaxY = 100;
	d.locationCanSeeSky = 'false';
	d.dropsEnabled = true;
	d.dropMode = 'replace';
	d.dropStrategy = 'weighted_pool';
	d.dropPoolRolls = 3;
	d.dropEntries = [{ item: 'minecraft:bone', countMin: 1, countMax: 3, chance: 1.0, rolls: 1, weight: 5 }];
	return d;
}

async function exportBytes(d: MobOriginDraft, packFormat?: number): Promise<Uint8Array> {
	const blob = await exportMobDatapack(d, packFormat);
	return new Uint8Array(await blob.arrayBuffer());
}

console.log('importMobDatapack');

await check('round-trips a minimal draft exactly, with no warnings', async () => {
	const original = minimalDraft();
	const res = importMobDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(
		deepEqual(res.draft, original),
		`draft mismatch.\n  expected: ${JSON.stringify(original)}\n  got:      ${JSON.stringify(res.draft)}`
	);
});

await check('round-trips a fully-loaded draft exactly, with no warnings', async () => {
	const original = fullDraft();
	const res = importMobDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(
		deepEqual(res.draft, original),
		`draft mismatch.\n  expected: ${JSON.stringify(original)}\n  got:      ${JSON.stringify(res.draft)}`
	);
});

await check('round-trips an entity-tag target', async () => {
	const original = minimalDraft();
	original.targetEntityType = '';
	original.targetEntityTag = 'minecraft:undead';
	const res = importMobDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(deepEqual(res.draft, original), `tag target mismatch: ${JSON.stringify(res.draft)}`);
});

await check('round-trips a multi-type (entity_types) target', async () => {
	const original = minimalDraft();
	original.targetEntityType = '';
	original.targetEntityTypes = ['minecraft:zombie', 'minecraft:husk'];
	const res = importMobDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(deepEqual(res.draft, original), `multi target mismatch: ${JSON.stringify(res.draft)}`);
});

await check('infers target version 1.21.1 from pack_format 48', async () => {
	const res = importMobDatapack(await exportBytes(minimalDraft(), 48));
	assert(res.targetVersion === '1.21.1', `got ${res.targetVersion}`);
});

await check('infers target version 26.1 from pack_format 84', async () => {
	const res = importMobDatapack(await exportBytes(minimalDraft(), 84));
	assert(res.targetVersion === '26.1', `got ${res.targetVersion}`);
});

await check('warns when component-form text actually loses data', async () => {
	// Hand-build a pack whose name uses a translate key (lossy → flattened).
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } })),
		'data/mypack/origins/mob_origins/wraith.json': enc.encode(
			JSON.stringify({
				name: { translate: 'mob.wraith.name', fallback: 'Wraith' },
				description: { text: '' },
				icon: 'minecraft:zombie_spawn_egg',
				target: { entity_type: 'minecraft:zombie' },
				powers: []
			})
		)
	});
	const res = importMobDatapack(zip);
	assert(res.draft.name === 'Wraith', `expected fallback flatten, got ${res.draft.name}`);
	assert(
		res.warnings.some((w) => w.toLowerCase().includes('flattened')),
		`expected a flatten warning, got: ${res.warnings.join(' | ')}`
	);
});

await check('throws MobImportError when no mob-origin file is present', () => {
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } }))
	});
	let threw = false;
	try {
		importMobDatapack(zip);
	} catch (e) {
		threw = e instanceof MobImportError;
	}
	assert(threw, 'expected MobImportError when mob-origin file is missing');
});

await check('throws MobImportError on non-zip bytes', () => {
	let threw = false;
	try {
		importMobDatapack(new Uint8Array([1, 2, 3, 4, 5]));
	} catch (e) {
		threw = e instanceof MobImportError;
	}
	assert(threw, 'expected MobImportError on garbage bytes');
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) {
	process.exit(1);
}
