<script lang="ts">
	type Tab = 'identity' | 'powers' | 'json';
	let active = $state<Tab>('identity');
</script>

<h1>Origin editor</h1>
<p class="subtitle">MVP scaffold — tabs render but content is not yet wired.</p>

<div class="tabs" role="tablist">
	<button
		role="tab"
		aria-selected={active === 'identity'}
		class:active={active === 'identity'}
		onclick={() => (active = 'identity')}
	>
		Identity
	</button>
	<button
		role="tab"
		aria-selected={active === 'powers'}
		class:active={active === 'powers'}
		onclick={() => (active = 'powers')}
	>
		Powers
	</button>
	<button
		role="tab"
		aria-selected={active === 'json'}
		class:active={active === 'json'}
		onclick={() => (active = 'json')}
	>
		JSON Preview
	</button>
</div>

{#if active === 'identity'}
	<section aria-labelledby="identity-heading">
		<h2 id="identity-heading">Identity</h2>
		<!-- TODO: Identity tab — name, description, icon, impact, order.
		     Plain inputs only for MVP; no item picker. -->
		<p class="placeholder">Coming next.</p>
	</section>
{:else if active === 'powers'}
	<section aria-labelledby="powers-heading">
		<h2 id="powers-heading">Powers</h2>
		<!-- TODO: Powers tab — add/remove, type picker over power.schema.json `type.enum`,
		     schema-driven FieldRow rendering. Tier-A fields = BOOL/INT/NUMBER/ENUM/STRING.
		     Everything else falls back to a raw-JSON <textarea>. -->
		<p class="placeholder">Coming next.</p>
	</section>
{:else}
	<section aria-labelledby="json-heading">
		<h2 id="json-heading">JSON Preview</h2>
		<!-- TODO: live origin JSON + per-power JSON; AJV-validated, errors inline.
		     Hook into stores/draft.ts once it exists. -->
		<p class="placeholder">Coming next.</p>
	</section>
{/if}

<style>
	.subtitle {
		color: #888;
		margin-top: -0.25rem;
	}
	.tabs {
		display: flex;
		gap: 0.25rem;
		border-bottom: 1px solid #333;
		margin: 1.5rem 0 1rem;
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
	.tabs button.active {
		color: #fff;
		border-bottom-color: #4a90e2;
	}
	.placeholder {
		color: #777;
		font-style: italic;
	}
</style>
