import { writable } from 'svelte/store';
import { browser } from '$app/environment';

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
	/**
	 * Top-level origin-JSON keys the editor has no UI for, carried verbatim
	 * from an imported datapack so export → hand-edit → import → export is
	 * lossless. `origin.schema.json` declares fifteen top-level properties;
	 * the Identity tab covers nine, leaving `tier_powers`, `spawn_location`,
	 * `required_mods`, `special`, `figura_model` and `figura_models`. Those
	 * used to be read and thrown away, so re-exporting a NeoOrigins origin
	 * quietly stripped its evolution tiers and spawn placement.
	 *
	 * Only ever populated by the importer (see `$lib/datapack/import.ts`);
	 * the serializer writes the entries back out with the mapped fields
	 * taking precedence. Keys the editor DOES own are never stored here, so
	 * an extras entry can never shadow an Identity-tab value.
	 *
	 * Left `undefined` when there is nothing to carry, so a draft authored
	 * from scratch serializes exactly as it did before.
	 */
	extras?: Record<string, unknown>;
	/**
	 * Fully-qualified power ids the origin grants that have no power file in the
	 * imported datapack — powers built into the mod (`neoorigins:*`) or supplied
	 * by another pack. The editor cannot show or edit them, but the serializer
	 * appends them to `origin.powers` so a round trip does not silently revoke
	 * grants, which loads fine and is therefore the hardest loss to notice.
	 *
	 * Only ever populated by the importer (see `$lib/datapack/import.ts`);
	 * `undefined` for a draft authored from scratch.
	 */
	externalPowers?: string[];
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

// ── target Minecraft version / pack_format ───────────────────────────────
//
// Phase 2 toggle: 1.21.1 → 48, 26.1 → 84. The editor is schema-driven and
// does NOT translate power types between version lines — the user picks
// their target version and is responsible for sticking to power types that
// exist in that version. The choice is only used to stamp `pack.mcmeta`
// in the export bundle (see `$lib/datapack/export.ts`).

export type TargetMcVersion = '1.21.1' | '26.1';

export interface TargetVersionOption {
	id: TargetMcVersion;
	label: string;
	packFormat: number;
}

export const TARGET_VERSIONS: readonly TargetVersionOption[] = [
	{ id: '1.21.1', label: 'MC 1.21.1', packFormat: 48 },
	{ id: '26.1', label: 'MC 26.1', packFormat: 84 }
] as const;

export const DEFAULT_TARGET_VERSION: TargetMcVersion = '1.21.1';

export function packFormatFor(version: TargetMcVersion): number {
	return TARGET_VERSIONS.find((v) => v.id === version)?.packFormat ?? 48;
}

export const targetVersion = writable<TargetMcVersion>(DEFAULT_TARGET_VERSION);

// ── persisted UI state (active tab) ──────────────────────────────────────
//
// Tab index is stored alongside the draft so a refresh lands the user back
// on whichever tab they were editing. Kept as a writable so `+page.svelte`
// can bind to it without an extra plumbing layer.

export type EditorTab = 'identity' | 'powers' | 'upgrades' | 'json';
export const KNOWN_TABS: readonly EditorTab[] = [
	'identity',
	'powers',
	'upgrades',
	'json'
] as const;

export const activeTab = writable<EditorTab>('identity');

// ── Powers tab view mode ─────────────────────────────────────────────────
//
// The Powers tab offers two interchangeable editing surfaces over the SAME
// `draft.powers` data: the classic stacked form ('form') and a colour-coded
// nested-block view ('blocks'). Module-level so the choice survives tab
// switches within a session. Deliberately NOT persisted to localStorage — it's
// a transient UI preference, not part of the saved draft.

export type PowersView = 'form' | 'blocks';
export const powersView = writable<PowersView>('form');

// ── draft store ──────────────────────────────────────────────────────────

export const draft = writable<OriginDraft>(createDraft());

export function resetDraft(): void {
	draft.set(createDraft());
	targetVersion.set(DEFAULT_TARGET_VERSION);
	activeTab.set('identity');
}

// ── localStorage autosave ────────────────────────────────────────────────
//
// Single key, versioned suffix — bump the suffix if the persisted shape
// ever stops being assignable to the current draft + target + tab.
// Writes are debounced ~300 ms so per-keystroke churn doesn't hammer
// storage. Reads happen once on `initPersistence()` (called from the
// editor route's `onMount`). A corrupt or older payload is discarded
// silently — the editor falls back to a fresh draft.
//
// We do NOT autosave anything sensitive: the draft is only namespace,
// id, display strings, icon glyph, layer choice, and form-driven power
// fields. No credentials, no PII.

export const STORAGE_KEY = 'neoorigins.editor.draft.v1';
const DEBOUNCE_MS = 300;

interface PersistedShape {
	draft: OriginDraft;
	targetVersion: TargetMcVersion;
	activeTab: EditorTab;
}

function isTargetVersion(v: unknown): v is TargetMcVersion {
	return v === '1.21.1' || v === '26.1';
}

function isEditorTab(v: unknown): v is EditorTab {
	return (
		v === 'identity' || v === 'powers' || v === 'upgrades' || v === 'json'
	);
}

function looksLikeDraft(d: unknown): d is OriginDraft {
	if (!d || typeof d !== 'object') return false;
	const o = d as Record<string, unknown>;
	return (
		typeof o.namespace === 'string' &&
		typeof o.path === 'string' &&
		typeof o.layerId === 'string' &&
		typeof o.name === 'string' &&
		typeof o.description === 'string' &&
		typeof o.icon === 'string' &&
		(o.impact === 'none' ||
			o.impact === 'low' ||
			o.impact === 'medium' ||
			o.impact === 'high') &&
		typeof o.order === 'number' &&
		typeof o.unchoosable === 'boolean' &&
		typeof o.hidden === 'boolean' &&
		Array.isArray(o.powers)
	);
}

let persistenceInitialized = false;
let saveTimer: ReturnType<typeof setTimeout> | null = null;
let suppressWrites = false;

function scheduleSave(snapshot: PersistedShape): void {
	if (!browser || suppressWrites) return;
	if (saveTimer) clearTimeout(saveTimer);
	saveTimer = setTimeout(() => {
		saveTimer = null;
		try {
			localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot));
		} catch {
			// QuotaExceeded / disabled storage — silently drop. The editor
			// continues to work; the user just loses cross-session restore.
		}
	}, DEBOUNCE_MS);
}

/**
 * Restore the persisted draft / target version / active tab from
 * localStorage if a valid entry exists, then subscribe the three stores
 * so subsequent writes are autosaved (debounced).
 *
 * Idempotent — safe to call from `onMount` even across HMR. Returns
 * silently on the server (no `window`).
 */
export function initPersistence(): void {
	if (!browser || persistenceInitialized) return;
	persistenceInitialized = true;

	suppressWrites = true;
	try {
		const raw = localStorage.getItem(STORAGE_KEY);
		if (raw) {
			const parsed = JSON.parse(raw) as unknown;
			if (parsed && typeof parsed === 'object') {
				const p = parsed as Record<string, unknown>;
				if (looksLikeDraft(p.draft)) {
					draft.set(p.draft);
				}
				if (isTargetVersion(p.targetVersion)) {
					targetVersion.set(p.targetVersion);
				}
				if (isEditorTab(p.activeTab)) {
					activeTab.set(p.activeTab);
				}
			}
		}
	} catch {
		// Corrupt / older / non-JSON payload — discard silently and start
		// fresh. The next write will overwrite the bad blob.
	} finally {
		suppressWrites = false;
	}

	// Snapshot reader — assembled from the three live store values so we
	// don't need a derived store just for serialization. The subscribers
	// below all funnel through here.
	let currentDraft = createDraft();
	let currentVersion: TargetMcVersion = DEFAULT_TARGET_VERSION;
	let currentTab: EditorTab = 'identity';

	draft.subscribe((v) => {
		currentDraft = v;
		scheduleSave({ draft: currentDraft, targetVersion: currentVersion, activeTab: currentTab });
	});
	targetVersion.subscribe((v) => {
		currentVersion = v;
		scheduleSave({ draft: currentDraft, targetVersion: currentVersion, activeTab: currentTab });
	});
	activeTab.subscribe((v) => {
		currentTab = v;
		scheduleSave({ draft: currentDraft, targetVersion: currentVersion, activeTab: currentTab });
	});
}

/**
 * Wipe the persisted draft from localStorage and reload the page so the
 * editor reboots from defaults. Caller is responsible for `confirm()` —
 * this is destructive.
 */
export function clearPersistedDraft(): void {
	if (!browser) return;
	if (saveTimer) {
		clearTimeout(saveTimer);
		saveTimer = null;
	}
	try {
		localStorage.removeItem(STORAGE_KEY);
	} catch {
		// Ignore — we're about to reload anyway.
	}
	window.location.reload();
}
