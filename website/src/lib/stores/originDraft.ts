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
	/**
	 * Layer this origin lives in. Defaults to `'neoorigins:origin'`
	 * (a normal origin). Set to `'neoorigins:class'` to author a class.
	 *
	 * Field is `string` to leave the door open for custom layers in
	 * other namespaces — the UI just defaults to a `<select>` over the
	 * two built-ins (see {@link KNOWN_LAYERS}). The layer-extension file
	 * `data/<userNs>/origins/origin_layers/<layerPath>.json` is emitted
	 * unconditionally by the exporter — without it, the loader's layer
	 * merger (`LayerDataManager.mergeForeignSamePathLayers`) never picks
	 * the origin up, regardless of which layer was chosen.
	 *
	 * IMPORTANT: layer ids use the `neoorigins:` namespace, NOT
	 * `origins:` — `origins:*` is reserved for the Apoli compat layer.
	 */
	layerId: string;
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
	/**
	 * Optional progression entries — when the player meets the
	 * advancement, they're upgraded into the named origin (with optional
	 * chat announcement). Shared between origin- and class-layer
	 * authoring (`examples/class_tier_up/` is the canonical class case,
	 * but normal origins are allowed upgrades by the schema too).
	 *
	 * Kept `undefined` rather than `[]` until the user actually adds an
	 * entry, so the serializer can cleanly omit the field.
	 */
	upgrades?: Array<{ advancement: string; origin: string; announcement?: string }>;
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

/** Default layer id for new drafts — a normal origin in the vanilla picker. */
export const DEFAULT_LAYER_ID = 'neoorigins:origin';

/**
 * Built-in layer ids the UI surfaces in a `<select>`. Custom layers in
 * other namespaces are NOT blocked at the type level (the field is
 * `string`); they're just not in the dropdown for MVP.
 *
 * The `neoorigins:` namespace is deliberate — `origins:*` is reserved
 * for the Apoli compat layer and is NOT a valid choice for authored
 * NeoOrigins content.
 */
export const KNOWN_LAYERS = [
	{ id: 'neoorigins:origin', label: 'Origin' },
	{ id: 'neoorigins:class', label: 'Class' }
] as const;

/** Valid Minecraft namespace characters. */
export const NAMESPACE_PATTERN = /^[a-z0-9_.-]+$/;
/** Valid Minecraft resource-path characters (allows `/` for subfolders). */
export const PATH_PATTERN = /^[a-z0-9_/.-]+$/;
/**
 * Standard Minecraft ResourceLocation regex — `<namespace>:<path>`.
 * Used to validate `upgrades[].advancement` and `upgrades[].origin`.
 */
export const RESOURCE_LOCATION_PATTERN = /^[a-z0-9_.-]+:[a-z0-9_/.-]+$/;

export function createDraft(): OriginDraft {
	return {
		namespace: DEFAULT_NAMESPACE,
		path: '',
		layerId: DEFAULT_LAYER_ID,
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
