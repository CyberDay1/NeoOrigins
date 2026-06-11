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
	import { exportDatapack, suggestedFilename, ExportValidationError } from '$lib/datapack/export';
	import { importDatapack, ImportError } from '$lib/datapack/import';
	import { loadTemplate, type TemplateEntry } from '$lib/datapack/vanillaTemplates';
	import VanillaTemplatePicker from '$lib/components/VanillaTemplatePicker.svelte';
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
	// Export-gate failures (blank/duplicate ids). Rendered as a role="alert"
	// list near the download button — screen readers announce it on refusal.
	let exportBlockedIssues = $state<string[]>([]);

	// Import state: warnings (non-fatal approximations) and a fatal error.
	let importWarnings = $state<string[]>([]);
	let importError = $state<string>('');
	let fileInput = $state<HTMLInputElement>();
	let templatePickerOpen = $state(false);

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
		exportBlockedIssues = [];
		try {
			const blob = await exportDatapack($draft, packFormatFor($targetVersion));
			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = suggestedFilename($draft);
			a.click();
			URL.revokeObjectURL(url);
		} catch (err) {
			if (err instanceof ExportValidationError) {
				// Validation gate refused the export — show every issue, not
				// just the first, so the user can fix them in one pass.
				exportBlockedIssues = err.issues;
			} else {
				console.error('exportDatapack failed:', err);
				downloadMessage = 'Export failed — see console for details.';
			}
		}
	}

	function onImportClick() {
		fileInput?.click();
	}

	function onTemplateSelect(
		entry: TemplateEntry,
		manifest: Parameters<typeof loadTemplate>[1]
	) {
		importError = '';
		importWarnings = [];
		downloadMessage = '';
		templatePickerOpen = false;
		try {
			const res = loadTemplate(entry, manifest);
			draft.set(res.draft);
			targetVersion.set(res.targetVersion);
			importWarnings = res.warnings;
			activeTab.set('identity');
		} catch (err) {
			importError = `Couldn't load that template: ${err instanceof Error ? err.message : String(err)}`;
		}
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
			const res = importDatapack(bytes);
			draft.set(res.draft);
			targetVersion.set(res.targetVersion);
			importWarnings = res.warnings;
			activeTab.set('identity');
		} catch (err) {
			importError =
				err instanceof ImportError
					? err.message
					: `Couldn't import that file: ${err instanceof Error ? err.message : String(err)}`;
		}
	}
</script>

<div class="topbar">
	<div class="topbar-id">
		<span class="topbar-label">Editing</span>
		<span class="id-display" aria-live="polite">{displayId}</span>
	</div>
	<div class="topbar-actions">
		<button type="button" class="btn-secondary" onclick={() => (templatePickerOpen = true)}>
			Load vanilla template
		</button>
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

<div class="tabs" role="tablist" aria-label="Origin editor sections">
	<button
		type="button"
		role="tab"
		id="origin-tab-identity"
		aria-selected={$activeTab === 'identity'}
		aria-controls="origin-tabpanel"
		class:active={$activeTab === 'identity'}
		onclick={() => setActive('identity')}
	>
		Identity
	</button>
	<button
		type="button"
		role="tab"
		id="origin-tab-powers"
		aria-selected={$activeTab === 'powers'}
		aria-controls="origin-tabpanel"
		class:active={$activeTab === 'powers'}
		onclick={() => setActive('powers')}
	>
		Powers
	</button>
	<button
		type="button"
		role="tab"
		id="origin-tab-upgrades"
		aria-selected={$activeTab === 'upgrades'}
		aria-controls="origin-tabpanel"
		class:active={$activeTab === 'upgrades'}
		onclick={() => setActive('upgrades')}
	>
		Upgrades
	</button>
	<button
		type="button"
		role="tab"
		id="origin-tab-json"
		aria-selected={$activeTab === 'json'}
		aria-controls="origin-tabpanel"
		class:active={$activeTab === 'json'}
		onclick={() => setActive('json')}
	>
		JSON Preview
	</button>
</div>

<div
	class="tab-card"
	role="tabpanel"
	id="origin-tabpanel"
	aria-labelledby={`origin-tab-${$activeTab}`}
	tabindex="0"
>
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
	{#if exportBlockedIssues.length > 0}
		<div class="export-blocked" role="alert" aria-label="Export blocked">
			<p class="export-blocked-title">
				Export blocked — fix {exportBlockedIssues.length === 1 ? 'this issue' : 'these issues'} first:
			</p>
			<ul>
				{#each exportBlockedIssues as issue, i (i)}
					<li>{issue}</li>
				{/each}
			</ul>
		</div>
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

<VanillaTemplatePicker
	open={templatePickerOpen}
	onselect={onTemplateSelect}
	onclose={() => (templatePickerOpen = false)}
/>

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

	/* Card wrapping each tab's body. Kept near-opaque so form fields stay
	 * crisp over the moving nebula backdrop. */
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

	/* Visually hide the file <input> but keep it focusable/clickable. */
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
	.export-blocked {
		width: 100%;
		padding: var(--space-3);
		background: var(--color-danger-subtle);
		border: 1px solid color-mix(in srgb, var(--color-danger) 40%, var(--color-border));
		border-radius: var(--radius-md);
	}
	.export-blocked-title {
		margin: 0 0 var(--space-2);
		font-size: 0.85rem;
		font-weight: 600;
		color: var(--color-danger);
	}
	.export-blocked ul {
		margin: 0;
		padding-left: 1.25rem;
		display: flex;
		flex-direction: column;
		gap: 2px;
	}
	.export-blocked li {
		font-size: 0.82rem;
		color: var(--color-text);
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
