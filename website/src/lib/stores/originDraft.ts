import { writable } from 'svelte/store';

/**
 * In-memory draft model for the Origin editor.
 *
 * Mirrors the locked Identity-tab field set from
 * `planning/web_editor_scope.md` (2026-05-21). The on-disk JSON shape
 * (see `docs/schema/origin.schema.json`) is richer — `upgrades`,
 * `unchoosable`, `hidden`, translatable text objects — but the MVP
 * editor exposes only the five Identity fields plus a powers array.
 * The serializer pass (backlog task #14 / JSON Preview tab) will
 * lower this draft into the schema-conformant JSON.
 *
 * `impact` is stored lowercase to match the wire format directly —
 * the serializer emits it as-is (omitting the default `'none'`).
 */
export interface OriginDraft {
	/** Namespaced id, e.g. "neoorigins:my_origin". */
	id: string;
	name: string;
	description: string;
	/** Text glyph for MVP, e.g. "✦" or "@". No item picker yet. */
	icon: string;
	impact: 'none' | 'low' | 'medium' | 'high';
	order: number;
	/** Hidden from the in-game origin selection screen. */
	unchoosable: boolean;
	/** Excluded from listings entirely (developer/testing). */
	hidden: boolean;
	/** Empty for MVP; Powers tab (task #13) will populate. */
	powers: PowerDraft[];
}

export interface PowerDraft {
	/** Local id within the origin namespace. */
	id: string;
	/** Power type id, e.g. "neoorigins:starting_equipment". */
	type: string;
	/** Field values keyed by schema field name. */
	fields: Record<string, unknown>;
}

export function createDraft(): OriginDraft {
	return {
		id: '',
		name: '',
		description: '',
		icon: '',
		impact: 'none',
		order: 0,
		unchoosable: false,
		hidden: false,
		powers: []
	};
}

export const draft = writable<OriginDraft>(createDraft());

export function resetDraft(): void {
	draft.set(createDraft());
}
