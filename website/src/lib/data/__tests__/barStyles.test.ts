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
import { BAR_STYLE_PRESETS, gradientAt, parseHex, planBar } from '../barPreview.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const repoRoot = resolve(__dirname, '../../../../..');
const presetDir = resolve(repoRoot, 'src/main/resources/assets/neoorigins/bar_fx');
const textureDir = resolve(repoRoot, 'src/main/resources/assets/neoorigins/textures/gui/bar_fx');
const staticDir = resolve(repoRoot, 'website/static/bar_fx');
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

// The style fields sit beside color and validate the same way.
const hudChildren = hud && hud.kind === 'OBJECT' ? hud.children : [];
const pat = (name: string) => {
	const f = hudChildren.find((c) => c.name === name);
	return f && f.kind === 'STRING' ? f.pattern : undefined;
};
const colorPattern = pat('color');
check('hud_render.color has a hex pattern', !!colorPattern, `got ${colorPattern}`);
for (const name of ['tint', 'tint_end']) {
	check(
		`hud_render.${name} is a STRING validated like color`,
		!!colorPattern && pat(name) === colorPattern,
		`got ${pat(name)}`
	);
}
if (colorPattern) {
	const rx = new RegExp(colorPattern);
	const good = ['#FF8800', 'FF8800', '#80FF8800', '#00000000'];
	const bad = ['#FFF', '#FFFF', '#FFFFFFF', '#GGGGGG', 'orange'];
	check('color pattern takes 6 and 8 digits', good.every((v) => rx.test(v)), good.join(', '));
	check('color pattern rejects other lengths', bad.every((v) => !rx.test(v)), bad.join(', '));
	check(
		'the preview reads colours as the pattern does',
		good.every((v) => parseHex(v) !== null) && bad.every((v) => parseHex(v) === null),
		'parseHex disagrees with the schema pattern'
	);
}
const idPattern = pat('animated');
check(
	'hud_render.animated takes every built-in style and a pack id',
	!!idPattern && [...listed, 'mypack:lava'].every((v) => new RegExp(idPattern).test(v)),
	`pattern ${idPattern}`
);

// The preview's style table matches the preset files, and its textures are the shipped ones.
for (const id of shipped) {
	const json = JSON.parse(readFileSync(resolve(presetDir, `${id.slice('neoorigins:'.length)}.json`), 'utf-8'));
	const p = BAR_STYLE_PRESETS[id];
	const tex = String(json.texture).split('/').pop();
	const scrolls = (json.mode ?? 'scroll') !== 'static';
	const want = {
		texture: tex,
		tileW: json.tile_width,
		loopW: json.loop_width ?? json.tile_width,
		scrollSpeed: scrolls ? json.scroll_speed ?? 24 : 0,
		tintFromColor: json.tint_from_color === true
	};
	check(`preview preset ${id} matches its file`, JSON.stringify(p) === JSON.stringify(want),
		`preview ${JSON.stringify(p)}; file ${JSON.stringify(want)}`);
}
check(
	'preview presets name exactly the shipped styles',
	JSON.stringify(Object.keys(BAR_STYLE_PRESETS).sort()) === JSON.stringify(shipped),
	Object.keys(BAR_STYLE_PRESETS).join(', ')
);
for (const f of readdirSync(textureDir).filter((n) => n.endsWith('.png'))) {
	let same = false;
	try {
		same = readFileSync(resolve(staticDir, f)).equals(readFileSync(resolve(textureDir, f)));
	} catch {
		same = false;
	}
	check(`static/bar_fx/${f} is the shipped texture`, same,
		'copy src/main/resources/assets/neoorigins/textures/gui/bar_fx/*.png to website/static/bar_fx/');
}

// Preview rules, against the HUD's.
const flat = planBar({ color: '#FF5500' });
check('no style draws the flat color', flat.kind === 'flat' && flat.color === 0xffff5500, JSON.stringify(flat));
const custom = planBar({ animated: 'mypack:lava' });
check('a pack style falls back to the color fill', custom.kind === 'flat' && custom.customStyle === 'mypack:lava',
	JSON.stringify(custom));
const tinted = planBar({ animated: 'neoorigins:tinted', color: '#112233' });
check('tinted with no tint uses color', tinted.kind === 'style' && tinted.tint === 0xff112233, JSON.stringify(tinted));
const fire = planBar({ animated: 'neoorigins:fire', tint_end: '#00FF00' });
check('a scrolling style ignores tint_end and has no tint by default',
	fire.kind === 'style' && fire.tint === null && fire.tintEnd === null, JSON.stringify(fire));
const grad = planBar({ animated: 'neoorigins:tinted', tint: '#000000', tint_end: '#FFFFFF' });
check('a static style takes tint_end', grad.kind === 'style' && grad.tintEnd === 0xffffffff, JSON.stringify(grad));
const bad = planBar({ animated: 'neoorigins:fire', tint: '#FFF' });
check('a malformed tint falls back to #55AAFF', bad.kind === 'style' && bad.tint === 0xff55aaff, JSON.stringify(bad));
check('gradient runs end to end', gradientAt(0x000000, 0xffffff, 0, 69) === 0 && gradientAt(0x000000, 0xffffff, 68, 69) === 0xffffff,
	'gradientAt endpoints');

process.exit(failed > 0 ? 1 : 0);
