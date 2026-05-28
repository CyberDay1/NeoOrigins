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

import { exportDatapack, suggestedFilename } from '../export.js';
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

await check('zip contains exactly the expected 3 files', async () => {
	const blob = await exportDatapack(makeDraft());
	const files = unzipSync(new Uint8Array(await blob.arrayBuffer()));
	const keys = Object.keys(files).sort();
	const expected = [
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
