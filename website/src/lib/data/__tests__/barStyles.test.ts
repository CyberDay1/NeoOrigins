// The `hud_render.animated` suggestions must name exactly the presets the mod ships,
// and the schema-driven form must offer them on that field.
// Framework-free to match the sibling suites: exit non-zero on failure.
//
//   npm run check:barstyles

import { readdirSync, readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve } from 'node:path';

import { BAR_STYLES, suggestionsFor } from '../suggestionKind.js';
import { parsePowerSchema } from '../../schema/SchemaFormModel.js';
import type { VanillaData } from '../vanilla.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '../../../../..');
const presetDir = resolve(repoRoot, 'src/main/resources/assets/neoorigins/bar_fx');
const schemaDir = resolve(repoRoot, 'docs/schema');

let failed = 0;
function check(label: string, ok: boolean, detail: string): void {
	if (ok) {
		console.log(`  pass  ${label}`);
	} else {
		failed++;
		console.error(`  FAIL  ${label}\n         ${detail}`);
	}
}

const shipped = readdirSync(presetDir)
	.filter((f) => f.endsWith('.json'))
	.map((f) => `neoorigins:${f.slice(0, -'.json'.length)}`)
	.sort();
const listed = [...BAR_STYLES].sort();
check(
	'BAR_STYLES matches assets/neoorigins/bar_fx',
	JSON.stringify(shipped) === JSON.stringify(listed),
	`shipped ${shipped.join(', ')}; listed ${listed.join(', ')}`
);

const read = (n: string) => JSON.parse(readFileSync(resolve(schemaDir, n), 'utf-8'));
const fields = parsePowerSchema(read('power.schema.json'), read('field_docs.json'), 'neoorigins:resource');
const hud = fields.find((f) => f.name === 'hud_render');
const animated =
	hud && hud.kind === 'OBJECT' ? hud.children.find((c) => c.name === 'animated') : undefined;
check(
	'hud_render.animated is a free-text STRING field',
	animated?.kind === 'STRING',
	`got ${animated ? animated.kind : 'no field'}`
);
const offered = animated ? suggestionsFor(animated.name, {} as VanillaData) : [];
check(
	'hud_render.animated offers every built-in style',
	JSON.stringify([...offered].sort()) === JSON.stringify(listed),
	`offered ${offered.join(', ')}`
);

process.exit(failed > 0 ? 1 : 0);
