// Generates bundled vanilla suggestion data for the editor's typeahead inputs.
//
//   node scripts/gen-vanilla-data.mjs
//
// Reads the vanilla Minecraft client jars (no server jar needed — everything we
// want ships in the client jar) and emits one JSON file per target version under
// src/lib/data/vanilla/. The data is used ONLY as autocomplete suggestions; the
// fields still accept free text, so the lists don't need to be exhaustive.
//
// Sources, all offline from the jar:
//   - entities / items   ← assets/minecraft/lang/en_us.json translation keys
//                          (entity.minecraft.X / item.minecraft.X / block.minecraft.X,
//                           single path segment only — sub-keys like villager
//                           professions or banner patterns are skipped)
//   - biomes / structures← data/minecraft/worldgen/{biome,structure}/*.json filenames
//   - *Tags              ← data/minecraft/tags/.../*.json filenames (recursive)
//   - dimensions         ← the three built-in dimensions (no data-driven dims in vanilla)

import { readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { unzipSync } from 'fflate';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(here, '../src/lib/data/vanilla');

// Map the editor's target-version id → the client jar that backs it.
const VERSIONS = [
	{ id: '1.21.1', jar: 'C:/Users/conno/curseforge/minecraft/Install/versions/1.21.1/1.21.1.jar' },
	{ id: '26.1', jar: 'C:/Users/conno/curseforge/minecraft/Install/versions/26.1.2/26.1.2.jar' }
];

const DIMENSIONS = ['minecraft:overworld', 'minecraft:the_nether', 'minecraft:the_end'];

const dec = new TextDecoder();
const sortUniq = (arr) => [...new Set(arr)].sort();

function idsFromLang(langJson, prefixes) {
	const obj = JSON.parse(langJson);
	const out = [];
	for (const key of Object.keys(obj)) {
		for (const prefix of prefixes) {
			// `<prefix>.minecraft.<single_segment>` — reject keys with extra dots
			// (villager professions, banner patterns, fish variants, …).
			const m = key.match(new RegExp(`^${prefix}\\.minecraft\\.([a-z0-9_]+)$`));
			if (m) out.push('minecraft:' + m[1]);
		}
	}
	return out;
}

function idsFromDir(names, dir) {
	const re = new RegExp(`^${dir}/(.+)\\.json$`);
	const out = [];
	for (const name of names) {
		const m = name.match(re);
		if (m) out.push('minecraft:' + m[1]);
	}
	return out;
}

for (const { id, jar } of VERSIONS) {
	const buf = readFileSync(jar);
	const zip = unzipSync(new Uint8Array(buf));
	const names = Object.keys(zip);

	const langName = 'assets/minecraft/lang/en_us.json';
	const lang = dec.decode(zip[langName]);

	const data = {
		minecraftVersion: id,
		entities: sortUniq(idsFromLang(lang, ['entity'])),
		items: sortUniq(idsFromLang(lang, ['item', 'block'])),
		biomes: sortUniq(idsFromDir(names, 'data/minecraft/worldgen/biome')),
		biomeTags: sortUniq(idsFromDir(names, 'data/minecraft/tags/worldgen/biome')),
		structures: sortUniq(idsFromDir(names, 'data/minecraft/worldgen/structure')),
		structureTags: sortUniq(idsFromDir(names, 'data/minecraft/tags/worldgen/structure')),
		entityTags: sortUniq(idsFromDir(names, 'data/minecraft/tags/entity_type')),
		dimensions: [...DIMENSIONS]
	};

	mkdirSync(outDir, { recursive: true });
	const outPath = resolve(outDir, `${id}.json`);
	writeFileSync(outPath, JSON.stringify(data, null, '\t') + '\n');
	console.log(
		`${id}: entities=${data.entities.length} items=${data.items.length} ` +
			`biomes=${data.biomes.length} biomeTags=${data.biomeTags.length} ` +
			`structures=${data.structures.length} structureTags=${data.structureTags.length} ` +
			`entityTags=${data.entityTags.length} → ${outPath}`
	);
}
