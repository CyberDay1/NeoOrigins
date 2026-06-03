import { writable } from 'svelte/store';
import { browser } from '$app/environment';
import type { PowerDraft } from '$lib/stores/originDraft';

/**
 * In-memory draft model for the Mob Origin editor.
 *
 * Mirrors the in-game Java creator's draft
 * (`screen/mobcreator/model/MobOriginDraft`) field-for-field so the web
 * editor reaches feature parity. The on-disk JSON shape it lowers to is
 * documented in `docs/schema/mob_origin.schema.json` and emitted by
 * `$lib/schema/mobOriginSerializer.ts`, which matches the mod's
 * `MobCustomPackSerializer` byte-for-byte.
 *
 * Like the player Origin editor, the id is split into a `namespace`
 * (defaults to `neoorigins`) and a `path` segment, joined with `:` at
 * serialize time. The in-game creator pins the namespace to a constant
 * (`neoorigins_custom`); the web editor is intentionally more permissive.
 *
 * Powers reuse the player editor's `PowerDraft` — the power body shaping
 * is identical (the mod's `MobCustomPackSerializer.powerJson` delegates to
 * the player `CustomPackSerializer.powerJson`).
 */
export interface MobOriginDraft {
	/** Datapack namespace (the part before `:`). Defaults to `neoorigins`. */
	namespace: string;
	/** Path segment of the id — the file name under `mob_origins/`. */
	path: string;
	name: string;
	description: string;
	/** Item id used as the browser icon, e.g. `minecraft:zombie_spawn_egg`. */
	icon: string;

	// ── target (set exactly one form) ──────────────────────────────────────
	/** Single exact entity type, e.g. `minecraft:zombie`. */
	targetEntityType: string;
	/** Entity-type tag, e.g. `minecraft:undead`. */
	targetEntityTag: string;
	/** Explicit set of exact types (round-trip / JSON-tab use). */
	targetEntityTypes: string[];

	powers: PowerDraft[];

	// ── spawn rules ────────────────────────────────────────────────────────
	spawnRulesEnabled: boolean;
	weight: number;
	timeOfDay: 'any' | 'day' | 'night';
	mutexGroup: string;
	replace: boolean;
	yRangeEnabled: boolean;
	yRangeMin: number;
	yRangeMax: number;
	lightRangeEnabled: boolean;
	lightRangeMin: number;
	lightRangeMax: number;
	/** Allowed spawn reasons (empty = any). Lowercase enum names. */
	spawnReasons: string[];

	// ── location filter (nested under spawn rules) ─────────────────────────
	locationDimension: string;
	locationBiome: string;
	locationBiomeTag: string;
	locationBiomes: string[];
	locationStructure: string;
	locationStructureTag: string;
	locationAllowWaterSurface: boolean;
	locationAllowOceanFloor: boolean;
	locationMinYEnabled: boolean;
	locationMinY: number;
	locationMaxYEnabled: boolean;
	locationMaxY: number;
	locationCanSeeSky: 'any' | 'true' | 'false';

	// ── drops ──────────────────────────────────────────────────────────────
	dropsEnabled: boolean;
	dropMode: 'additive' | 'replace';
	dropStrategy: 'independent_chance' | 'weighted_pool';
	dropPoolRolls: number;
	dropEntries: DropRow[];
}

/** One per-origin drop. Carries the union of both strategies' fields. */
export interface DropRow {
	item: string;
	countMin: number;
	countMax: number;
	chance: number;
	rolls: number;
	weight: number;
}

/** Default namespace for new drafts. */
export const DEFAULT_NAMESPACE = 'neoorigins';

/** Valid Minecraft namespace characters. */
export const NAMESPACE_PATTERN = /^[a-z0-9_.-]+$/;
/** Valid Minecraft resource-path characters (allows `/` for subfolders). */
export const PATH_PATTERN = /^[a-z0-9_/.-]+$/;
/** Standard Minecraft ResourceLocation regex — `<namespace>:<path>`. */
export const RESOURCE_LOCATION_PATTERN = /^[a-z0-9_.-]+:[a-z0-9_/.-]+$/;

/** Time-of-day options for the spawn-rules gate. */
export const TIME_OF_DAY = ['any', 'day', 'night'] as const;
/** Tristate options for `can_see_sky`. */
export const TRISTATE = ['any', 'true', 'false'] as const;
/** Drop combine modes. */
export const DROP_MODES = ['additive', 'replace'] as const;
/** Drop roll strategies. */
export const DROP_STRATEGIES = ['independent_chance', 'weighted_pool'] as const;

/**
 * Canonical spawn-reason list surfaced in the UI. Hard-coded (not reflected
 * off a running enum) and matches the in-game creator's 1.21.1 list. The
 * schema validates leniently (any lowercase token) so 26.1's differing
 * `EntitySpawnReason` names don't trip false errors.
 */
export const SPAWN_REASONS = [
	'natural',
	'spawner',
	'chunk_generation',
	'breeding',
	'reinforcement',
	'event',
	'spawn_egg',
	'command',
	'structure',
	'bucket',
	'dispenser',
	'mob_summoned',
	'patrol',
	'conversion',
	'jockey',
	'triggered'
] as const;

export function createDropRow(): DropRow {
	return { item: 'minecraft:rotten_flesh', countMin: 1, countMax: 1, chance: 1.0, rolls: 1, weight: 1 };
}

export function createMobDraft(): MobOriginDraft {
	return {
		namespace: DEFAULT_NAMESPACE,
		path: '',
		name: '',
		description: '',
		icon: 'minecraft:zombie_spawn_egg',
		targetEntityType: '',
		targetEntityTag: '',
		targetEntityTypes: [],
		powers: [],
		spawnRulesEnabled: false,
		weight: 0.25,
		timeOfDay: 'any',
		mutexGroup: '',
		replace: false,
		yRangeEnabled: false,
		yRangeMin: -64,
		yRangeMax: 320,
		lightRangeEnabled: false,
		lightRangeMin: 0,
		lightRangeMax: 15,
		spawnReasons: [],
		locationDimension: '',
		locationBiome: '',
		locationBiomeTag: '',
		locationBiomes: [],
		locationStructure: '',
		locationStructureTag: '',
		locationAllowWaterSurface: false,
		locationAllowOceanFloor: false,
		locationMinYEnabled: false,
		locationMinY: -64,
		locationMaxYEnabled: false,
		locationMaxY: 320,
		locationCanSeeSky: 'any',
		dropsEnabled: false,
		dropMode: 'additive',
		dropStrategy: 'independent_chance',
		dropPoolRolls: 1,
		dropEntries: []
	};
}

/** Join `namespace` + `path` into a full resource id (e.g. `neoorigins:wraith`). */
export function fullId(draft: Pick<MobOriginDraft, 'namespace' | 'path'>): string {
	return `${draft.namespace}:${draft.path}`;
}

// ── target Minecraft version (shared with the player editor) ──────────────
//
// Re-exported from originDraft so the mob editor's Identity tab can reuse the
// same toggle + pack_format mapping without duplicating the table.
export {
	targetVersion,
	TARGET_VERSIONS,
	DEFAULT_TARGET_VERSION,
	packFormatFor,
	type TargetMcVersion,
	type TargetVersionOption
} from '$lib/stores/originDraft';

// ── persisted UI state (active tab) ──────────────────────────────────────

export type MobEditorTab = 'identity' | 'spawn' | 'drops' | 'powers' | 'json';
export const KNOWN_TABS: readonly MobEditorTab[] = [
	'identity',
	'spawn',
	'drops',
	'powers',
	'json'
] as const;

export const activeTab = writable<MobEditorTab>('identity');

// ── Powers tab view mode (form ⇄ blocks) ──────────────────────────────────
//
// Reuses the shared `powersView` store from the player editor so the
// form/blocks choice is consistent across both editors within a session.
export { powersView, type PowersView } from '$lib/stores/originDraft';

// ── draft store ────────────────────────────────────────────────────────────

export const draft = writable<MobOriginDraft>(createMobDraft());

export function resetDraft(): void {
	draft.set(createMobDraft());
	activeTab.set('identity');
}

// ── localStorage autosave ────────────────────────────────────────────────
//
// Separate key from the player editor so the two drafts don't collide.

export const STORAGE_KEY = 'neoorigins.mobeditor.draft.v1';
const DEBOUNCE_MS = 300;

interface PersistedShape {
	draft: MobOriginDraft;
	activeTab: MobEditorTab;
}

function isMobEditorTab(v: unknown): v is MobEditorTab {
	return (KNOWN_TABS as readonly string[]).includes(v as string);
}

function looksLikeDraft(d: unknown): d is MobOriginDraft {
	if (!d || typeof d !== 'object') return false;
	const o = d as Record<string, unknown>;
	return (
		typeof o.namespace === 'string' &&
		typeof o.path === 'string' &&
		typeof o.name === 'string' &&
		typeof o.icon === 'string' &&
		typeof o.targetEntityType === 'string' &&
		typeof o.targetEntityTag === 'string' &&
		Array.isArray(o.powers) &&
		Array.isArray(o.spawnReasons) &&
		Array.isArray(o.dropEntries)
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
			// QuotaExceeded / disabled storage — silently drop.
		}
	}, DEBOUNCE_MS);
}

/**
 * Restore the persisted draft / active tab from localStorage if a valid
 * entry exists, then subscribe so subsequent writes autosave (debounced).
 * Idempotent — safe to call from `onMount` even across HMR.
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
					// Merge over a fresh draft so any field added since the blob
					// was written gets its default rather than `undefined`.
					draft.set({ ...createMobDraft(), ...(p.draft as MobOriginDraft) });
				}
				if (isMobEditorTab(p.activeTab)) {
					activeTab.set(p.activeTab);
				}
			}
		}
	} catch {
		// Corrupt / older payload — discard silently and start fresh.
	} finally {
		suppressWrites = false;
	}

	let currentDraft = createMobDraft();
	let currentTab: MobEditorTab = 'identity';

	draft.subscribe((v) => {
		currentDraft = v;
		scheduleSave({ draft: currentDraft, activeTab: currentTab });
	});
	activeTab.subscribe((v) => {
		currentTab = v;
		scheduleSave({ draft: currentDraft, activeTab: currentTab });
	});
}

/**
 * Wipe the persisted draft from localStorage and reload the page so the
 * editor reboots from defaults. Caller is responsible for `confirm()`.
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
		// Ignore — about to reload anyway.
	}
	window.location.reload();
}
