<script lang="ts">
	// Add/remove list of fixed-shape OBJECT sub-forms — an `array` whose `items`
	// declare inline `properties` (e.g. `edible_item.tiers`, `kill_loot_drops.drops`).
	// TS port of the in-game `ArrayObjectRow`.
	//
	// Unlike ArrayRefRow there is no type to pick: every entry has the SAME
	// children, so each element renders through FieldRowAdapter with a synthesized
	// per-index OBJECT spec and FieldRow → ObjectRow does the inline editing. Every
	// edit bubbles up as a NEW array reference (the adapter only re-fires on
	// reference change). The bound value is an array of plain objects, so it
	// serializes straight into the wire JSON.

	import type { ArrayObjectFieldSpec, ObjectFieldSpec } from '$lib/schema/FormFieldSpec';
	import FieldRowAdapter from '$lib/components/power/FieldRowAdapter.svelte';

	let {
		field,
		value = $bindable()
	}: { field: ArrayObjectFieldSpec; value: unknown } = $props();

	const items = $derived(Array.isArray(value) ? (value as unknown[]) : []);

	const id = $derived(`arrobj-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);

	function elemField(i: number): ObjectFieldSpec {
		return {
			path: `${field.path}/${i}`,
			name: String(i),
			label: `#${i + 1}`,
			description: '',
			required: false,
			kind: 'OBJECT',
			children: field.children
		};
	}

	function addElem() {
		// A fresh entry starts as an empty object so its sub-form renders at once;
		// pruneForWire drops keys the author never fills in.
		value = [...items, {}];
	}

	function setElem(i: number, v: unknown) {
		value = items.map((el, j) => (j === i ? v : el));
	}

	function removeElem(i: number) {
		value = items.filter((_, j) => j !== i);
	}
</script>

<div class="arr">
	<div class="arr-head">
		<span class="lbl" {id}>
			{field.label}
			{#if field.required}<span class="req" aria-label="required">*</span>{/if}
			<span class="kind">[object[]]</span>
		</span>
		<button type="button" class="add" onclick={addElem}>+ Add</button>
	</div>

	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}

	{#if items.length === 0}
		<small class="empty">None yet.</small>
	{:else}
		<div class="nested" role="group" aria-labelledby={id}>
			{#each items as item, i (i)}
				<div class="elem">
					<button
						type="button"
						class="remove"
						onclick={() => removeElem(i)}
						aria-label={`Remove #${i + 1}`}
						title="Remove"
					>
						×
					</button>
					<div class="elem-body">
						<FieldRowAdapter
							field={elemField(i)}
							value={item}
							onUpdate={(v) => setElem(i, v)}
						/>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.arr {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
	}
	.arr-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
	}
	.req {
		color: var(--color-accent);
		margin-left: 0.2rem;
	}
	.kind {
		color: var(--color-text-subtle);
		font-size: 0.7rem;
		font-weight: normal;
		font-family: var(--font-mono);
		margin-left: 0.3rem;
	}
	.add {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
		border: 1px solid var(--color-accent);
		border-radius: var(--radius-sm);
		padding: 0.25rem 0.6rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.78rem;
		font-weight: 500;
	}
	.add:hover {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}
	.desc {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.empty {
		color: var(--color-text-muted);
		font-style: italic;
		font-size: 0.78rem;
	}
	.nested {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		margin-left: var(--space-3);
		padding-left: var(--space-3);
		border-left: 2px solid var(--color-border);
	}
	.elem {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
	}
	.elem-body {
		flex: 1;
		min-width: 0;
	}
	.remove {
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.15rem 0.4rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.9rem;
		line-height: 1;
		margin-top: 0.5rem;
	}
	.remove:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
</style>
