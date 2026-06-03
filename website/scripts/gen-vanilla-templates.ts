// Generates `static/vanilla-templates.json` — the data behind the editor's
// "Load vanilla template" button. Reads the mod's shipped origin/class/power
// JSON straight out of `src/main/resources` and bakes a lean, fetch-once
// manifest the browser can turn into an editable `OriginDraft`.
//
// Run from the `website/` dir:  npm run gen:templates
//
// Design notes:
//  - name/description are stored translation KEYS in the mod files
//    (e.g. "origins.neoorigins.avian.name"). We resolve them against
//    en_us.json and substitute the resolved strings into the stored body,
//    so a loaded template shows "Avian" (not the raw key) and exports as a
//    plain-string override.
//  - `tier_powers` (the evolution system) is NOT modelled by the web
//    editor's OriginDraft, so we drop it from the stored body but record
//    `tierPowerCount` so the loader can warn the user about the loss.
//  - Only the flat `powers` list's bodies are bundled (that's all the
//    importer's buildDraft resolves); tier-power bodies are intentionally
//    omitted as dead weight.

import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = resolve(HERE, '../../src/main/resources');
const ORIGINS_DIR = resolve(RES, 'data/neoorigins/origins/origins');
const POWERS_DIR = resolve(RES, 'data/neoorigins/origins/powers');
const LAYERS_DIR = resolve(RES, 'data/neoorigins/origins/origin_layers');
const LANG = resolve(RES, 'assets/neoorigins/lang/en_us.json');
const PACK_MCMETA = resolve(RES, 'pack.mcmeta');
const OUT = resolve(HERE, '../static/vanilla-templates.json');

const NS = 'neoorigins';

interface TemplateEntry {
	id: string;
	localId: string;
	name: string;
	isClass: boolean;
	icon: string | null;
	impact: string;
	layerPath: string;
	/** Count of distinct evolution (tier_powers) add-refs dropped — for the load warning. */
	tierPowerCount: number;
	/** Origin JSON body with translate keys resolved and tier_powers stripped. */
	originBody: Record<string, unknown>;
	/** localId → flat-power JSON body (verbatim). */
	powers: Record<string, string>;
}

function readJson(path: string): any {
	return JSON.parse(readFileSync(path, 'utf8'));
}

/** Resolve a name/description field: a translate key → its en_us value, else
 *  the value as-is (string), else the fallback. */
function resolveText(v: unknown, lang: Record<string, string>, fallback: string): string {
	if (typeof v === 'string') return lang[v] ?? v;
	if (v && typeof v === 'object') {
		const o = v as Record<string, unknown>;
		if (typeof o.translate === 'string') return lang[o.translate] ?? (o.fallback as string) ?? o.translate;
		if (typeof o.text === 'string') return o.text;
	}
	return fallback;
}

function localOf(ref: string): string | null {
	const colon = ref.indexOf(':');
	if (colon < 0) return null;
	return ref.slice(0, colon) === NS ? ref.slice(colon + 1) : null;
}

function main() {
	const lang: Record<string, string> = readJson(LANG);
	const packFormat: number = readJson(PACK_MCMETA).pack.pack_format;

	// id → layer path ("origin" | "class"), from the two layer files.
	const layerOf = new Map<string, string>();
	for (const layerFile of ['origin.json', 'class.json']) {
		const layer = readJson(resolve(LAYERS_DIR, layerFile));
		const layerPath = layerFile.replace(/\.json$/, '');
		for (const id of layer.origins ?? []) layerOf.set(id, layerPath);
	}

	const entries: TemplateEntry[] = [];
	const files = readdirSync(ORIGINS_DIR).filter((f) => f.endsWith('.json')).sort();

	for (const file of files) {
		const localId = file.replace(/\.json$/, '');
		const id = `${NS}:${localId}`;
		const raw = readJson(resolve(ORIGINS_DIR, file));

		// Count evolution add-powers before stripping tier_powers.
		const tierRefs = new Set<string>();
		for (const t of raw.tier_powers ?? []) for (const a of t.add ?? []) tierRefs.add(a);

		// Build the stored body: resolve text, drop tier_powers, keep the rest.
		const body: Record<string, unknown> = { ...raw };
		body.name = resolveText(raw.name, lang, localId);
		body.description = resolveText(raw.description, lang, '');
		delete body.tier_powers;
		if (Array.isArray(body.upgrades) && body.upgrades.length === 0) delete body.upgrades;

		// Bundle the flat powers' bodies.
		const powers: Record<string, string> = {};
		for (const ref of raw.powers ?? []) {
			const pl = localOf(ref);
			if (!pl) continue;
			try {
				powers[pl] = readFileSync(resolve(POWERS_DIR, `${pl}.json`), 'utf8');
			} catch {
				// Power file missing (built-in/external) — buildDraft will warn on load.
			}
		}

		const layerPath = layerOf.get(id) ?? (localId.startsWith('class_') ? 'class' : 'origin');
		entries.push({
			id,
			localId,
			name: resolveText(raw.name, lang, localId),
			isClass: layerPath === 'class',
			icon: typeof raw.icon === 'string' ? raw.icon : null,
			impact: typeof raw.impact === 'string' ? raw.impact : 'none',
			layerPath,
			tierPowerCount: tierRefs.size,
			originBody: body,
			powers
		});
	}

	entries.sort((a, b) => a.name.localeCompare(b.name));

	const manifest = {
		generatedFrom: `neoorigins (pack_format ${packFormat})`,
		generatedAt: new Date().toISOString(),
		namespace: NS,
		packFormat,
		entries
	};

	mkdirSync(dirname(OUT), { recursive: true });
	writeFileSync(OUT, JSON.stringify(manifest), 'utf8');

	const classes = entries.filter((e) => e.isClass).length;
	const origins = entries.length - classes;
	const withTiers = entries.filter((e) => e.tierPowerCount > 0).length;
	console.log(
		`Wrote ${OUT}\n  ${entries.length} entries (${origins} origins, ${classes} classes), ` +
			`${withTiers} carry tier_powers (dropped), pack_format ${packFormat}`
	);
}

main();
