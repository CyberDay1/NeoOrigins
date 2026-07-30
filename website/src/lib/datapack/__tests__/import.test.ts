// Round-trip test for `importDatapack` — the inverse of `exportDatapack`.
// Run with:
//
//   npm run check:import
//
// (under the hood: `tsx src/lib/datapack/__tests__/import.test.ts`).
//
// Framework-free — same shape as `check:export`. The core property under
// test is round-trip fidelity: a draft exported and then re-imported must
// reproduce the original draft (modulo the documented lossy spots), with
// no warnings for a clean editor-produced pack.

import { zipSync } from 'fflate';

import { exportDatapack } from '../export.js';
import { importDatapack, ImportError } from '../import.js';
import type { OriginDraft } from '../../stores/originDraft.js';

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

/** Order-independent structural equality (objects compared by key set). */
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

function makeDraft(): OriginDraft {
	return {
		namespace: 'mypack',
		path: 'wizard',
		layerId: 'neoorigins:origin',
		name: 'Wizard',
		description: 'A spellcaster of arcane power.',
		icon: 'minecraft:enchanted_book',
		impact: 'medium',
		order: 5,
		unchoosable: false,
		hidden: false,
		powers: [
			{
				id: 'starter_robes',
				type: 'neoorigins:starting_equipment',
				fields: {
					grant_id: 'mypack:wizard_starter_robes',
					item: 'minecraft:leather_chestplate',
					count: 1
				}
			}
		]
	};
}

async function exportBytes(d: OriginDraft, packFormat?: number): Promise<Uint8Array> {
	const blob = await exportDatapack(d, packFormat);
	return new Uint8Array(await blob.arrayBuffer());
}

console.log('importDatapack');

await check('round-trips an origin-layer draft exactly, with no warnings', async () => {
	const original = makeDraft();
	const res = importDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(
		deepEqual(res.draft, original),
		`draft mismatch.\n  expected: ${JSON.stringify(original)}\n  got:      ${JSON.stringify(res.draft)}`
	);
});

await check('infers target version 1.21.1 from pack_format 48', async () => {
	const res = importDatapack(await exportBytes(makeDraft(), 48));
	assert(res.targetVersion === '1.21.1', `got ${res.targetVersion}`);
});

await check('infers target version 26.1 from pack_format 84', async () => {
	const res = importDatapack(await exportBytes(makeDraft(), 84));
	assert(res.targetVersion === '26.1', `got ${res.targetVersion}`);
});

await check('round-trips a class-layer draft (layerId reconstructed)', async () => {
	const original = makeDraft();
	original.path = 'archmage';
	original.layerId = 'neoorigins:class';
	original.name = 'Archmage';
	const res = importDatapack(await exportBytes(original));
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
	assert(res.draft.layerId === 'neoorigins:class', `got layerId ${res.draft.layerId}`);
	assert(deepEqual(res.draft, original), `class draft mismatch: ${JSON.stringify(res.draft)}`);
});

await check('round-trips upgrades, omitting absent announcement', async () => {
	const original = makeDraft();
	original.upgrades = [
		{ advancement: 'mypack:wizard/tier_1', origin: 'mypack:archmage', announcement: 'Ascended!' },
		{ advancement: 'mypack:wizard/tier_2', origin: 'mypack:lich' }
	];
	const res = importDatapack(await exportBytes(original));
	assert(res.draft.upgrades?.length === 2, `expected 2 upgrades, got ${res.draft.upgrades?.length}`);
	assert(deepEqual(res.draft.upgrades, original.upgrades), `upgrades mismatch: ${JSON.stringify(res.draft.upgrades)}`);
});

await check('warns and skips a power reference with no power file', () => {
	// Hand-build a pack whose origin references a power that isn't shipped.
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } })),
		'data/mypack/origins/origins/wizard.json': enc.encode(
			JSON.stringify({ powers: ['mypack:present', 'minecraft:built_in'] })
		),
		'data/mypack/origins/powers/present.json': enc.encode(
			JSON.stringify({ type: 'neoorigins:flight' })
		),
		'data/mypack/origins/origin_layers/origin.json': enc.encode(
			JSON.stringify({ replace: false, origins: ['mypack:wizard'] })
		)
	});
	const res = importDatapack(zip);
	assert(res.draft.powers.length === 1, `expected 1 resolved power, got ${res.draft.powers.length}`);
	assert(res.draft.powers[0].id === 'present', `got ${res.draft.powers[0].id}`);
	assert(
		res.warnings.some((w) => w.includes('minecraft:built_in')),
		`expected a warning about the missing power, got: ${res.warnings.join(' | ')}`
	);
});

await check('reads single-key { text } components losslessly, with no warning', () => {
	// The shape NeoOrigins' own shipped origins use (asura, golden_body,
	// iron_monk, qi_cultivator, sword_immortal, windwalker). Before `text`
	// was handled this silently produced an empty name AND description
	// with an empty `warnings` array — the pack looked like it imported fine.
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } })),
		'data/neoorigins/origins/origins/asura.json': enc.encode(
			JSON.stringify({
				name: { text: 'Asura' },
				description: { text: 'A wrathful demigod of the battlefield.' },
				icon: 'minecraft:netherite_sword',
				impact: 'high',
				powers: []
			})
		),
		'data/neoorigins/origins/origin_layers/origin.json': enc.encode(
			JSON.stringify({ replace: false, origins: ['neoorigins:asura'] })
		)
	});
	const res = importDatapack(zip);
	assert(res.draft.name === 'Asura', `expected "Asura", got ${JSON.stringify(res.draft.name)}`);
	assert(
		res.draft.description === 'A wrathful demigod of the battlefield.',
		`expected the description text, got ${JSON.stringify(res.draft.description)}`
	);
	assert(res.warnings.length === 0, `unexpected warnings: ${res.warnings.join(' | ')}`);
});

await check('warns when component-form text actually loses data', () => {
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } })),
		'data/mypack/origins/origins/wizard.json': enc.encode(
			JSON.stringify({
				name: { translate: 'origin.mypack.wizard.name', fallback: 'Wizard' },
				description: { text: 'Styled', color: 'blue' },
				powers: []
			})
		),
		'data/mypack/origins/origin_layers/origin.json': enc.encode(
			JSON.stringify({ replace: false, origins: ['mypack:wizard'] })
		)
	});
	const res = importDatapack(zip);
	assert(res.draft.name === 'Wizard', `expected fallback flatten, got ${res.draft.name}`);
	assert(res.draft.description === 'Styled', `expected "Styled", got ${res.draft.description}`);
	assert(
		res.warnings.some((w) => w.toLowerCase().includes('flattened')),
		`expected a flatten warning, got: ${res.warnings.join(' | ')}`
	);
});

await check('throws ImportError when no origin file is present', () => {
	const enc = new TextEncoder();
	const zip = zipSync({
		'pack.mcmeta': enc.encode(JSON.stringify({ pack: { pack_format: 48 } }))
	});
	let threw = false;
	try {
		importDatapack(zip);
	} catch (e) {
		threw = e instanceof ImportError;
	}
	assert(threw, 'expected ImportError when origin file is missing');
});

await check('throws ImportError on non-zip bytes', () => {
	let threw = false;
	try {
		importDatapack(new Uint8Array([1, 2, 3, 4, 5]));
	} catch (e) {
		threw = e instanceof ImportError;
	}
	assert(threw, 'expected ImportError on garbage bytes');
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) {
	process.exit(1);
}
