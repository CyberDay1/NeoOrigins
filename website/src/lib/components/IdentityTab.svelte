<script lang="ts">
	import { draft } from '$lib/stores/originDraft';

	// Namespaced id pattern: <namespace>:<path>. Accepts a-z, 0-9, underscore
	// in the namespace half; the path half additionally allows `/`, `.`, `-`.
	// Matches the MVP scope of "looks like a valid resource location"; the
	// full schema regex is stricter and will be enforced at JSON export time.
	const ID_PATTERN = /^[a-z0-9_]+:[a-z0-9_/.-]+$/;

	const IMPACTS = ['none', 'low', 'medium', 'high'] as const;
	type Impact = (typeof IMPACTS)[number];
	const IMPACT_LABELS: Record<Impact, string> = {
		none: 'None',
		low: 'Low',
		medium: 'Medium',
		high: 'High'
	};

	let idInvalid = $derived($draft.id !== '' && !ID_PATTERN.test($draft.id));

	function setId(v: string) {
		draft.update((d) => ({ ...d, id: v }));
	}
	function setName(v: string) {
		draft.update((d) => ({ ...d, name: v }));
	}
	function setDescription(v: string) {
		draft.update((d) => ({ ...d, description: v }));
	}
	function setIcon(v: string) {
		draft.update((d) => ({ ...d, icon: v }));
	}
	function setImpact(v: Impact) {
		draft.update((d) => ({ ...d, impact: v }));
	}
	function setOrder(v: number) {
		draft.update((d) => ({ ...d, order: Number.isFinite(v) ? v : 0 }));
	}
	function setUnchoosable(v: boolean) {
		draft.update((d) => ({ ...d, unchoosable: v }));
	}
	function setHidden(v: boolean) {
		draft.update((d) => ({ ...d, hidden: v }));
	}
</script>

<section aria-labelledby="identity-heading" class="tab">
	<h2 id="identity-heading">Identity</h2>

	<div class="row">
		<label class="lbl" for="origin-id">Id</label>
		<input
			id="origin-id"
			type="text"
			class:invalid={idInvalid}
			value={$draft.id}
			oninput={(e) => setId((e.currentTarget as HTMLInputElement).value)}
			placeholder="mypack:wizard"
			autocomplete="off"
			spellcheck="false"
		/>
		<small class="hint">namespaced, e.g. <code>mypack:wizard</code></small>
		{#if idInvalid}
			<small class="err">must match <code>{ID_PATTERN.source}</code></small>
		{/if}
	</div>

	<div class="row">
		<label class="lbl" for="origin-name">Name</label>
		<input
			id="origin-name"
			type="text"
			value={$draft.name}
			oninput={(e) => setName((e.currentTarget as HTMLInputElement).value)}
		/>
		<small class="hint">Display name</small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-description">Description</label>
		<textarea
			id="origin-description"
			rows="4"
			value={$draft.description}
			oninput={(e) => setDescription((e.currentTarget as HTMLTextAreaElement).value)}
		></textarea>
	</div>

	<div class="row">
		<label class="lbl" for="origin-icon">Icon</label>
		<input
			id="origin-icon"
			type="text"
			class="mono"
			value={$draft.icon}
			oninput={(e) => setIcon((e.currentTarget as HTMLInputElement).value)}
		/>
		<small class="hint">Single character or short text</small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-impact">Impact</label>
		<select
			id="origin-impact"
			value={$draft.impact}
			onchange={(e) => setImpact((e.currentTarget as HTMLSelectElement).value as Impact)}
		>
			{#each IMPACTS as i (i)}
				<option value={i}>{IMPACT_LABELS[i]}</option>
			{/each}
		</select>
	</div>

	<div class="row">
		<label class="lbl" for="origin-order">Order</label>
		<input
			id="origin-order"
			type="number"
			step="1"
			value={$draft.order}
			oninput={(e) => setOrder(parseInt((e.currentTarget as HTMLInputElement).value, 10))}
		/>
	</div>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.unchoosable}
				onchange={(e) => setUnchoosable((e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Unchoosable</span>
		</label>
		<small class="hint">Hidden from origin selection screen</small>
	</div>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.hidden}
				onchange={(e) => setHidden((e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Hidden</span>
		</label>
		<small class="hint">Excluded from listings (developer/testing)</small>
	</div>
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	h2 {
		margin: 0 0 0.25rem;
		color: #e6e6e6;
	}
	.row {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}
	.lbl {
		color: #e6e6e6;
		font-size: 0.9rem;
	}
	.check {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		color: #e6e6e6;
		font-size: 0.9rem;
	}
	.hint {
		color: #999;
		font-size: 0.78rem;
	}
	.err {
		color: #e25d4a;
		font-size: 0.78rem;
	}
	code {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.78rem;
		color: #b8b8b8;
	}
	input[type='text'],
	input[type='number'],
	textarea,
	select {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.55rem;
		font: inherit;
		max-width: 32rem;
	}
	textarea {
		resize: vertical;
		min-height: 4.5rem;
	}
	input.mono {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	}
	input:focus,
	textarea:focus,
	select:focus {
		outline: none;
		border-color: #4a90e2;
	}
	input.invalid {
		border-color: #e25d4a;
	}
</style>
