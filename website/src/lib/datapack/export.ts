import type { OriginDraft } from '$lib/stores/originDraft';

/**
 * Build a complete datapack `.zip` Blob from the current draft.
 *
 * Planned output layout (see `planning/web_editor_scope.md` §3 MVP scope):
 *
 *   pack.mcmeta
 *   data/<ns>/origins/origins/<id>.json
 *   data/<ns>/origins/powers/<power_id>.json   (one per draft.powers entry)
 *
 * Namespace + path id are split from `draft.id` on the first `:`.
 * The origin JSON shape conforms to `docs/schema/origin.schema.json`;
 * power JSONs conform to `docs/schema/power.schema.json`. Zipping is
 * done via `fflate` (already a dependency) — no JSZip.
 *
 * TODO(backlog #15): implement. Until then this stub throws so the
 * editor shell can wire the "Download datapack" button and toast a
 * "Coming soon" message without a separate feature flag.
 */
export function exportDatapack(_draft: OriginDraft): Promise<Blob> {
	throw new Error('datapack export not yet implemented');
}
