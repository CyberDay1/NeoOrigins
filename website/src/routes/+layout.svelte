<script lang="ts">
	import '../app.css';
	import { base } from '$app/paths';
	import { page } from '$app/state';
	import favicon from '$lib/assets/favicon.svg';
	import { theme, palette, toggleTheme, setPalette, PALETTES } from '$lib/stores/themeStore';

	let { children } = $props();

	// Labelled colourblind-palette control: a button trigger that opens a small
	// popup menu. Each option is a real <button role="menuitemradio"> naming the
	// colourblindness type (title + aria-label from PALETTES.desc) with
	// aria-checked reflecting the active mode. Replaces the old binary toggle so
	// the choice is discoverable and screen-reader legible.
	let paletteOpen = $state(false);
	let paletteWrap = $state<HTMLDivElement | null>(null);

	const activePalette = $derived(
		PALETTES.find((p) => p.id === $palette) ?? PALETTES[0]
	);

	function choosePalette(id: typeof PALETTES[number]['id']): void {
		setPalette(id);
		paletteOpen = false;
	}

	function onWindowKeydown(e: KeyboardEvent): void {
		if (e.key === 'Escape' && paletteOpen) {
			paletteOpen = false;
		}
	}

	function onWindowPointerdown(e: PointerEvent): void {
		if (paletteOpen && paletteWrap && !paletteWrap.contains(e.target as Node)) {
			paletteOpen = false;
		}
	}

	// Docs site lives at cyberday1.github.io/NeoOrigins/ (mkdocs).
	// Hard-coded sibling URL so it points to the real docs in production
	// without needing the editor's own base path.
	const docsHref = 'https://cyberday1.github.io/NeoOrigins/';

	// Active-nav highlighting — compare against the current pathname.
	const homeHref = `${base}/`;
	const editorHref = `${base}/editor/origin/`;

	function isActive(href: string): boolean {
		// page.url is set both client- and server-side under SvelteKit.
		const path = page.url?.pathname ?? '';
		if (href === homeHref) {
			return path === homeHref || path === `${base}` || path === `${base}/`;
		}
		return path.startsWith(href);
	}
</script>

<svelte:window onpointerdown={onWindowPointerdown} onkeydown={onWindowKeydown} />

<svelte:head>
	<title>NeoOrigins Editor</title>
	<link rel="icon" href={favicon} />
</svelte:head>

<header class="nav">
	<div class="nav-inner">
		<a class="brand" href={homeHref}>
			<svg class="brand-mark" viewBox="0 0 24 24" fill="none" aria-hidden="true">
				<path
					class="edge"
					d="M6 12 L18 6 M6 12 L18 12 M6 12 L18 18"
					stroke="currentColor"
					stroke-width="1.5"
					stroke-linecap="round"
				/>
				<circle class="node-src" cx="6" cy="12" r="2.7" fill="currentColor" />
				<circle class="node" cx="18" cy="6" r="2.1" />
				<circle class="node" cx="18" cy="12" r="2.1" />
				<circle class="node" cx="18" cy="18" r="2.1" />
			</svg>
			<span class="brand-name">NeoOrigins<span class="brand-suffix">Editor</span></span>
		</a>
		<nav aria-label="Primary">
			<a class="nav-link" class:active={isActive(homeHref)} href={homeHref}>Home</a>
			<a class="nav-link" class:active={isActive(editorHref)} href={editorHref}>Origin Editor</a>
			<a
				class="nav-link external"
				href={docsHref}
				target="_blank"
				rel="noopener noreferrer"
			>
				Docs
				<span class="ext-icon" aria-hidden="true">↗</span>
			</a>
			<div class="nav-controls">
				<button
					type="button"
					class="ctl-btn"
					aria-pressed={$theme === 'light'}
					aria-label={$theme === 'dark'
						? 'Switch to light theme'
						: 'Switch to dark theme'}
					title={$theme === 'dark' ? 'Switch to light theme' : 'Switch to dark theme'}
					onclick={toggleTheme}
				>
					<span class="ctl-glyph" aria-hidden="true">{$theme === 'dark' ? '☾' : '☀'}</span>
					<span class="ctl-text">{$theme === 'dark' ? 'Dark' : 'Light'}</span>
				</button>
				<div class="palette-menu" bind:this={paletteWrap}>
					<button
						type="button"
						class="ctl-btn"
						class:active={$palette !== 'default'}
						aria-haspopup="menu"
						aria-expanded={paletteOpen}
						aria-label={`Colour palette: ${activePalette.label} — ${activePalette.desc}. Activate to choose a colourblind-safe palette.`}
						title={`Colour palette: ${activePalette.label} — ${activePalette.desc}`}
						onclick={() => (paletteOpen = !paletteOpen)}
					>
						<span class="ctl-glyph" aria-hidden="true">◑</span>
						<span class="ctl-text">{activePalette.label}</span>
						<span class="ctl-caret" aria-hidden="true">▾</span>
					</button>
					{#if paletteOpen}
						<div class="palette-popup" role="menu" aria-label="Colour palette">
							{#each PALETTES as p (p.id)}
								<button
									type="button"
									class="palette-item"
									class:selected={$palette === p.id}
									role="menuitemradio"
									aria-checked={$palette === p.id}
									aria-label={`${p.label} — ${p.desc}`}
									title={`${p.label} — ${p.desc}`}
									onclick={() => choosePalette(p.id)}
								>
									<span class="palette-check" aria-hidden="true"
										>{$palette === p.id ? '✓' : ''}</span
									>
									<span class="palette-label">{p.label}</span>
									<span class="palette-desc">{p.desc}</span>
								</button>
							{/each}
						</div>
					{/if}
				</div>
			</div>
		</nav>
	</div>
</header>

<main>
	<div class="container">
		{@render children()}
	</div>
</main>

<style>
	.nav {
		position: sticky;
		top: 0;
		z-index: 10;
		background: color-mix(in srgb, var(--color-bg) 92%, transparent);
		backdrop-filter: saturate(140%) blur(10px);
		-webkit-backdrop-filter: saturate(140%) blur(10px);
		border-bottom: 1px solid var(--color-border);
	}
	.nav-inner {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		padding: var(--space-3) var(--space-5);
		max-width: 1180px;
		margin: 0 auto;
	}
	.brand {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		font-family: var(--font-display);
		font-weight: 700;
		font-size: 1.02rem;
		letter-spacing: -0.01em;
		text-decoration: none;
		color: var(--color-text);
	}
	.brand-mark {
		width: 22px;
		height: 22px;
		flex: none;
		color: var(--color-accent);
	}
	.brand-mark .edge {
		opacity: 0.5;
	}
	.brand-mark .node {
		fill: var(--color-bg);
		stroke: currentColor;
		stroke-width: 1.5;
	}
	.brand-name {
		display: inline-flex;
		gap: 0.35rem;
		align-items: baseline;
	}
	.brand-suffix {
		color: var(--color-text-muted);
		font-weight: 500;
	}
	nav {
		display: flex;
		align-items: center;
		gap: var(--space-1);
	}
	.nav-link {
		position: relative;
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		padding: 0.5rem 0.85rem;
		border-radius: var(--radius-md);
		color: var(--color-text-muted);
		font-size: 0.88rem;
		font-weight: 500;
		text-decoration: none;
		transition: color 120ms ease, background 120ms ease;
	}
	.nav-link:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
	}
	.nav-link.active {
		color: var(--color-text);
		background: var(--color-accent-subtle);
	}
	.ext-icon {
		font-size: 0.75rem;
		opacity: 0.7;
	}
	.nav-controls {
		display: inline-flex;
		align-items: center;
		gap: var(--space-1);
		margin-left: var(--space-2);
		padding-left: var(--space-2);
		border-left: 1px solid var(--color-border);
	}
	.ctl-btn {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		padding: 0.4rem 0.7rem;
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		font: inherit;
		font-size: 0.82rem;
		font-weight: 500;
		cursor: pointer;
		transition: color 120ms ease, background 120ms ease, border-color 120ms ease;
	}
	.ctl-btn:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
		border-color: var(--color-border-strong);
	}
	.ctl-btn[aria-pressed='true'],
	.ctl-btn.active {
		color: var(--color-text);
		background: var(--color-accent-subtle);
		border-color: color-mix(in srgb, var(--color-accent) 45%, var(--color-border));
	}
	.ctl-glyph {
		font-size: 0.95rem;
		line-height: 1;
	}
	.ctl-caret {
		font-size: 0.7rem;
		line-height: 1;
		opacity: 0.7;
	}
	/* ─── palette dropdown ─── */
	.palette-menu {
		position: relative;
		display: inline-flex;
	}
	.palette-popup {
		position: absolute;
		top: calc(100% + 6px);
		right: 0;
		z-index: 20;
		min-width: 232px;
		display: flex;
		flex-direction: column;
		padding: var(--space-1);
		background: var(--color-bg-elevated);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		box-shadow: var(--shadow-md);
	}
	.palette-item {
		display: grid;
		grid-template-columns: 1.1rem 1fr;
		align-items: baseline;
		gap: 0 0.5rem;
		width: 100%;
		padding: 0.4rem 0.55rem;
		background: transparent;
		color: var(--color-text-muted);
		border: none;
		border-radius: var(--radius-sm);
		font: inherit;
		font-size: 0.82rem;
		text-align: left;
		cursor: pointer;
		transition: color 120ms ease, background 120ms ease;
	}
	.palette-item:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
	}
	.palette-item.selected {
		color: var(--color-text);
		background: var(--color-accent-subtle);
	}
	.palette-check {
		grid-column: 1;
		color: var(--color-accent);
		font-size: 0.8rem;
		line-height: 1.3;
	}
	.palette-label {
		grid-column: 2;
		font-weight: 600;
	}
	.palette-desc {
		grid-column: 2;
		color: var(--color-text-subtle);
		font-size: 0.74rem;
	}
	@media (max-width: 600px) {
		.ctl-text {
			display: none;
		}
	}
	main {
		min-height: calc(100vh - 56px);
	}
	.container {
		max-width: 1180px;
		margin: 0 auto;
		padding: var(--space-5) var(--space-5) var(--space-7);
	}
	@media (max-width: 600px) {
		.nav-inner {
			padding: var(--space-3) var(--space-4);
		}
		.brand-suffix {
			display: none;
		}
		.container {
			padding: var(--space-4);
		}
	}
</style>
