<script lang="ts">
	import {
		draft,
		createDropRow,
		DROP_MODES,
		DROP_STRATEGIES,
		targetVersion,
		type MobOriginDraft,
		type DropRow
	} from '$lib/stores/mobOriginDraft';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	const MAX_ROWS = 32;

	$effect(() => ensureVanilla($targetVersion));

	function set<K extends keyof MobOriginDraft>(key: K, value: MobOriginDraft[K]) {
		draft.update((d) => ({ ...d, [key]: value }));
	}

	function updateRow(idx: number, patch: Partial<DropRow>) {
		draft.update((d) => ({
			...d,
			dropEntries: d.dropEntries.map((r, i) => (i === idx ? { ...r, ...patch } : r))
		}));
	}

	function addRow() {
		draft.update((d) =>
			d.dropEntries.length >= MAX_ROWS
				? d
				: { ...d, dropEntries: [...d.dropEntries, createDropRow()] }
		);
	}

	function removeRow(idx: number) {
		draft.update((d) => ({ ...d, dropEntries: d.dropEntries.filter((_, i) => i !== idx) }));
	}

	function numInput(e: Event): number {
		return Number((e.currentTarget as HTMLInputElement).value);
	}

	let enabled = $derived($draft.dropsEnabled);
	let weighted = $derived($draft.dropStrategy === 'weighted_pool');
	let rowCount = $derived($draft.dropEntries.length);
</script>

<section aria-labelledby="mob-drops-heading" class="tab">
	<h2 id="mob-drops-heading">Drops</h2>
	<p class="lede">Per-origin drops layered onto the mob's vanilla loot table.</p>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.dropsEnabled}
				onchange={(e) => set('dropsEnabled', (e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Enable drops</span>
		</label>
		<small class="hint">
			The <code>drops</code> block is only written when enabled <em>and</em> at least one entry has an
			item.
		</small>
	</div>

	<fieldset class="group" disabled={!enabled} aria-disabled={!enabled}>
		<legend>Strategy</legend>

		<div class="grid2">
			<div class="row">
				<span class="lbl" id="drops-mode-lbl">Mode</span>
				<div class="seg" role="radiogroup" aria-labelledby="drops-mode-lbl">
					{#each DROP_MODES as opt (opt)}
						<button
							type="button"
							role="radio"
							aria-checked={$draft.dropMode === opt}
							class="seg-btn"
							class:active={$draft.dropMode === opt}
							onclick={() => set('dropMode', opt)}
						>
							{opt}
						</button>
					{/each}
				</div>
				<small class="hint">
					<code>additive</code> adds to vanilla drops; <code>replace</code> overrides them.
				</small>
			</div>

			<div class="row">
				<span class="lbl" id="drops-strat-lbl">Roll strategy</span>
				<div class="seg" role="radiogroup" aria-labelledby="drops-strat-lbl">
					{#each DROP_STRATEGIES as opt (opt)}
						<button
							type="button"
							role="radio"
							aria-checked={$draft.dropStrategy === opt}
							class="seg-btn"
							class:active={$draft.dropStrategy === opt}
							onclick={() => set('dropStrategy', opt)}
						>
							{opt}
						</button>
					{/each}
				</div>
				<small class="hint">
					{#if weighted}
						Pool draws use each entry's <strong>weight</strong>; per-entry chance/rolls are ignored.
					{:else}
						Each entry rolls independently using its <strong>chance</strong> and <strong>rolls</strong>;
						weight is ignored.
					{/if}
				</small>
			</div>
		</div>

		{#if weighted}
			<div class="row">
				<label class="lbl" for="drops-pool-rolls">Pool rolls</label>
				<input
					id="drops-pool-rolls"
					type="number"
					min="0"
					value={$draft.dropPoolRolls}
					oninput={(e) => set('dropPoolRolls', numInput(e))}
				/>
				<small class="hint">How many times the weighted pool is drawn per kill.</small>
			</div>
		{/if}
	</fieldset>

	<fieldset class="group" disabled={!enabled} aria-disabled={!enabled}>
		<legend>Entries ({rowCount}{rowCount === MAX_ROWS ? ` / ${MAX_ROWS} max` : ''})</legend>

		{#if rowCount === 0}
			<p class="empty">No drops yet. Add one below.</p>
		{:else}
			<div class="rows" role="table" aria-label="Drop entries">
				<div class="rhead" role="row">
					<span role="columnheader">Item</span>
					<span role="columnheader">Count min</span>
					<span role="columnheader">Count max</span>
					<span role="columnheader" class:dim={weighted}>Chance</span>
					<span role="columnheader" class:dim={weighted}>Rolls</span>
					<span role="columnheader" class:dim={!weighted}>Weight</span>
					<span role="columnheader" class="sr-only">Remove</span>
				</div>

				{#each $draft.dropEntries as entry, i (i)}
					<div class="rrow" role="row">
						<SuggestInput
							value={entry.item}
							suggestions={$vanilla.items}
							placeholder="minecraft:rotten_flesh"
							ariaLabel="Item id for drop {i + 1}"
							oninput={(v) => updateRow(i, { item: v })}
						/>
						<input
							type="number"
							min="0"
							value={entry.countMin}
							oninput={(e) => updateRow(i, { countMin: numInput(e) })}
							aria-label="Count min for drop {i + 1}"
						/>
						<input
							type="number"
							min="0"
							value={entry.countMax}
							oninput={(e) => updateRow(i, { countMax: numInput(e) })}
							aria-label="Count max for drop {i + 1}"
						/>
						<input
							type="number"
							step="0.05"
							min="0"
							max="1"
							value={entry.chance}
							disabled={weighted}
							oninput={(e) => updateRow(i, { chance: numInput(e) })}
							aria-label="Chance for drop {i + 1}"
						/>
						<input
							type="number"
							min="0"
							value={entry.rolls}
							disabled={weighted}
							oninput={(e) => updateRow(i, { rolls: numInput(e) })}
							aria-label="Rolls for drop {i + 1}"
						/>
						<input
							type="number"
							min="0"
							value={entry.weight}
							disabled={!weighted}
							oninput={(e) => updateRow(i, { weight: numInput(e) })}
							aria-label="Weight for drop {i + 1}"
						/>
						<button
							type="button"
							class="remove"
							onclick={() => removeRow(i)}
							aria-label="Remove drop {i + 1}"
						>
							&times;
						</button>
					</div>
				{/each}
			</div>
		{/if}

		<button type="button" class="add" onclick={addRow} disabled={rowCount >= MAX_ROWS}>
			+ Add drop
		</button>
	</fieldset>
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}
	h2 {
		margin: 0;
		color: var(--color-text);
		font-size: 1.05rem;
		font-weight: 600;
		letter-spacing: -0.01em;
	}
	.lede {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.85rem;
	}
	.row {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}
	.grid2 {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
		gap: var(--space-4);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
	}
	.check {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text);
		font-size: 0.88rem;
		cursor: pointer;
	}
	.hint {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.empty {
		margin: 0;
		color: var(--color-text-subtle);
		font-size: 0.82rem;
	}
	code {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		color: var(--color-text-muted);
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.3rem;
	}
	.group {
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: var(--space-3);
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		margin: 0;
	}
	.group:disabled {
		opacity: 0.55;
	}
	.group legend {
		padding: 0 var(--space-2);
		color: var(--color-text);
		font-size: 0.8rem;
		font-weight: 600;
	}

	.rows {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
		overflow-x: auto;
	}
	.rhead,
	.rrow {
		display: grid;
		grid-template-columns: minmax(10rem, 2fr) 5rem 5rem 5rem 4rem 5rem 1.75rem;
		gap: var(--space-2);
		align-items: center;
		min-width: 40rem;
	}
	.rhead span {
		color: var(--color-text-subtle);
		font-size: 0.74rem;
		font-weight: 600;
		text-transform: uppercase;
		letter-spacing: 0.03em;
	}
	.rhead span.dim {
		opacity: 0.45;
	}

	input[type='number'] {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.4rem 0.55rem;
		font: inherit;
		font-size: 0.85rem;
		width: 100%;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input:hover:not(:disabled) {
		border-color: var(--color-border-strong);
	}
	input:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input:disabled {
		opacity: 0.4;
		cursor: not-allowed;
	}
	input[type='checkbox'] {
		accent-color: var(--color-accent);
		width: 1rem;
		height: 1rem;
		cursor: pointer;
	}

	.remove {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.75rem;
		height: 1.75rem;
		background: var(--color-bg-subtle);
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		font-size: 1.1rem;
		line-height: 1;
		cursor: pointer;
		transition: border-color 120ms ease, color 120ms ease, background 120ms ease;
	}
	.remove:hover {
		border-color: var(--color-danger);
		color: var(--color-danger);
	}

	.add {
		align-self: flex-start;
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.45rem 0.85rem;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		transition: border-color 120ms ease, background 120ms ease;
	}
	.add:hover:not(:disabled) {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	.add:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}

	.seg {
		display: inline-flex;
		gap: 2px;
		padding: 3px;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		align-self: flex-start;
		max-width: 100%;
		overflow-x: auto;
	}
	.seg-btn {
		background: transparent;
		color: var(--color-text-muted);
		border: none;
		border-radius: var(--radius-sm);
		padding: 0.4rem 0.85rem;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		white-space: nowrap;
		transition: background 120ms ease, color 120ms ease;
	}
	.seg-btn:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
	}
	.seg-btn.active {
		background: var(--color-surface);
		color: var(--color-text);
		box-shadow: var(--shadow-sm);
	}

	.sr-only {
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
</style>
