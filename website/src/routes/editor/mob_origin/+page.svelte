<script lang="ts">
	import { onMount } from 'svelte';
	import type { Writable } from 'svelte/store';
	import type { PowerDraft } from '$lib/stores/originDraft';
	import {
		draft,
		fullId,
		resetDraft,
		activeTab,
		targetVersion,
		packFormatFor,
		initPersistence,
		clearPersistedDraft,
		type MobEditorTab
	} from '$lib/stores/mobOriginDraft';
	import { exportMobDatapack, suggestedFilename } from '$lib/datapack/mobExport';
	import { importMobDatapack, MobImportError } from '$lib/datapack/mobImport';
	import MobIdentityTab from '$lib/components/mob/MobIdentityTab.svelte';
	import MobSpawnRulesTab from '$lib/components/mob/MobSpawnRulesTab.svelte';
	import MobDropsTab from '$lib/components/mob/MobDropsTab.svelte';
	import PowersTab from '$lib/components/PowersTab.svelte';
	import MobJsonPreviewTab from '$lib/components/mob/MobJsonPreviewTab.svelte';

	// Restore draft + tab from localStorage and start autosaving. Idempotent.
	onMount(() => {
		initPersistence();
	});

	let downloadMessage = $state<string>('');
	let importWarnings = $state<string[]>([]);
	let importError = $state<string>('');
	let fileInput = $state<HTMLInputElement>();

	let displayId = $derived($draft.path ? fullId($draft) : 'Untitled Mob Origin');

	// Shared PowersTab/BlockCanvas type their store as `Writable<{ powers }>`;
	// the mob draft store satisfies that structurally but `Writable` is
	// invariant over its update/set signatures, so cast at the call site.
	const powersStore = draft as unknown as Writable<{ powers: PowerDraft[] }>;

	function setActive(t: MobEditorTab) {
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
				'This permanently deletes the autosaved mob-origin draft from this browser ' +
				'and reloads the page. This cannot be undone.'
		);
		if (ok) {
			clearPersistedDraft();
		}
	}

	async function onDownload() {
		downloadMessage = '';
		try {
			const blob = await exportMobDatapack($draft, packFormatFor($targetVersion));
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = suggestedFilename($draft);
			a.click();
			URL.revokeObjectURL(url);
		} catch {
			downloadMessage = 'Export failed — see console for details.';
		}
	}

	function onImportClick() {
		fileInput?.click();
	}

	async function onImportFile(e: Event) {
		const input = e.currentTarget as HTMLInputElement;
		const file = input.files?.[0];
		// Reset the input so picking the same file again re-fires `change`.
		input.value = '';
		if (!file) return;

		importError = '';
		importWarnings = [];
		downloadMessage = '';
		try {
			const bytes = new Uint8Array(await file.arrayBuffer());
			const res = importMobDatapack(bytes);
			draft.set(res.draft);
			targetVersion.set(res.targetVersion);
			importWarnings = res.warnings;
			activeTab.set('identity');
		} catch (err) {
			importError =
				err instanceof MobImportError
					? err.message
					: `Couldn't import that file: ${err instanceof Error ? err.message : String(err)}`;
		}
	}
</script>

<div class="topbar">
	<div class="topbar-id">
		<span class="topbar-label">Editing mob origin</span>
		<span class="id-display" aria-live="polite">{displayId}</span>
	</div>
	<div class="topbar-actions">
		<button type="button" class="btn-secondary" onclick={onImportClick}>
			Import datapack (.zip)
		</button>
		<input
			bind:this={fileInput}
			type="file"
			accept=".zip,application/zip"
			class="visually-hidden"
			aria-label="Import datapack zip file"
			onchange={onImportFile}
		/>
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

<div class="tabs" role="tablist" aria-label="Mob origin editor sections">
	<button
		type="button"
		role="tab"
		id="mob-tab-identity"
		aria-selected={$activeTab === 'identity'}
		aria-controls="mob-tabpanel"
		class:active={$activeTab === 'identity'}
		onclick={() => setActive('identity')}
	>
		Identity
	</button>
	<button
		type="button"
		role="tab"
		id="mob-tab-spawn"
		aria-selected={$activeTab === 'spawn'}
		aria-controls="mob-tabpanel"
		class:active={$activeTab === 'spawn'}
		onclick={() => setActive('spawn')}
	>
		Spawn Rules
	</button>
	<button
		type="button"
		role="tab"
		id="mob-tab-drops"
		aria-selected={$activeTab === 'drops'}
		aria-controls="mob-tabpanel"
		class:active={$activeTab === 'drops'}
		onclick={() => setActive('drops')}
	>
		Drops
	</button>
	<button
		type="button"
		role="tab"
		id="mob-tab-powers"
		aria-selected={$activeTab === 'powers'}
		aria-controls="mob-tabpanel"
		class:active={$activeTab === 'powers'}
		onclick={() => setActive('powers')}
	>
		Powers
	</button>
	<button
		type="button"
		role="tab"
		id="mob-tab-json"
		aria-selected={$activeTab === 'json'}
		aria-controls="mob-tabpanel"
		class:active={$activeTab === 'json'}
		onclick={() => setActive('json')}
	>
		JSON Preview
	</button>
</div>

<div
	class="tab-card"
	role="tabpanel"
	id="mob-tabpanel"
	aria-labelledby={`mob-tab-${$activeTab}`}
	tabindex="0"
>
	{#if $activeTab === 'identity'}
		<MobIdentityTab />
	{:else if $activeTab === 'spawn'}
		<MobSpawnRulesTab />
	{:else if $activeTab === 'drops'}
		<MobDropsTab />
	{:else if $activeTab === 'powers'}
		<PowersTab {powersStore} />
	{:else}
		<MobJsonPreviewTab />
	{/if}
</div>

<div class="bottombar">
	<button type="button" class="btn-primary download" onclick={onDownload}>
		Download datapack (.zip)
	</button>
	{#if downloadMessage}
		<p class="dl-msg">{downloadMessage}</p>
	{/if}
	{#if importError}
		<p class="import-error" role="alert">{importError}</p>
	{/if}
	{#if importWarnings.length > 0}
		<div class="import-warnings" role="status">
			<p class="import-warnings-title">
				Imported with {importWarnings.length}
				{importWarnings.length === 1 ? 'note' : 'notes'}:
			</p>
			<ul>
				{#each importWarnings as warning}
					<li>{warning}</li>
				{/each}
			</ul>
		</div>
	{/if}
</div>

<style>
	.topbar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-3);
		padding: var(--space-3) var(--space-4);
		background: color-mix(in srgb, var(--color-surface) 86%, transparent);
		backdrop-filter: blur(8px) saturate(130%);
		-webkit-backdrop-filter: blur(8px) saturate(130%);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		margin-bottom: var(--space-4);
		box-shadow: var(--shadow-sm);
		animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) both;
	}
	.topbar-id {
		display: flex;
		flex-direction: column;
		gap: 2px;
		min-width: 0;
	}
	.topbar-label {
		font-size: 0.68rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.14em;
		color: var(--color-accent);
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
		transition: background 120ms ease, border-color 120ms ease, color 120ms ease;
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

	.tab-card {
		min-height: 14rem;
		padding: var(--space-5);
		background: color-mix(in srgb, var(--color-surface) 94%, transparent);
		backdrop-filter: blur(10px) saturate(125%);
		-webkit-backdrop-filter: blur(10px) saturate(125%);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		box-shadow: var(--shadow-sm);
		margin-bottom: var(--space-4);
		animation: rise 0.5s cubic-bezier(0.2, 0.7, 0.2, 1) both;
		animation-delay: 0.08s;
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

	.visually-hidden {
		position: absolute;
		width: 1px;
		height: 1px;
		padding: 0;
		margin: -1px;
		overflow: hidden;
		clip: rect(0, 0, 0, 0);
		white-space: nowrap;
		border: 0;
	}

	.import-error {
		margin: 0;
		color: var(--color-danger);
		font-size: 0.85rem;
		font-weight: 500;
	}
	.import-warnings {
		width: 100%;
		padding: var(--space-3);
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
	}
	.import-warnings-title {
		margin: 0 0 var(--space-2);
		font-size: 0.85rem;
		font-weight: 600;
		color: var(--color-text);
	}
	.import-warnings ul {
		margin: 0;
		padding-left: 1.25rem;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.import-warnings li {
		font-size: 0.82rem;
		color: var(--color-text-muted);
	}
</style>
