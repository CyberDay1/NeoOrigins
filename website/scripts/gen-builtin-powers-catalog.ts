// Generates `static/builtin-powers-catalog.json` — the data behind the
// editor's "Import power" button. Reads EVERY shipped power JSON straight out
// of `src/main/resources` and bakes a lean, fetch-once catalog the browser can
// deep-copy into an editable `PowerDraft`.
//
// Run from the `website/` dir:  npm run gen:powers
//
// Design notes:
//  - Unlike `gen-vanilla-templates.ts` (which bundles whole origins/classes),
//    this catalogs the ~820 individual power files so a user can import ANY
//    built-in power on its own, not just the ones referenced by a curated
//    origin template.
//  - A display name is resolved from `assets/neoorigins/lang/en_us.json` when a
//    matching `power.neoorigins.<localId>.name` key exists; otherwise the
//    localId is prettified (`sword_immortal` → "Sword Immortal").
//  - The full power body is stored verbatim (including its `type`), so the
//    loader can deep-copy it into a draft and round-trip on export.

import { readFileSync, readdirSync, writeFileSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const RES = resolve(HERE, '../../src/main/resources');
const POWERS_DIR = resolve(RES, 'data/neoorigins/origins/powers');
const LANG = resolve(RES, 'assets/neoorigins/lang/en_us.json');
const OUT = resolve(HERE, '../static/builtin-powers-catalog.json');

const NS = 'neoorigins';

interface CatalogEntry {
	/** Fully-qualified power id, e.g. `neoorigins:sword_immortal`. */
	id: string;
	/** Path segment of the id (the file name without `.json`). */
	localId: string;
	/** Human display name (en_us where available, else a prettified localId). */
	name: string;
	/** The power body's `type` field, e.g. `neoorigins:active_ability`. */
	type: string;
	/** The full power JSON body, verbatim (includes `type`). */
	powerBody: Record<string, unknown>;
}

function readJson(path: string): any {
	return JSON.parse(readFileSync(path, 'utf8'));
}

/** `sword_immortal` → "Sword Immortal"; leaves other separators intact. */
function prettify(localId: string): string {
	return localId
		.split(/[_/]/)
		.filter(Boolean)
		.map((w) => w.charAt(0).toUpperCase() + w.slice(1))
		.join(' ');
}

/**
 * Resolve a display name for a power. Prefer a `power.neoorigins.<localId>.name`
 * lang key; if the body carries a translate-key `name`, resolve that; else
 * prettify the localId.
 */
function resolveName(
	localId: string,
	body: Record<string, unknown>,
	lang: Record<string, string>
): string {
	const conventionKey = `power.${NS}.${localId}.name`;
	if (typeof lang[conventionKey] === 'string') return lang[conventionKey];

	const n = body.name;
	if (typeof n === 'string') {
		return lang[n] ?? (n.includes('.') ? prettify(localId) : n);
	}
	if (n && typeof n === 'object') {
		const o = n as Record<string, unknown>;
		if (typeof o.translate === 'string') {
			return lang[o.translate] ?? (typeof o.fallback === 'string' ? o.fallback : prettify(localId));
		}
		if (typeof o.text === 'string') return o.text;
	}
	return prettify(localId);
}

function main() {
	const lang: Record<string, string> = readJson(LANG);

	const files = readdirSync(POWERS_DIR)
		.filter((f) => f.endsWith('.json'))
		.sort();

	const entries: CatalogEntry[] = [];
	for (const file of files) {
		const localId = file.replace(/\.json$/, '');
		let body: Record<string, unknown>;
		try {
			body = readJson(resolve(POWERS_DIR, file));
		} catch {
			// Skip unreadable/non-object power files rather than fail the build.
			continue;
		}
		const type = typeof body.type === 'string' ? body.type : '';
		entries.push({
			id: `${NS}:${localId}`,
			localId,
			name: resolveName(localId, body, lang),
			type,
			powerBody: body
		});
	}

	entries.sort((a, b) => a.name.localeCompare(b.name) || a.localId.localeCompare(b.localId));

	const catalog = {
		generatedAt: new Date().toISOString(),
		namespace: NS,
		entries
	};

	mkdirSync(dirname(OUT), { recursive: true });
	writeFileSync(OUT, JSON.stringify(catalog), 'utf8');

	console.log(`Wrote ${OUT}\n  ${entries.length} built-in power entries`);
}

main();
