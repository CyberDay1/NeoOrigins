<script lang="ts">
	// Modal picker for the "Import power" button. Lists every shipped built-in
	// power, filterable by name / id / type. Selecting one hands the entry back
	// to the caller, which deep-copies it into a new editable PowerDraft.
	// Portaled to <body> so the fixed overlay isn't trapped by any transformed
	// editor ancestor. Modeled on VanillaTemplatePicker.svelte.

	import {
		loadBuiltinPowerCatalog,
		type BuiltinPowerEntry
	} from '$lib/datapack/builtinPowers';

	let {
		open = false,
		onselect,
		onclose
	}: {
		open?: boolean;
		onselect: (entry: BuiltinPowerEntry) => void;
		onclose: () => void;
	} = $props();

	let entries = $state<BuiltinPowerEntry[] | null>(null);
	let error = $state('');
	let loading = $state(false);
	let query = $state('');
	let searchEl = $state<HTMLInputElement>();

	// Fetch the catalog the first time the modal opens; focus the search box.
	$effect(() => {
		if (!open) return;
		query = '';
		if (!entries && !loading) {
			loading = true;
			error = '';
			loadBuiltinPowerCatalog()
				.then((c) => (entries = c.entries))
				.catch((e) => (error = e instanceof Error ? e.message : String(e)))
				.finally(() => (loading = false));
		}
		// Defer focus until the dialog is in the DOM.
		queueMicrotask(() => searchEl?.focus());
	});

	// Cap the rendered list so 820 rows don't all mount at once; the count line
	// tells the user to narrow their search when there are more matches.
	const RENDER_CAP = 200;

	let filtered = $derived.by(() => {
		const all = entries ?? [];
		const q = query.trim().toLowerCase();
		if (!q) return all;
		return all.filter(
			(e) =>
				e.name.toLowerCase().includes(q) ||
				e.id.toLowerCase().includes(q) ||
				e.type.toLowerCase().includes(q)
		);
	});

	let shown = $derived(filtered.slice(0, RENDER_CAP));

	function pick(entry: BuiltinPowerEntry) {
		onselect(entry);
	}

	function onKeydown(e: KeyboardEvent) {
		if (e.key === 'Escape') {
			e.stopPropagation();
			onclose();
		}
	}

	// Move the overlay to <body> so `position: fixed` resolves to the viewport
	// even under a transformed ancestor.
	function portal(node: HTMLElement) {
		document.body.appendChild(node);
		return {
			destroy() {
				node.remove();
			}
		};
	}
</script>

{#if open}
	<!-- svelte-ignore a11y_no_noninteractive_element_interactions -->
	<div
		use:portal
		class="overlay"
		role="dialog"
		aria-modal="true"
		aria-label="Import built-in power"
		tabindex="-1"
		onkeydown={onKeydown}
	>
		<!-- Backdrop: click to dismiss. Keyboard users use Esc / the close button. -->
		<!-- svelte-ignore a11y_click_events_have_key_events -->
		<!-- svelte-ignore a11y_no_static_element_interactions -->
		<div class="backdrop" onclick={onclose}></div>

		<div class="panel">
			<header class="panel-head">
				<div class="titles">
					<h2>Import power</h2>
					<p>Copy any shipped built-in power in as an editable power.</p>
				</div>
				<button type="button" class="close" aria-label="Close" onclick={onclose}>×</button>
			</header>

			<div class="search">
				<input
					bind:this={searchEl}
					type="text"
					placeholder="Search powers by name, id, or type…"
					aria-label="Filter built-in powers"
					bind:value={query}
					autocomplete="off"
					spellcheck="false"
				/>
			</div>

			<div class="body">
				{#if loading}
					<p class="status">Loading powers…</p>
				{:else if error}
					<p class="status error">Couldn't load powers: {error}</p>
				{:else if filtered.length === 0}
					<p class="status">No powers match “{query}”.</p>
				{:else}
					<p class="count-line">
						{filtered.length} match{filtered.length === 1 ? '' : 'es'}{filtered.length > RENDER_CAP
							? ` — showing first ${RENDER_CAP}, narrow your search`
							: ''}
					</p>
					<ul>
						{#each shown as entry (entry.id)}
							<li>
								<button type="button" class="row" onclick={() => pick(entry)}>
									<span class="name">{entry.name}</span>
									<span class="id">{entry.id}</span>
									{#if entry.type}
										<span class="type">{entry.type}</span>
									{/if}
								</button>
							</li>
						{/each}
					</ul>
				{/if}
			</div>
		</div>
	</div>
{/if}

<style>
	.overlay {
		position: fixed;
		inset: 0;
		z-index: 80;
		display: flex;
		align-items: center;
		justify-content: center;
		padding: var(--space-4);
	}
	.backdrop {
		position: absolute;
		inset: 0;
		background: rgb(0 0 0 / 0.55);
		backdrop-filter: blur(2px);
	}
	.panel {
		position: relative;
		display: flex;
		flex-direction: column;
		width: min(46rem, 100%);
		max-height: min(80vh, 44rem);
		background: var(--color-surface);
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-lg, 0 16px 48px rgb(0 0 0 / 0.4));
		overflow: hidden;
	}
	.panel-head {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: var(--space-3);
		padding: var(--space-4) var(--space-4) var(--space-3);
		border-bottom: 1px solid var(--color-border);
	}
	.titles h2 {
		margin: 0;
		font-size: 1rem;
		font-weight: 600;
		color: var(--color-text);
	}
	.titles p {
		margin: 0.2rem 0 0;
		font-size: 0.82rem;
		color: var(--color-text-muted);
	}
	.close {
		flex-shrink: 0;
		width: 2rem;
		height: 2rem;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		background: var(--color-bg-subtle);
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		font-size: 1.3rem;
		line-height: 1;
		cursor: pointer;
		transition: border-color 120ms ease, color 120ms ease;
	}
	.close:hover {
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
	.search {
		padding: var(--space-3) var(--space-4);
		border-bottom: 1px solid var(--color-border);
	}
	.search input {
		width: 100%;
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.5rem 0.65rem;
		font: inherit;
		font-size: 0.88rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	.search input:focus {
		outline: none;
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	.body {
		padding: var(--space-3) var(--space-4) var(--space-4);
		overflow-y: auto;
	}
	.status {
		margin: 0;
		padding: var(--space-4) 0;
		text-align: center;
		color: var(--color-text-muted);
		font-size: 0.85rem;
	}
	.status.error {
		color: var(--color-danger);
	}
	.count-line {
		margin: 0 0 var(--space-2);
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-subtle);
	}
	.body ul {
		list-style: none;
		margin: 0;
		padding: 0;
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(13rem, 1fr));
		gap: var(--space-2);
	}
	.row {
		display: flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 0.1rem;
		width: 100%;
		text-align: left;
		padding: 0.5rem 0.65rem;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		cursor: pointer;
		transition: border-color 120ms ease, background 120ms ease;
	}
	.row:hover {
		border-color: var(--color-accent);
		background: var(--color-surface-hover);
	}
	.row:focus-visible {
		outline: none;
		border-color: var(--color-accent);
		box-shadow: 0 0 0 2px var(--color-accent-subtle);
	}
	.name {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--color-text);
	}
	.id {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		color: var(--color-text-subtle);
	}
	.type {
		margin-top: 0.2rem;
		font-family: var(--font-mono);
		font-size: 0.66rem;
		color: var(--color-text-muted);
	}
</style>
