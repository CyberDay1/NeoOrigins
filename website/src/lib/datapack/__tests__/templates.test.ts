// Every "Load vanilla template" entry exports back the origin it came from:
// same keys (tier_powers included), same power list, same power bodies.
//
//   npx tsx src/lib/datapack/__tests__/templates.test.ts

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

import { loadTemplate } from '../templateDraft.js';
import type { TemplateEntry, TemplateManifest } from '../vanillaTemplates.js';
import { serializeOrigin } from '../../schema/originSerializer.js';

let failed = 0;
let passed = 0;
function check(label: string, fn: () => void): void {
	try {
		fn();
		passed++;
		console.log(`  pass  ${label}`);
	} catch (e) {
		failed++;
		console.error(`  FAIL  ${label}`);
		console.error('         ' + (e instanceof Error ? e.message : String(e)));
	}
}
function assert(cond: unknown, msg: string): asserts cond {
	if (!cond) throw new Error(msg);
}
const canon = (v: unknown): string =>
	JSON.stringify(v, (_k, x) =>
		x && typeof x === 'object' && !Array.isArray(x)
			? Object.fromEntries(Object.keys(x).sort().map((k) => [k, (x as Record<string, unknown>)[k]]))
			: x
	);

const manifest = JSON.parse(
	readFileSync(fileURLToPath(new URL('../../../../static/vanilla-templates.json', import.meta.url)), 'utf8')
) as TemplateManifest & { entries: TemplateEntry[] };

check('every template exports its own origin body, tier_powers included', () => {
	assert(manifest.entries.length >= 70, `only ${manifest.entries.length} templates`);
	const bad: string[] = [];
	let tiers = 0;
	for (const entry of manifest.entries) {
		const bundle = serializeOrigin(loadTemplate(entry, manifest).draft);
		if ('tier_powers' in bundle.origin) tiers++;
		// impact "none" is the default and is written by omission
		const want = { ...entry.originBody };
		if (want.impact === 'none') delete want.impact;
		if (canon(bundle.origin) !== canon(want)) {
			bad.push(`${entry.id}\n      was ${canon(entry.originBody)}\n      now ${canon(bundle.origin)}`);
		}
	}
	assert(tiers >= 50, `tier_powers exported on only ${tiers}`);
	assert(bad.length === 0, `${bad.length} changed:\n${bad.slice(0, 5).join('\n')}`);
});

check('every template exports its power bodies unchanged', () => {
	const bad: string[] = [];
	for (const entry of manifest.entries) {
		const bundle = serializeOrigin(loadTemplate(entry, manifest).draft);
		for (const p of bundle.powers) {
			const src = entry.powers[p.id];
			if (src === undefined) continue;
			if (canon(JSON.parse(src)) !== canon(p.json)) bad.push(`${p.fullId}\n      was ${src}\n      now ${canon(p.json)}`);
		}
	}
	assert(bad.length === 0, `${bad.length} changed:\n${bad.slice(0, 5).join('\n')}`);
});

check('new drafts default to the creator namespace, never neoorigins', () => {
	// originDraft.ts / mobOriginDraft.ts import SvelteKit modules node cannot load, so read the source
	const read = (rel: string) => readFileSync(fileURLToPath(new URL(rel, import.meta.url)), 'utf8');
	for (const rel of ['../../stores/originDraft.ts', '../../stores/mobOriginDraft.ts']) {
		assert(/DEFAULT_NAMESPACE = 'neoorigins_custom'/.test(read(rel)), `${rel} default namespace`);
	}
	for (const rel of ['../import.ts', '../mobImport.ts']) {
		assert(!/namespace: 'neoorigins',/.test(read(rel)), `${rel} blank draft still uses neoorigins`);
	}
});

console.log(`\n${passed} passed, ${failed} failed`);
if (failed > 0) process.exit(1);
