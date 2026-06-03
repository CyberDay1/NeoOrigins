import { writable } from 'svelte/store';
import { browser } from '$app/environment';

/**
 * Global UI preferences: light/dark theme + Blockly colour palette.
 *
 * Both are persisted to localStorage and mirrored onto the <html> element
 * as `data-theme` / `data-palette` attributes, which app.css keys its
 * token overrides on. The SSR-safe `$app/environment.browser` guard
 * mirrors `originDraft.ts` so this is import-safe during prerender.
 *
 * The INITIAL attribute application happens in an inline <script> in
 * app.html (before first paint, to avoid a flash). This module re-reads
 * the same keys on the client and keeps the attribute + store in sync
 * thereafter. Keep the storage keys / attribute names identical to the
 * app.html script.
 */

export type Theme = 'dark' | 'light';
/**
 * Colourblind palette modes. Each repaints BOTH the Blockly category colours
 * (see blockRegistry.ts) AND the UI accent tokens (see app.css), so the choice
 * is visibly effective across the whole editor, not just the canvas.
 *   - default : the standard nebula colours.
 *   - protan  : red-deficient (protanopia) safe set.
 *   - deutan  : green-deficient (deuteranopia) safe set.
 *   - tritan  : blue-deficient (tritanopia) safe set.
 *   - mono    : monochrome — no colour cues; relies on the category glyphs.
 */
export type Palette = 'default' | 'protan' | 'deutan' | 'tritan' | 'mono';

export const THEME_KEY = 'neoorigins.ui.theme';
export const PALETTE_KEY = 'neoorigins.ui.palette';

/**
 * Selectable palettes, in display order, for the labelled nav control. `desc`
 * is surfaced as a title / aria description so each option names the
 * colourblindness type for hover + screen-reader users.
 */
export const PALETTES: { id: Palette; label: string; desc: string }[] = [
	{ id: 'default', label: 'Default', desc: 'Nebula colours' },
	{ id: 'protan', label: 'Protanopia', desc: 'Red-deficient safe palette' },
	{ id: 'deutan', label: 'Deuteranopia', desc: 'Green-deficient safe palette' },
	{ id: 'tritan', label: 'Tritanopia', desc: 'Blue-deficient safe palette' },
	{ id: 'mono', label: 'Monochrome', desc: 'No colour cues — relies on glyphs' }
];

/** Default theme is DARK (matches the `:root` token set). */
const DEFAULT_THEME: Theme = 'dark';
/** Default palette is the standard nebula colours. */
const DEFAULT_PALETTE: Palette = 'default';

function isTheme(v: unknown): v is Theme {
	return v === 'dark' || v === 'light';
}
function isPalette(v: unknown): v is Palette {
	return (
		v === 'default' ||
		v === 'protan' ||
		v === 'deutan' ||
		v === 'tritan' ||
		v === 'mono'
	);
}

/**
 * Read the persisted theme. If the user never chose, returns `null` so the
 * caller can defer to the OS preference (the app.html script does the same
 * — it only sets `data-theme` when an explicit choice exists).
 */
function readStoredTheme(): Theme | null {
	if (!browser) return null;
	try {
		const v = localStorage.getItem(THEME_KEY);
		return isTheme(v) ? v : null;
	} catch {
		return null;
	}
}

function readStoredPalette(): Palette {
	if (!browser) return DEFAULT_PALETTE;
	try {
		const v = localStorage.getItem(PALETTE_KEY);
		// Legacy migration: the old binary toggle stored 'cb-safe' (Okabe-Ito).
		// Map it to the closest granular mode so existing users don't break.
		if (v === 'cb-safe') return 'deutan';
		return isPalette(v) ? v : DEFAULT_PALETTE;
	} catch {
		return DEFAULT_PALETTE;
	}
}

/**
 * Resolve the effective theme for the store's initial value. An explicit
 * stored choice wins; otherwise we default to dark regardless of OS
 * preference (the product defaults to dark — see app.css). This keeps the
 * toggle's reported state in sync with what app.css actually renders.
 */
function initialTheme(): Theme {
	const stored = readStoredTheme();
	if (stored) return stored;
	if (browser) {
		const attr = document.documentElement.getAttribute('data-theme');
		if (isTheme(attr)) return attr;
	}
	return DEFAULT_THEME;
}

export const theme = writable<Theme>(initialTheme());
export const palette = writable<Palette>(readStoredPalette());

/** Apply the theme: persist it, set the explicit `data-theme` attr. */
export function setTheme(next: Theme): void {
	theme.set(next);
	if (!browser) return;
	try {
		localStorage.setItem(THEME_KEY, next);
	} catch {
		// Storage disabled / full — the in-memory + attribute state still works.
	}
	document.documentElement.setAttribute('data-theme', next);
}

export function toggleTheme(): void {
	let current: Theme = DEFAULT_THEME;
	theme.subscribe((v) => (current = v))();
	setTheme(current === 'dark' ? 'light' : 'dark');
}

/** Apply the palette: persist it, set/remove the `data-palette` attr. */
export function setPalette(next: Palette): void {
	palette.set(next);
	if (!browser) return;
	try {
		localStorage.setItem(PALETTE_KEY, next);
	} catch {
		// Ignore — non-fatal.
	}
	if (next === 'default') {
		document.documentElement.removeAttribute('data-palette');
	} else {
		document.documentElement.setAttribute('data-palette', next);
	}
}
