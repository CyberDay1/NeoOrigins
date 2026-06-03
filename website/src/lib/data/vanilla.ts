// Bundled vanilla Minecraft suggestion data for the editor's typeahead inputs.
//
// The JSON files under ./vanilla/ are generated from the client jars by
// `scripts/gen-vanilla-data.mjs`. They drive autocomplete only — every field
// still accepts free text, so the lists need not be exhaustive.
//
// Loading is lazy + cached per version: the (large) item list isn't pulled into
// the bundle until something actually asks for that version's data.

import { writable } from 'svelte/store';
import type { TargetMcVersion } from '$lib/stores/originDraft';

export interface VanillaData {
	minecraftVersion: string;
	entities: string[];
	items: string[];
	biomes: string[];
	biomeTags: string[];
	structures: string[];
	structureTags: string[];
	entityTags: string[];
	dimensions: string[];
}

const EMPTY: VanillaData = {
	minecraftVersion: '',
	entities: [],
	items: [],
	biomes: [],
	biomeTags: [],
	structures: [],
	structureTags: [],
	entityTags: [],
	dimensions: []
};

const loaders: Record<TargetMcVersion, () => Promise<{ default: VanillaData }>> = {
	'1.21.1': () => import('./vanilla/1.21.1.json'),
	'26.1': () => import('./vanilla/26.1.json')
};

const cache = new Map<TargetMcVersion, VanillaData>();

/** Loads (and caches) the suggestion data for a target version. */
export async function loadVanillaData(version: TargetMcVersion): Promise<VanillaData> {
	const hit = cache.get(version);
	if (hit) return hit;
	const mod = await loaders[version]();
	const data = mod.default;
	cache.set(version, data);
	return data;
}

/** Synchronous cache read — returns empty lists until `loadVanillaData` resolves. */
export function vanillaDataSync(version: TargetMcVersion): VanillaData {
	return cache.get(version) ?? EMPTY;
}

/**
 * Reactive suggestion data for the active target version. Components subscribe to
 * `vanilla` and call `ensureVanilla($targetVersion)` (in an `$effect`) to trigger
 * the lazy load; the store updates to the loaded data when it resolves.
 */
export const vanilla = writable<VanillaData>(EMPTY);

let lastRequested: TargetMcVersion | null = null;

export function ensureVanilla(version: TargetMcVersion): void {
	const hit = vanillaDataSync(version);
	if (hit.minecraftVersion) {
		vanilla.set(hit);
		lastRequested = version;
		return;
	}
	if (lastRequested === version) return;
	lastRequested = version;
	loadVanillaData(version).then((d) => {
		// Guard against a fast version switch resolving out of order.
		if (lastRequested === version) vanilla.set(d);
	});
}
