// Shared tail of the catalogue generators: write the file, or with `--check`
// fail when the committed copy no longer matches what the mod's data produces.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname } from 'node:path';

/** `generatedAt` changes every run, so it never counts as drift. */
function comparable(o: Record<string, unknown>): string {
	const { generatedAt: _ignored, ...rest } = o;
	return JSON.stringify(rest);
}

export function writeOrCheck(out: string, data: Record<string, unknown>, summary: string): void {
	if (process.argv.includes('--check')) {
		const committed = existsSync(out) ? JSON.parse(readFileSync(out, 'utf8')) : {};
		if (comparable(committed) !== comparable(data)) {
			console.error(`STALE ${out}\n  regenerate it: npm run gen:powers && npm run gen:templates`);
			process.exit(1);
		}
		console.log(`OK ${out} matches src/main/resources\n  ${summary}`);
		return;
	}
	mkdirSync(dirname(out), { recursive: true });
	writeFileSync(out, JSON.stringify(data), 'utf8');
	console.log(`Wrote ${out}\n  ${summary}`);
}
