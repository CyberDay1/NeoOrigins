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
	/**
	 * Datapack namespace (the part before `:`). Defaults to `neoorigins`.
	 * Mirrors the in-game Java editor's split-field model: the path
	 * segment is stored separately from the namespace, and the two are
	 * joined with `:` at serialize time (see {@link fullId}).
	 *
	 * Java parallel: {@code OriginDraft.idPath} + {@code CUSTOM_NAMESPACE}
	 * in `src/main/java/com/cyberday1/neoorigins/screen/creator`. The
	 * Java editor pins the namespace to a constant; the web editor is
	 * intentionally more permissive (unknown user packs).
	 */
	namespace: string;
	/** Path segment of the id (everything after `:`), e.g. `wizard`. */
	path: string;
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

/** Default namespace for new drafts — mirrors `CUSTOM_NAMESPACE` on the Java side. */
export const DEFAULT_NAMESPACE = 'neoorigins';

/** Valid Minecraft namespace characters. */
export const NAMESPACE_PATTERN = /^[a-z0-9_.-]+$/;
/** Valid Minecraft resource-path characters (allows `/` for subfolders). */
export const PATH_PATTERN = /^[a-z0-9_/.-]+$/;

export function createDraft(): OriginDraft {
	return {
		namespace: DEFAULT_NAMESPACE,
		path: '',
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

/**
 * Derived helper: join `namespace` + `path` into a full resource id
 * (e.g. `neoorigins:wizard`). Not stored on the draft — recompute at
 * read time so the two halves never disagree.
 */
export function fullId(draft: Pick<OriginDraft, 'namespace' | 'path'>): string {
	return `${draft.namespace}:${draft.path}`;
}

export const draft = writable<OriginDraft>(createDraft());

export function resetDraft(): void {
	draft.set(createDraft());
}
