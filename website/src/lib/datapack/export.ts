// Datapack `.zip` export.
//
// Lowers the in-memory `OriginDraft` to disk via `serializeOrigin`
// (see `src/lib/schema/originSerializer.ts`) and packs the resulting
// JSON files into a zip with `fflate`. Layout, locked in
// `planning/web_editor_scope.md` §3:
//
//   pack.mcmeta
//   data/<ns>/origins/origins/<localId>.json
//   data/<ns>/origins/powers/<powerLocalId>.json   (one per power)
//
// `fflate.zipSync` is fine here — the payloads are a handful of small
// JSON files (KB-range), so the sync cost is negligible and we dodge
// async-callback shape juggling.

import { zipSync, type Zippable } from 'fflate';

import { serializeOrigin } from '$lib/schema/originSerializer';
import type { OriginDraft } from '$lib/stores/originDraft';

/**
 * Datapack `pack_format` for MC 1.21.1 — the vanilla value is `48`,
 * and that's what user datapacks dropped into `world/datapacks/` must
 * declare or the game refuses to load them. The mod jar's own
 * `pack.mcmeta` uses `84` only because it pairs that with
 * `supported_formats: [0, 2147483647]` to bypass MC's version gate;
 * regular datapacks don't get that escape hatch, so we ship `48` here.
 */
const PACK_FORMAT = 48;

/**
 * Default filename used when the draft has no namespaced id yet.
 * Matches the prose style of the mod's own `pack.mcmeta` description.
 */
const FALLBACK_FILENAME = 'neoorigins_custom_datapack.zip';

/**
 * Compute a suggested filename for the download. The shell wires this
 * into the `<a download>` attribute. Falls back to a neutral name when
 * the user hasn't filled in a namespaced id yet.
 */
export function suggestedFilename(draft: OriginDraft): string {
	// Draft model stores namespace + path as independent fields (mirroring
	// the in-game Java editor). Treat an empty path the same as an
	// "id not filled in yet" state, even if the namespace has its default.
	const ns = draft.namespace?.trim() ?? '';
	const local = draft.path?.trim() ?? '';
	if (!ns || !local) return FALLBACK_FILENAME;
	return `${ns}_${local}_datapack.zip`;
}

/**
 * Build a complete datapack `.zip` Blob from the current draft.
 * Throws if the serializer rejects the draft.
 */
export async function exportDatapack(draft: OriginDraft): Promise<Blob> {
	const bundle = serializeOrigin(draft);

	const description = (draft.name?.trim() || 'Custom NeoOrigins datapack') +
		' — built with NeoOrigins Web Editor';

	const mcmeta = {
		pack: {
			pack_format: PACK_FORMAT,
			description
		}
	};

	const enc = new TextEncoder();
	const entries: Zippable = {
		'pack.mcmeta': enc.encode(JSON.stringify(mcmeta, null, 2)),
		[bundle.originPath]: enc.encode(JSON.stringify(bundle.origin, null, 2))
	};
	for (const power of bundle.powers) {
		entries[power.path] = enc.encode(JSON.stringify(power.json, null, 2));
	}

	const bytes = zipSync(entries);
	return new Blob([bytes], { type: 'application/zip' });
}
