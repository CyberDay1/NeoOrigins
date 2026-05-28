<script lang="ts">
	import { base } from '$app/paths';
	import favicon from '$lib/assets/favicon.svg';

	let { children } = $props();

	// Docs site lives at cyberday1.github.io/NeoOrigins/ (mkdocs).
	// Hard-coded sibling URL so it points to the real docs in production
	// without needing the editor's own base path.
	const docsHref = 'https://cyberday1.github.io/NeoOrigins/';
</script>

<svelte:head>
	<title>NeoOrigins Editor</title>
	<link rel="icon" href={favicon} />
</svelte:head>

<!--
	TODO(branding): swap this nav skeleton for the in-game `CreatorStyle` palette
	(dark panel + accent stripe) once the hex values are pulled from the
	NeoOrigins Java source. Tracked in planning/web_editor_scope.md open questions.
-->
<header class="nav">
	<a class="brand" href="{base}/">NeoOrigins Editor</a>
	<nav>
		<a href="{base}/">Home</a>
		<a href="{base}/editor/origin/">Origin editor</a>
		<a href={docsHref} target="_blank" rel="noopener noreferrer">Docs</a>
	</nav>
</header>

<main>
	{@render children()}
</main>

<style>
	:global(body) {
		margin: 0;
		font-family: system-ui, -apple-system, Segoe UI, sans-serif;
		background: #1a1a1a;
		color: #e6e6e6;
	}
	.nav {
		display: flex;
		align-items: center;
		justify-content: space-between;
		padding: 0.75rem 1.25rem;
		background: #111;
		border-bottom: 2px solid #4a90e2;
	}
	.brand {
		font-weight: 600;
		text-decoration: none;
		color: #e6e6e6;
	}
	nav a {
		margin-left: 1rem;
		color: #b8b8b8;
		text-decoration: none;
	}
	nav a:hover {
		color: #fff;
	}
	main {
		padding: 1.5rem;
		max-width: 960px;
		margin: 0 auto;
	}
</style>
