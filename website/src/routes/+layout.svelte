<script lang="ts">
	import '../app.css';
	import { base } from '$app/paths';
	import { page } from '$app/state';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();

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

<svelte:head>
	<title>NeoOrigins Editor</title>
	<link rel="icon" href={favicon} />
</svelte:head>

<header class="nav">
	<div class="nav-inner">
		<a class="brand" href={homeHref}>
			<span class="brand-mark" aria-hidden="true"></span>
			<span class="brand-name">NeoOrigins<span class="brand-suffix">Editor</span></span>
		</a>
		<nav aria-label="Primary">
			<a class="nav-link" class:active={isActive(homeHref)} href={homeHref}>Home</a>
			<a class="nav-link" class:active={isActive(editorHref)} href={editorHref}>Origin editor</a>
			<a
				class="nav-link external"
				href={docsHref}
				target="_blank"
				rel="noopener noreferrer"
			>
				Docs
				<span class="ext-icon" aria-hidden="true">↗</span>
			</a>
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
		width: 18px;
		height: 18px;
		border-radius: var(--radius-sm);
		background: linear-gradient(
			135deg,
			var(--color-accent) 0%,
			var(--color-accent-2) 100%
		);
		box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 50%, transparent),
			0 0 12px color-mix(in srgb, var(--color-accent) 35%, transparent);
		animation: mark-pulse 4.5s ease-in-out infinite;
	}
	@keyframes mark-pulse {
		0%,
		100% {
			box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 50%, transparent),
				0 0 12px color-mix(in srgb, var(--color-accent) 35%, transparent);
		}
		50% {
			box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-accent) 70%, transparent),
				0 0 20px color-mix(in srgb, var(--color-accent) 55%, transparent);
		}
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
