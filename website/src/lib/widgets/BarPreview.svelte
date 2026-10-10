<script lang="ts">
	// Preview swatch for a resource power's `hud_render`: the chosen bar style with
	// its tint / tint_end, drawn from the same textures the mod ships, at 3/4 full.
	// The drawing rules live in `$lib/data/barPreview` (tested by check:barstyles).

	import { base } from '$app/paths';
	import { FRAME, WINDOW, cssRgb, gradientAt, planBar } from '$lib/data/barPreview';

	let { value }: { value: Record<string, unknown> | null } = $props();

	const SCALE = 4;
	const LEVEL = 0.75;

	const plan = $derived(planBar(value));

	let canvas: HTMLCanvasElement | undefined = $state();
	let loaded = $state(0);
	const images = new Map<string, HTMLImageElement>();

	function image(name: string): HTMLImageElement | null {
		let img = images.get(name);
		if (!img) {
			img = new Image();
			img.onload = () => loaded++;
			img.src = `${base}/bar_fx/${name}`;
			images.set(name, img);
		}
		return img.complete && img.naturalWidth > 0 ? img : null;
	}

	const fillW = Math.round(WINDOW.w * LEVEL);
	let scratch: HTMLCanvasElement | null = null;

	function draw(offset: number) {
		const ctx = canvas?.getContext('2d');
		if (!ctx) return;
		ctx.imageSmoothingEnabled = false;
		ctx.clearRect(0, 0, FRAME.w, FRAME.h);
		const p = plan;

		if (p.kind === 'flat') {
			// The HUD's plain bar: a dark track with the color fill.
			ctx.fillStyle = 'rgba(0, 0, 0, 0.67)';
			ctx.fillRect(WINDOW.x, WINDOW.y, WINDOW.w, WINDOW.h);
			ctx.fillStyle = cssRgb(p.color);
			ctx.globalAlpha = ((p.color >>> 24) & 0xff) / 255;
			ctx.fillRect(WINDOW.x, WINDOW.y, fillW, WINDOW.h);
			ctx.globalAlpha = 1;
			return;
		}

		const frame = image(FRAME.texture);
		const strip = image(p.style.texture);
		if (frame) ctx.drawImage(frame, 0, 0);
		if (!strip) return;

		scratch ??= document.createElement('canvas');
		scratch.width = WINDOW.w;
		scratch.height = WINDOW.h;
		const s = scratch.getContext('2d');
		if (!s) return;
		s.imageSmoothingEnabled = false;
		const lay = () => {
			for (let dx = -offset; dx < fillW; dx += p.style.loopW) {
				s.drawImage(strip, 0, 0, p.style.loopW, WINDOW.h, dx, 0, p.style.loopW, WINDOW.h);
			}
		};
		s.save();
		s.beginPath();
		s.rect(0, 0, fillW, WINDOW.h);
		s.clip();
		lay();
		if (p.tint !== null || p.tintEnd !== null) {
			// Multiply the art by the tint, then cut back to the art's own alpha.
			s.globalCompositeOperation = 'multiply';
			const start = p.tint ?? 0xffffff;
			for (let c = 0; c < fillW; c++) {
				s.fillStyle = cssRgb(p.tintEnd !== null ? gradientAt(start, p.tintEnd, c, WINDOW.w) : start);
				s.fillRect(c, 0, 1, WINDOW.h);
			}
			s.globalCompositeOperation = 'destination-in';
			lay();
		}
		s.restore();
		ctx.drawImage(scratch, WINDOW.x, WINDOW.y);
	}

	$effect(() => {
		void loaded;
		const p = plan;
		if (!canvas) return;
		const speed = p.kind === 'style' ? p.style.scrollSpeed : 0;
		const still =
			speed === 0 || window.matchMedia('(prefers-reduced-motion: reduce)').matches;
		if (still) {
			draw(0);
			return;
		}
		let raf = 0;
		const tick = (t: number) => {
			const loop = p.kind === 'style' ? p.style.loopW : 1;
			draw(Math.floor(((t / 1000) * speed) % loop));
			raf = requestAnimationFrame(tick);
		};
		raf = requestAnimationFrame(tick);
		return () => cancelAnimationFrame(raf);
	});

	const caption = $derived(
		plan.kind === 'flat'
			? plan.customStyle
				? `${plan.customStyle} is not a built-in style; shown as its color fill`
				: 'flat color fill'
			: 'preview at 3/4 full'
	);
</script>

<div class="preview">
	<canvas
		bind:this={canvas}
		width={FRAME.w}
		height={FRAME.h}
		style:width={`${FRAME.w * SCALE}px`}
		aria-hidden="true"
	></canvas>
	<small class="cap">Bar preview: {caption}</small>
</div>

<style>
	.preview {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		flex-wrap: wrap;
		padding: 0.25rem 0;
	}
	canvas {
		image-rendering: pixelated;
		max-width: 100%;
		height: auto;
		aspect-ratio: 71 / 8;
	}
	.cap {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
</style>
