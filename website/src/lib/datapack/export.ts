// Datapack `.zip` export.
//
// Lowers the in-memory `OriginDraft` to disk via `serializeOrigin`
// (see `src/lib/schema/originSerializer.ts`) and packs the resulting
// JSON files into a zip with `fflate`. Layout, locked in
// `planning/web_editor_scope.md` §3:
//
//   pack.mcmeta
//   data/<ns>/origins/origins/<localId>.json
//   data/<ns>/origins/origin_layers/<layerPath>.json   (layer-extension)
//   data/<ns>/origins/powers/<powerLocalId>.json       (one per power)
//
// The layer-extension file is REQUIRED for the origin to appear in any
// picker — without it, the loader's `LayerDataManager` merger has
// nothing to fold onto the canonical layer. We ship one for both layer
// choices (origin and class).
//
// `fflate.zipSync` is fine here — the payloads are a handful of small
// JSON files (KB-range), so the sync cost is negligible and we dodge
// async-callback shape juggling.

import { zipSync, type Zippable } from 'fflate';

import { serializeOrigin } from '$lib/schema/originSerializer';
import type { OriginDraft } from '$lib/stores/originDraft';

/**
 * Default datapack `pack_format` for MC 1.21.1 — the vanilla value is
 * `48`, and that's what user datapacks dropped into `world/datapacks/`
 * must declare or the game refuses to load them. The mod jar's own
 * `pack.mcmeta` uses `84` only because it pairs that with
 * `supported_formats: [0, 2147483647]` to bypass MC's version gate;
 * regular datapacks don't get that escape hatch, so we ship `48` here.
 *
 * Phase 2 introduced a target-version toggle (1.21.1 → 48, 26.1 → 84);
 * the UI passes the chosen value through to `exportDatapack`. This
 * constant remains the fallback for callers that don't specify one
 * (e.g. existing tests).
 */
const DEFAULT_PACK_FORMAT = 48;

/**
 * Default filename used when the draft has no namespaced id yet.
 * Matches the prose style of the mod's own `pack.mcmeta` description.
 */
const FALLBACK_FILENAME = 'neoorigins_custom_datapack.zip';

// ── export validation gate ──────────────────────────────────────────────────
//
// A blank origin path or blank power id would export broken files like
// `data/<ns>/origins/origins/.json`, and duplicate power ids would collide
// on the same `powers/<id>.json` entry (last write wins, silently). The
// export is REFUSED in those cases — blocking was chosen over auto-default
// ids so the user always names their content deliberately.

/**
 * One structured problem found by {@link validateDraftIds}. `scope` +
 * `powerIndex` + `field` let the form editor pin the message to the exact
 * offending input; `message` is the human-readable text shown in the UI.
 */
export interface DraftIssue {
	scope: 'origin' | 'power';
	/** Index into `draft.powers` when `scope === 'power'`. */
	powerIndex?: number;
	/** Draft field key: `namespace` / `path` for origin, `id` for powers. */
	field: string;
	message: string;
}

/**
 * Check the draft's identifiers for problems that would produce a broken
 * or self-colliding datapack: blank origin namespace/path, blank power
 * ids, and duplicate power ids. Returns an empty array when exportable.
 *
 * Whitespace-only values count as blank. Shared by the export gate below
 * and the live form validation (`$lib/stores/originValidation`).
 */
export function validateDraftIds(draft: OriginDraft): DraftIssue[] {
	const issues: DraftIssue[] = [];

	if (!draft.namespace?.trim()) {
		issues.push({
			scope: 'origin',
			field: 'namespace',
			message: 'Origin namespace is blank — fill it in on the Identity tab before exporting.'
		});
	}
	if (!draft.path?.trim()) {
		issues.push({
			scope: 'origin',
			field: 'path',
			message:
				'Origin path (id) is blank — a blank path would export a broken origins/.json file. Fill it in on the Identity tab.'
		});
	}

	const firstIndexById = new Map<string, number>();
	draft.powers.forEach((p, i) => {
		const id = p.id?.trim() ?? '';
		const label = `Power ${i + 1}`;
		if (!id) {
			issues.push({
				scope: 'power',
				powerIndex: i,
				field: 'id',
				message: `${label} has a blank id — it would export as a broken powers/.json file. Give it an id on the Powers tab.`
			});
			return;
		}
		const first = firstIndexById.get(id);
		if (first === undefined) {
			firstIndexById.set(id, i);
		} else {
			issues.push({
				scope: 'power',
				powerIndex: i,
				field: 'id',
				message: `${label} reuses the id "${id}" (same as power ${first + 1}) — both would export to powers/${id}.json and overwrite each other. Rename one.`
			});
		}
	});

	return issues;
}

/**
 * Thrown by {@link exportDatapack} when the draft fails
 * {@link validateDraftIds}. Carries the full human-readable issue list so
 * the UI can show every problem at once instead of just the first.
 */
export class ExportValidationError extends Error {
	readonly issues: string[];

	constructor(issues: string[]) {
		super(`Export blocked: ${issues.join(' | ')}`);
		this.name = 'ExportValidationError';
		this.issues = issues;
	}
}

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
 *
 * `packFormat` defaults to {@link DEFAULT_PACK_FORMAT} (48, MC 1.21.1).
 * Pass `84` for MC 26.1 — the UI's version toggle plumbs the user's
 * choice through here (see `$lib/stores/originDraft.ts`'s
 * `TARGET_VERSIONS`).
 *
 * Throws {@link ExportValidationError} (with the full issue list) when the
 * draft has a blank origin path/namespace, a blank power id, or colliding
 * power ids — see {@link validateDraftIds}.
 */
export async function exportDatapack(
	draft: OriginDraft,
	packFormat: number = DEFAULT_PACK_FORMAT
): Promise<Blob> {
	const idIssues = validateDraftIds(draft);
	if (idIssues.length > 0) {
		throw new ExportValidationError(idIssues.map((i) => i.message));
	}

	const bundle = serializeOrigin(draft);

	const description = (draft.name?.trim() || 'Custom NeoOrigins datapack') +
		' — built with NeoOrigins Web Editor';

	const mcmeta = {
		pack: {
			pack_format: packFormat,
			description
		}
	};

	const enc = new TextEncoder();
	const entries: Zippable = {
		'pack.mcmeta': enc.encode(JSON.stringify(mcmeta, null, 2)),
		[bundle.originPath]: enc.encode(JSON.stringify(bundle.origin, null, 2)),
		[bundle.layerExtensionPath]: enc.encode(
			JSON.stringify(bundle.layerExtension, null, 2)
		)
	};
	for (const power of bundle.powers) {
		entries[power.path] = enc.encode(JSON.stringify(power.json, null, 2));
	}

	const bytes = zipSync(entries);
	return new Blob([bytes], { type: 'application/zip' });
}
