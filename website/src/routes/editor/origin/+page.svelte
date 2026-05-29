<script lang="ts">
	import { onMount } from 'svelte';
	import {
		draft,
		fullId,
		resetDraft,
		activeTab,
		targetVersion,
		packFormatFor,
		initPersistence,
		clearPersistedDraft,
		type EditorTab
	} from '$lib/stores/originDraft';
	import { exportDatapack, suggestedFilename } from '$lib/datapack/export';
	import IdentityTab from '$lib/components/IdentityTab.svelte';
	import PowersTab from '$lib/components/PowersTab.svelte';
	import UpgradesTab from '$lib/components/UpgradesTab.svelte';
	import JsonPreviewTab from '$lib/components/JsonPreviewTab.svelte';

	// Restore draft + tab + target version from localStorage and start
	// autosaving subsequent edits. Idempotent — safe under HMR.
	onMount(() => {
		initPersistence();
	});

	let downloadMessage = $state<string>('');

	let displayId = $derived($draft.path ? fullId($draft) : 'Untitled Origin');

	function setActive(t: EditorTab) {
		activeTab.set(t);
	}

	function onReset() {
		if (confirm('Reset the draft? Unsaved changes will be lost.')) {
			resetDraft();
			downloadMessage = '';
		}
	}

	function onClearPersisted() {
		const ok = confirm(
			'Reset draft and clear saved progress?\n\n' +
				'This permanently deletes the autosaved draft from this browser ' +
				'and reloads the page. This cannot be undone.'
		);
		if (ok) {
			clearPersistedDraft();
		}
	}

	async function onDownload() {
		downloadMessage = '';
		try {
			const blob = await exportDatapack($draft, packFormatFor($targetVersion));
			// Real download path — wired now so task #15 only has to remove the
			// stub error. Currently unreachable because exportDatapack throws.
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = suggestedFilename($draft);
			a.click();
			URL.revokeObjectURL(url);
		} catch {
			downloadMessage = 'Coming soon — datapack export is not yet implemented.';
		}
	}
</script>

<div class="topbar">
	<div class="topbar-id">
		<span class="topbar-label">Editing</span>
		<span class="id-display" aria-live="polite">{displayId}</span>
	</div>
	<div class="topbar-actions">
		<button type="button" class="btn-secondary" onclick={onReset}>Reset</button>
		<button
			type="button"
			class="btn-danger"
			onclick={onClearPersisted}
			title="Delete autosaved draft from this browser and reload."
		>
			Reset draft (clear saved)
		</button>
	</div>
</div>

<div class="tabs" role="tablist" aria-label="Origin editor sections">
	<button
		type="button"
		role="tab"
		aria-selected={$activeTab === 'identity'}
		class:active={$activeTab === 'identity'}
		onclick={() => setActive('identity')}
	>
		Identity
	</button>
	<button
		type="button"
		role="tab"
		aria-selected={$activeTab === 'powers'}
		class:active={$activeTab === 'powers'}
		onclick={() => setActive('powers')}
	>
		Powers
	</button>
	<button
		type="button"
		role="tab"
		aria-selected={$activeTab === 'upgrades'}
		class:active={$activeTab === 'upgrades'}
		onclick={() => setActive('upgrades')}
	>
		Upgrades
	</button>
	<button
		type="button"
		role="tab"
		aria-selected={$activeTab === 'json'}
		class:active={$activeTab === 'json'}
		onclick={() => setActive('json')}
	>
		JSON Preview
	</button>
</div>

<div class="tab-card">
	{#if $activeTab === 'identity'}
		<IdentityTab />
	{:else if $activeTab === 'powers'}
		<PowersTab />
	{:else if $activeTab === 'upgrades'}
		<UpgradesTab />
	{:else}
		<JsonPreviewTab />
	{/if}
</div>

<div class="bottombar">
	<button type="button" class="btn-primary download" onclick={onDownload}>
		Download datapack (.zip)
	</button>
	{#if downloadMessage}
		<p class="dl-msg">{downloadMessage}</p>
	{/if}
</div>

<style>
	.topbar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		margin-bottom: var(--space-4);
		box-shadow: var(--shadow-sm);
	}
	.topbar-id {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.topbar-label {
		font-size: 0.7rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.1em;
		color: var(--color-text-subtle);
	}
	.id-display {
		font-family: var(--font-mono);
		color: var(--color-text);
		font-size: 0.92rem;
		font-weight: 500;
		overflow-wrap: anywhere;
	}
	.topbar-actions {
		display: flex;
		gap: var(--space-2);
		flex-wrap: wrap;
		justify-content: flex-end;
	}

	/* Shared button base — kept local to this file since the editor route
	 * is where they appear; FieldRow/etc. tabs have their own variants. */
	.btn-secondary,
	.btn-danger,
	.btn-primary {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		padding: 0.5rem 0.95rem;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		border-radius: var(--radius-md);
		border: 1px solid transparent;
		cursor: pointer;
		transition: background 120ms ease, border-color 120ms ease,
			color 120ms ease;
	}
	.btn-secondary {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border-color: var(--color-border);
	}
	.btn-secondary:hover {
		background: var(--color-surface-hover);
		border-color: var(--color-border-strong);
	}
	.btn-danger {
		background: transparent;
		color: var(--color-danger);
		border-color: color-mix(in srgb, var(--color-danger) 35%, var(--color-border));
	}
	.btn-danger:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger-hover);
	}
	.btn-primary {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
		border-color: var(--color-accent);
	}
	.btn-primary:hover {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}

	/* Pill-style tab strip — modern dev-tool aesthetic, not browser default. */
	.tabs {
		display: inline-flex;
		gap: 2px;
		padding: 4px;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		margin-bottom: var(--space-4);
		max-width: 100%;
		overflow-x: auto;
	}
	.tabs button {
		padding: 0.45rem 0.95rem;
		background: transparent;
		color: var(--color-text-muted);
		border: none;
		border-radius: var(--radius-sm);
		cursor: pointer;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		white-space: nowrap;
		transition: background 120ms ease, color 120ms ease;
	}
	.tabs button:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
	}
	.tabs button.active {
		color: var(--color-text);
		background: var(--color-surface);
		box-shadow: var(--shadow-sm);
	}

	/* Card wrapping each tab's body. */
	.tab-card {
		min-height: 14rem;
		padding: var(--space-5);
		background: var(--color-surface);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		margin-bottom: var(--space-4);
	}

	.bottombar {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		align-items: flex-start;
		padding-top: var(--space-3);
	}
	.download {
		padding: 0.6rem 1.1rem;
		font-size: 0.92rem;
	}
	.dl-msg {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.85rem;
		font-style: italic;
	}
</style>
