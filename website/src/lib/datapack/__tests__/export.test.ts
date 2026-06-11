// Round-trip test for `exportDatapack`. Run with:
//
//   npm run check:export
//
// (under the hood: `tsx src/lib/datapack/__tests__/export.test.ts`).
//
// Framework-free — same shape as `check:schema`. The test builds a
// representative draft, exports the zip, decompresses it back with
// `fflate.unzipSync`, and asserts each expected entry lives where the
// scope doc says it should.

import { unzipSync } from 'fflate';

import {
	exportDatapack,
	suggestedFilename,
	validateDraftIds,
	ExportValidationError
} from '../export.js';
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

function makeClassDraft(): OriginDraft {
	const d = makeDraft();
	d.namespace = 'mypack';
	d.path = 'archmage';
	d.layerId = 'neoorigins:class';
	d.name = 'Archmage';
	return d;
}

console.log('exportDatapack');

// We run the checks sequentially to keep output ordered.
await check('exports non-empty Blob with application/zip MIME', async () => {
	const blob = await exportDatapack(makeDraft());
	assert(blob instanceof Blob, 'expected a Blob');
	assert(blob.size > 0, 'expected non-empty Blob');
	assert(
		blob.type === 'application/zip',
		`expected application/zip, got ${blob.type}`
	);
});

await check('zip contains pack.mcmeta at root with correct pack_format', async () => {
	const blob = await exportDatapack(makeDraft());
	const bytes = new Uint8Array(await blob.arrayBuffer());
	const files = unzipSync(bytes);
	assert('pack.mcmeta' in files, 'pack.mcmeta missing from zip root');
	const meta = JSON.parse(new TextDecoder().decode(files['pack.mcmeta']));
	assert(
		meta?.pack?.pack_format === 48,
		`expected pack_format 48, got ${meta?.pack?.pack_format}`
	);
	assert(
		typeof meta?.pack?.description === 'string' &&
			meta.pack.description.includes('NeoOrigins Web Editor'),
		`description missing editor attribution: ${meta?.pack?.description}`
	);
});

await check('origin JSON lives at data/mypack/origins/origins/wizard.json', async () => {
	const blob = await exportDatapack(makeDraft());
	const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
	const path = 'data/mypack/origins/origins/wizard.json';
	assert(path in files, `${path} missing from zip`);
	const origin = JSON.parse(new TextDecoder().decode(files[path]));
	assert(origin.name === 'Wizard', `expected origin.name "Wizard", got ${origin.name}`);
	assert(origin.impact === 'medium', `expected impact "medium", got ${origin.impact}`);
	assert(
		Array.isArray(origin.powers) && origin.powers[0] === 'mypack:starter_robes',
		`expected origin.powers[0] = "mypack:starter_robes", got ${JSON.stringify(origin.powers)}`
	);
});

await check('power JSON lives at data/mypack/origins/powers/starter_robes.json', async () => {
	const blob = await exportDatapack(makeDraft());
	const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
	const path = 'data/mypack/origins/powers/starter_robes.json';
	assert(path in files, `${path} missing from zip`);
	const power = JSON.parse(new TextDecoder().decode(files[path]));
	assert(
		power.type === 'neoorigins:starting_equipment',
		`expected type "neoorigins:starting_equipment", got ${power.type}`
	);
	assert(
		power.grant_id === 'mypack:wizard_starter_robes',
		`expected grant_id passthrough, got ${power.grant_id}`
	);
	assert(power.count === 1, `expected count 1, got ${power.count}`);
});

await check('zip contains exactly the expected 4 files (origin-layer draft)', async () => {
	const blob = await exportDatapack(makeDraft());
	const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
	const keys = Object.keys(files).sort();
	const expected = [
		'data/mypack/origins/origin_layers/origin.json',
		'data/mypack/origins/origins/wizard.json',
		'data/mypack/origins/powers/starter_robes.json',
		'pack.mcmeta'
	].sort();
	assert(
		keys.length === expected.length &&
			keys.every((k, i) => k === expected[i]),
		`zip contents mismatch.\n  expected: ${expected.join(', ')}\n  got:      ${keys.join(', ')}`
	);
});

await check(
	'layer-extension at data/mypack/origins/origin_layers/origin.json (origin draft)',
	async () => {
		const blob = await exportDatapack(makeDraft());
		const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
		const path = 'data/mypack/origins/origin_layers/origin.json';
		assert(path in files, `${path} missing from zip`);
		const ext = JSON.parse(new TextDecoder().decode(files[path]));
		assert(ext.replace === false, `expected replace=false, got ${ext.replace}`);
		assert(
			Array.isArray(ext.origins) && ext.origins[0] === 'mypack:wizard',
			`expected origins[0]="mypack:wizard", got ${JSON.stringify(ext.origins)}`
		);
	}
);

await check(
	'class-type draft produces the four expected zip entries',
	async () => {
		const blob = await exportDatapack(makeClassDraft());
		const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
		const keys = Object.keys(files).sort();
		const expected = [
			'data/mypack/origins/origin_layers/class.json',
			'data/mypack/origins/origins/archmage.json',
			'data/mypack/origins/powers/starter_robes.json',
			'pack.mcmeta'
		].sort();
		assert(
			keys.length === expected.length &&
				keys.every((k, i) => k === expected[i]),
			`zip contents mismatch.\n  expected: ${expected.join(', ')}\n  got:      ${keys.join(', ')}`
		);
		const ext = JSON.parse(
			new TextDecoder().decode(files['data/mypack/origins/origin_layers/class.json'])
		);
		assert(ext.replace === false, `expected replace=false on class draft, got ${ext.replace}`);
		assert(
			Array.isArray(ext.origins) && ext.origins[0] === 'mypack:archmage',
			`expected class-layer origins[0]="mypack:archmage", got ${JSON.stringify(ext.origins)}`
		);
	}
);

await check(
	'upgrades emitted only when non-empty, with announcement passthrough',
	async () => {
		const d = makeDraft();
		d.upgrades = [
			{
				advancement: 'mypack:wizard/tier_1',
				origin: 'mypack:archmage',
				announcement: 'Ascended!'
			},
			{
				advancement: 'mypack:wizard/tier_2',
				origin: 'mypack:lich'
			}
		];
		const blob = await exportDatapack(d);
		const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
		const origin = JSON.parse(
			new TextDecoder().decode(files['data/mypack/origins/origins/wizard.json'])
		);
		assert(
			Array.isArray(origin.upgrades) && origin.upgrades.length === 2,
			`expected 2 upgrades, got ${JSON.stringify(origin.upgrades)}`
		);
		assert(
			origin.upgrades[0].announcement === 'Ascended!',
			`expected announcement passthrough, got ${origin.upgrades[0].announcement}`
		);
		assert(
			!('announcement' in origin.upgrades[1]),
			`expected announcement omitted on entry 2, got ${JSON.stringify(origin.upgrades[1])}`
		);

		// And confirm an empty/undefined upgrades does NOT emit the field.
		const blob2 = await exportDatapack(makeDraft());
		const files2 = unzipSync(new Uint8Array(await blob2.arrayBuffer()));
		const origin2 = JSON.parse(
			new TextDecoder().decode(files2['data/mypack/origins/origins/wizard.json'])
		);
		assert(
			!('upgrades' in origin2),
			`expected no upgrades on default draft, got ${JSON.stringify(origin2.upgrades)}`
		);
	}
);

// ── export validation gate ─────────────────────────────────────────────────

async function expectExportBlocked(
	d: OriginDraft,
	expectFragment: string
): Promise<ExportValidationError> {
	try {
		await exportDatapack(d);
	} catch (e) {
		assert(
			e instanceof ExportValidationError,
			`expected ExportValidationError, got ${e instanceof Error ? e.name : typeof e}`
		);
		assert(
			e.issues.some((m) => m.includes(expectFragment)),
			`expected an issue mentioning "${expectFragment}", got: ${e.issues.join(' | ')}`
		);
		return e;
	}
	throw new Error('expected exportDatapack to throw, but it produced a zip');
}

await check('gate — blank origin path refuses export', async () => {
	const d = makeDraft();
	d.path = '   ';
	await expectExportBlocked(d, 'Origin path');
});

await check('gate — blank namespace refuses export', async () => {
	const d = makeDraft();
	d.namespace = '';
	await expectExportBlocked(d, 'namespace');
});

await check('gate — blank power id refuses export', async () => {
	const d = makeDraft();
	d.powers.push({ id: '', type: 'neoorigins:attribute_modifier', fields: {} });
	await expectExportBlocked(d, 'blank id');
});

await check('gate — colliding power ids refuse export', async () => {
	const d = makeDraft();
	d.powers.push({ id: 'starter_robes', type: 'neoorigins:attribute_modifier', fields: {} });
	const e = await expectExportBlocked(d, 'starter_robes');
	assert(
		e.issues.some((m) => m.includes('powers/starter_robes.json')),
		`expected collision message to name the colliding file, got: ${e.issues.join(' | ')}`
	);
});

await check('gate — multiple problems reported together', async () => {
	const d = makeDraft();
	d.path = '';
	d.powers.push({ id: '', type: 'neoorigins:attribute_modifier', fields: {} });
	try {
		await exportDatapack(d);
		throw new Error('expected exportDatapack to throw');
	} catch (e) {
		assert(e instanceof ExportValidationError, 'expected ExportValidationError');
		assert(e.issues.length === 2, `expected 2 issues, got ${e.issues.length}`);
	}
});

await check('gate — validateDraftIds maps issues to fields/indices', () => {
	const d = makeDraft();
	d.path = '';
	d.powers.push({ id: 'starter_robes', type: 'neoorigins:attribute_modifier', fields: {} });
	const issues = validateDraftIds(d);
	assert(issues.length === 2, `expected 2 issues, got ${issues.length}`);
	const pathIssue = issues.find((i) => i.scope === 'origin' && i.field === 'path');
	assert(pathIssue, 'expected an origin/path issue');
	const dupIssue = issues.find((i) => i.scope === 'power' && i.field === 'id');
	assert(dupIssue, 'expected a power/id issue');
	assert(dupIssue.powerIndex === 1, `expected powerIndex 1, got ${dupIssue.powerIndex}`);
});

await check('gate — clean draft passes validateDraftIds', () => {
	const issues = validateDraftIds(makeDraft());
	assert(issues.length === 0, `expected no issues, got: ${issues.map((i) => i.message).join(' | ')}`);
});

await check('suggestedFilename — populated id', () => {
	const name = suggestedFilename(makeDraft());
	assert(name === 'mypack_wizard_datapack.zip', `got ${name}`);
});

await check('suggestedFilename — empty path falls back', () => {
	const d = makeDraft();
	d.path = '';
	const name = suggestedFilename(d);
	assert(name === 'neoorigins_custom_datapack.zip', `got ${name}`);
});

await check('suggestedFilename — empty namespace falls back', () => {
	const d = makeDraft();
	d.namespace = '';
	const name = suggestedFilename(d);
	assert(name === 'neoorigins_custom_datapack.zip', `got ${name}`);
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) {
	process.exit(1);
}
