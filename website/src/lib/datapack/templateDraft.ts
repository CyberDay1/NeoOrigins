// Template entry → editable draft. Pure (no SvelteKit imports) so the node
// test runner can drive it; vanillaTemplates.ts does the fetching.

import { strToU8 } from 'fflate';

import { buildDraft, type ImportResult } from './import';
import type { TemplateEntry, TemplateManifest } from './vanillaTemplates';

/**
 * Turn a template entry into a draft by reconstructing the datapack file map
 * and running it through the shared importer. Notes when the source origin has
 * evolution powers, which are kept but not editable here.
 */
export function loadTemplate(entry: TemplateEntry, manifest: TemplateManifest): ImportResult {
	const ns = manifest.namespace;
	const files: Record<string, Uint8Array> = {};

	files['pack.mcmeta'] = strToU8(
		JSON.stringify({ pack: { pack_format: manifest.packFormat } })
	);
	files[`data/${ns}/origins/origins/${entry.localId}.json`] = strToU8(
		JSON.stringify(entry.originBody)
	);
	for (const [pl, body] of Object.entries(entry.powers)) {
		files[`data/${ns}/origins/powers/${pl}.json`] = strToU8(body);
	}
	// Minimal layer-extension listing this origin so buildDraft resolves the
	// layer id to the correct path ("origin" | "class").
	files[`data/${ns}/origins/origin_layers/${entry.layerPath}.json`] = strToU8(
		JSON.stringify({ origins: [entry.id] })
	);

	const result = buildDraft(files);
	// The importer asks for tier-power files to be copied by hand; a template's
	// are the mod's own and load as they are.
	result.warnings = result.warnings.filter((w) => !w.startsWith('tier_powers references'));
	if (entry.tierPowerCount > 0) {
		result.warnings.unshift(
			`"${entry.name}" has ${entry.tierPowerCount} evolution (tier_powers) ` +
				`power${entry.tierPowerCount === 1 ? '' : 's'}. They are kept as they are ` +
				`and export with the origin, but can't be edited here.`
		);
	}
	return result;
}
