<script lang="ts">
	import { draft, fullId, resetDraft } from '$lib/stores/originDraft';
	import { exportDatapack, suggestedFilename } from '$lib/datapack/export';
	import IdentityTab from '$lib/components/IdentityTab.svelte';
	import PowersTab from '$lib/components/PowersTab.svelte';
	import JsonPreviewTab from '$lib/components/JsonPreviewTab.svelte';

	type Tab = 'identity' | 'powers' | 'json';
	let active = $state<Tab>('identity');

	let downloadMessage = $state<string>('');

	let displayId = $derived($draft.path ? fullId($draft) : 'Untitled Origin');

	function onReset() {
		if (confirm('Reset the draft? Unsaved changes will be lost.')) {
			resetDraft();
			downloadMessage = '';
		}
	}

	async function onDownload() {
		downloadMessage = '';
		try {
			const blob = await exportDatapack($draft);
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
	<div class="id-display" aria-live="polite">{displayId}</div>
	<button type="button" class="reset" onclick={onReset}>Reset</button>
</div>

<div class="tabs" role="tablist">
	<button
		type="button"
		role="tab"
		aria-selected={active === 'identity'}
		class:active={active === 'identity'}
		onclick={() => (active = 'identity')}
	>
		Identity
	</button>
	<button
		type="button"
		role="tab"
		aria-selected={active === 'powers'}
		class:active={active === 'powers'}
		onclick={() => (active = 'powers')}
	>
		Powers
	</button>
	<button
		type="button"
		role="tab"
		aria-selected={active === 'json'}
		class:active={active === 'json'}
		onclick={() => (active = 'json')}
	>
		JSON Preview
	</button>
</div>

<div class="tab-body">
	{#if active === 'identity'}
		<IdentityTab />
	{:else if active === 'powers'}
		<PowersTab />
	{:else}
		<JsonPreviewTab />
	{/if}
</div>

<div class="bottombar">
	<button type="button" class="download" onclick={onDownload}>Download datapack (.zip)</button>
	{#if downloadMessage}
		<p class="dl-msg">{downloadMessage}</p>
	{/if}
</div>

<style>
	.topbar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 1rem;
		padding: 0.75rem 1rem;
		background: #1a1a1a;
		border: 1px solid #2a2a2a;
		border-radius: 4px;
		margin-bottom: 1rem;
	}
	.id-display {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		color: #e6e6e6;
		font-size: 0.95rem;
	}
	.reset {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.9rem;
		cursor: pointer;
		font: inherit;
	}
	.reset:hover {
		border-color: #e25d4a;
		color: #e25d4a;
	}
	.tabs {
		display: flex;
		gap: 0.25rem;
		border-bottom: 1px solid #333;
		margin-bottom: 1rem;
	}
	.tabs button {
		padding: 0.5rem 1rem;
		background: transparent;
		color: #b8b8b8;
		border: none;
		border-bottom: 2px solid transparent;
		cursor: pointer;
		font: inherit;
	}
	.tabs button:hover {
		color: #fff;
	}
	.tabs button.active {
		color: #fff;
		border-bottom-color: #4a90e2;
	}
	.tab-body {
		min-height: 12rem;
		padding: 0.5rem 0 1.5rem;
	}
	.bottombar {
		border-top: 1px solid #2a2a2a;
		padding-top: 1rem;
		display: flex;
		flex-direction: column;
		gap: 0.5rem;
		align-items: flex-start;
	}
	.download {
		background: #1a1a1a;
		color: #e6e6e6;
		border: 1px solid #4a90e2;
		border-radius: 3px;
		padding: 0.5rem 1rem;
		cursor: pointer;
		font: inherit;
	}
	.download:hover {
		background: #4a90e2;
		color: #fff;
	}
	.dl-msg {
		margin: 0;
		color: #b8b8b8;
		font-size: 0.85rem;
		font-style: italic;
	}
</style>
