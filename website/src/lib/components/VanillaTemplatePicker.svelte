<script lang="ts">
	// Modal picker for the "Load vanilla template" button. Lists the shipped
	// origins and classes (split into two groups), filterable by name or id.
	// Selecting one hands the entry + manifest back to the caller, which runs
	// it through `loadTemplate`. Portaled to <body> so the fixed overlay isn't
	// trapped by any transformed editor ancestor.

	import {
		loadTemplateIndex,
		type TemplateEntry
	} from '$lib/datapack/vanillaTemplates';

	let {
		open = false,
		onselect,
		onclose
	}: {
		open?: boolean;
		onselect: (entry: TemplateEntry, manifest: Awaited<ReturnType<typeof loadTemplateIndex>>) => void;
		onclose: () => void;
	} = $props();

	let manifest = $state<Awaited<ReturnType<typeof loadTemplateIndex>> | null>(null);
	let error = $state('');
	let loading = $state(false);
	let query = $state('');
	let searchEl = $state<HTMLInputElement>();

	// Fetch the index the first time the modal opens; focus the search box.
	$effect(() => {
		if (!open) return;
		query = '';
		if (!manifest && !loading) {
			loading = true;
			error = '';
			loadTemplateIndex()
				.then((m) => (manifest = m))
				.catch((e) => (error = e instanceof Error ? e.message : String(e)))
				.finally(() => (loading = false));
		}
		// Defer focus until the dialog is in the DOM.
		queueMicrotask(() => searchEl?.focus());
	});

	let filtered = $derived.by(() => {
		const all = manifest?.entries ?? [];
		const q = query.trim().toLowerCase();
		const match = q
			? all.filter((e) => e.name.toLowerCase().includes(q) || e.id.toLowerCase().includes(q))
			: all;
		return {
			origins: match.filter((e) => !e.isClass),
			classes: match.filter((e) => e.isClass)
		};
	});

	let total = $derived(filtered.origins.length + filtered.classes.length);

	function pick(entry: TemplateEntry) {
		if (manifest) onselect(entry, manifest);
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
		aria-label="Load vanilla template"
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
					<h2>Load vanilla template</h2>
					<p>Open a shipped origin or class as an editable override.</p>
				</div>
				<button type="button" class="close" aria-label="Close" onclick={onclose}>×</button>
			</header>

			<div class="search">
				<input
					bind:this={searchEl}
					type="text"
					placeholder="Search origins and classes…"
					aria-label="Filter templates"
					bind:value={query}
					autocomplete="off"
					spellcheck="false"
				/>
			</div>

			<div class="body">
				{#if loading}
					<p class="status">Loading templates…</p>
				{:else if error}
					<p class="status error">Couldn't load templates: {error}</p>
				{:else if total === 0}
					<p class="status">No templates match “{query}”.</p>
				{:else}
					{#each [{ label: 'Origins', items: filtered.origins }, { label: 'Classes', items: filtered.classes }] as group (group.label)}
						{#if group.items.length > 0}
							<section class="group">
								<h3>{group.label} <span class="count">{group.items.length}</span></h3>
								<ul>
									{#each group.items as entry (entry.id)}
										<li>
											<button type="button" class="row" onclick={() => pick(entry)}>
												<span class="name">{entry.name}</span>
												<span class="id">{entry.id}</span>
												{#if entry.tierPowerCount > 0}
													<span class="badge" title="Has evolution powers the web editor can't edit">
														evolves
													</span>
												{/if}
											</button>
										</li>
									{/each}
								</ul>
							</section>
						{/if}
					{/each}
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
	.group + .group {
		margin-top: var(--space-4);
	}
	.group h3 {
		margin: 0 0 var(--space-2);
		font-size: 0.72rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.06em;
		color: var(--color-text-subtle);
	}
	.group h3 .count {
		color: var(--color-text-subtle);
		opacity: 0.7;
		margin-left: 0.3rem;
	}
	.group ul {
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
	.badge {
		margin-top: 0.2rem;
		font-size: 0.66rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.04em;
		color: var(--color-accent-2);
		border: 1px solid color-mix(in srgb, var(--color-accent-2) 40%, var(--color-border));
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.35rem;
	}
</style>
