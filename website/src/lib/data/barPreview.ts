// What the hud_render preview swatch draws, worked out from the sub-form's value.
// Mirrors the framed-bar path of the in-game `ResourceHudOverlay.renderFramedBar`
// and the colour rule of `util/HexColor`. Pure, so `check:barstyles` can test it.

/** One built-in bar style, copied from `assets/neoorigins/bar_fx/<name>.json`. */
export interface BarStyle {
	/** Strip under `static/bar_fx/`. */
	texture: string;
	tileW: number;
	/** Texels per scroll loop. */
	loopW: number;
	/** Texels per second the strip drifts left; 0 = static. */
	scrollSpeed: number;
	/** With no tint, multiply by the bar's `color`. */
	tintFromColor: boolean;
}

/** Frame and fill window, shared by every built-in style. */
export const FRAME = { texture: 'empty_bar.png', w: 71, h: 8 } as const;
export const WINDOW = { x: 1, y: 1, w: FRAME.w - 2, h: FRAME.h - 2 } as const;

const scrolling = (name: string, scrollSpeed: number, loopW = 125): BarStyle => ({
	texture: `${name}.png`,
	tileW: 126,
	loopW,
	scrollSpeed,
	tintFromColor: false
});

/** `check:barstyles` compares this table against the shipped preset files. */
export const BAR_STYLE_PRESETS: Record<string, BarStyle> = {
	'neoorigins:fire': scrolling('fire', 16),
	'neoorigins:water': scrolling('water', 10),
	'neoorigins:wind': scrolling('wind', 24),
	'neoorigins:earth': scrolling('earth', 6),
	'neoorigins:soul': scrolling('soul', 8),
	'neoorigins:void': scrolling('void', 10),
	'neoorigins:sculk': scrolling('sculk', 12, 126),
	'neoorigins:tinted': {
		texture: 'grayscale.png',
		tileW: 69,
		loopW: 69,
		scrollSpeed: 0,
		tintFromColor: true
	}
};

export const DEFAULT_COLOR = 0xff55aaff;

/** `RRGGBB` or `AARRGGBB` with an optional `#`, as ARGB; `null` for anything else. */
export function parseHex(s: string): number | null {
	const hex = s.startsWith('#') ? s.slice(1) : s;
	if ((hex.length !== 6 && hex.length !== 8) || !/^[0-9A-Fa-f]+$/.test(hex)) return null;
	const v = parseInt(hex, 16);
	return hex.length === 6 ? (0xff000000 | v) >>> 0 : v >>> 0;
}

/** The loader's reading of a colour field: blank or absent = `unset`, malformed = #55AAFF. */
function readColor(v: unknown, unset: number | null): number | null {
	if (typeof v !== 'string' || v.trim() === '') return unset;
	return parseHex(v.trim()) ?? DEFAULT_COLOR;
}

export type BarPlan =
	| { kind: 'flat'; color: number; customStyle: string | null }
	| {
			kind: 'style';
			style: BarStyle;
			/** RGB multiplied over the art, or null for the art as drawn. */
			tint: number | null;
			/** Right-edge colour of a static fill's gradient, or null for one colour. */
			tintEnd: number | null;
	  };

/**
 * What the HUD would draw for this `hud_render` value. A style id this editor does
 * not ship (a pack's own) is drawn as the flat `color` fill, which is also what the
 * HUD shows while that style is not loaded.
 */
export function planBar(hud: Record<string, unknown> | null | undefined): BarPlan {
	const h = hud ?? {};
	const color = readColor(h.color, DEFAULT_COLOR) ?? DEFAULT_COLOR;
	const id = typeof h.animated === 'string' ? h.animated.trim() : '';
	const style = id ? BAR_STYLE_PRESETS[id] : undefined;
	if (!style) return { kind: 'flat', color, customStyle: id || null };

	// A fully transparent tint (#00000000) reads as "no tint", as on the HUD.
	const tintRaw = readColor(h.tint, 0) ?? 0;
	const tint = tintRaw !== 0 ? tintRaw : style.tintFromColor ? color : null;
	const endRaw = readColor(h.tint_end, 0) ?? 0;
	const tintEnd = style.scrollSpeed === 0 && endRaw !== 0 ? endRaw : null;
	return { kind: 'style', style, tint, tintEnd };
}

/** Opaque RGB at column `col` of a `width`-wide track: `ResourceBarGradient.at`. */
export function gradientAt(start: number, end: number, col: number, width: number): number {
	if (width <= 1) return start & 0xffffff;
	const c = Math.max(0, Math.min(width - 1, col));
	const t = c / (width - 1);
	const ch = (shift: number) => {
		const a = (start >> shift) & 0xff;
		const b = (end >> shift) & 0xff;
		return a + Math.round((b - a) * t);
	};
	return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

/** `#rrggbb` for a canvas fill. */
export function cssRgb(argb: number): string {
	return `#${(argb & 0xffffff).toString(16).padStart(6, '0')}`;
}
