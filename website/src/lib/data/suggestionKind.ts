// Maps a schema field's trailing JSON key to one of the bundled vanilla
// suggestion lists (see `vanilla.ts`). This is how the schema-driven player
// power form (StringRow / ArrayStringRow) gets the same typeahead the mob
// editor wires up by hand: the field names are stable across the power /
// action / condition schemas (`entity_type`, `biome`, `item`, …), so a name
// lookup is enough — no schema-metadata round-trip through the Java generator.
//
// Suggestions are hints only; every field stays free-text, so modded/datapack
// ids that aren't in the vanilla list still type fine. Kinds we have no
// authoritative vanilla list for (blocks, status effects) are intentionally
// left unmapped rather than guessing.

import type { VanillaData } from './vanilla';

/** A suggestion-bearing list on {@link VanillaData}. */
export type SuggestionField = Exclude<keyof VanillaData, 'minecraftVersion'>;

// Trailing JSON keys → which vanilla list to suggest. Both singular and plural
// (array) forms map to the same list — the array list rows reuse one datalist.
const NAME_MAP: Record<string, SuggestionField> = {
	entity: 'entities',
	entity_type: 'entities',
	entity_types: 'entities',
	biome: 'biomes',
	biomes: 'biomes',
	structure: 'structures',
	structures: 'structures',
	dimension: 'dimensions',
	dimensions: 'dimensions',
	item: 'items',
	items: 'items'
};

/** The vanilla list key for a field name, or `null` when none applies. */
export function suggestionFieldFor(name: string): SuggestionField | null {
	return NAME_MAP[name.toLowerCase()] ?? null;
}

/**
 * The built-in resource bar styles, one per preset under
 * `src/main/resources/assets/neoorigins/bar_fx/`. Offered on `hud_render.animated`;
 * a pack's own style id still types fine. `npm run check:barstyles` fails when
 * this list and the preset folder disagree.
 */
export const BAR_STYLES: readonly string[] = [
	'neoorigins:fire',
	'neoorigins:water',
	'neoorigins:wind',
	'neoorigins:earth',
	'neoorigins:soul',
	'neoorigins:void',
	'neoorigins:sculk',
	'neoorigins:tinted'
];

// Trailing JSON keys with a fixed NeoOrigins list rather than a vanilla one.
const STATIC_MAP: Record<string, readonly string[]> = {
	animated: BAR_STYLES
};

/** The suggestion strings for a field name, given loaded vanilla data ([] if none). */
export function suggestionsFor(name: string, data: VanillaData): string[] {
	const fixed = STATIC_MAP[name.toLowerCase()];
	if (fixed) return [...fixed];
	const key = suggestionFieldFor(name);
	return key ? data[key] : [];
}
