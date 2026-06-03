// Mob-origin datapack `.zip` export.
//
// Lowers the in-memory `MobOriginDraft` via `serializeMobOrigin` and packs
// the result into a zip with `fflate`. Layout:
//
//   pack.mcmeta
//   data/<ns>/origins/mob_origins/<localId>.json
//   data/<ns>/origins/powers/<powerLocalId>.json   (one per power)
//
// Unlike the player origin export there is NO layer-extension file — mob
// origins are loaded directly from the `mob_origins/` folder by
// MobOriginDataManager (no layer-merge step).

import { zipSync, type Zippable } from 'fflate';

import { serializeMobOrigin } from '$lib/schema/mobOriginSerializer';
import type { MobOriginDraft } from '$lib/stores/mobOriginDraft';

/** Default datapack pack_format for MC 1.21.1 — see datapack/export.ts. */
const DEFAULT_PACK_FORMAT = 48;

/** Default filename used when the draft has no namespaced id yet. */
const FALLBACK_FILENAME = 'neoorigins_mob_origin_datapack.zip';

/** Suggested download filename. Falls back when the id isn't filled in. */
export function suggestedFilename(draft: MobOriginDraft): string {
	const ns = draft.namespace?.trim() ?? '';
	const local = draft.path?.trim() ?? '';
	if (!ns || !local) return FALLBACK_FILENAME;
	return `${ns}_${local}_mob_origin_datapack.zip`;
}

/**
 * Build a complete mob-origin datapack `.zip` Blob from the current draft.
 * `packFormat` defaults to 48 (MC 1.21.1); pass 84 for MC 26.1.
 */
export async function exportMobDatapack(
	draft: MobOriginDraft,
	packFormat: number = DEFAULT_PACK_FORMAT
): Promise<Blob> {
	const bundle = serializeMobOrigin(draft);

	const description =
		(draft.name?.trim() || 'Custom NeoOrigins mob origin') +
		' — built with NeoOrigins Web Editor';

	const mcmeta = { pack: { pack_format: packFormat, description } };

	const enc = new TextEncoder();
	const entries: Zippable = {
		'pack.mcmeta': enc.encode(JSON.stringify(mcmeta, null, 2)),
		[bundle.mobOriginPath]: enc.encode(JSON.stringify(bundle.mobOrigin, null, 2))
	};
	for (const power of bundle.powers) {
		entries[power.path] = enc.encode(JSON.stringify(power.json, null, 2));
	}

	const bytes = zipSync(entries);
	return new Blob([bytes], { type: 'application/zip' });
}
