<script lang="ts">
	import type { IntegerFieldSpec, NumberFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable(null)
	}: { field: IntegerFieldSpec | NumberFieldSpec; value: number | null } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const step = $derived(field.kind === 'INTEGER' ? '1' : 'any');
	const descId = $derived(`${id}-desc`);
	const rangeId = $derived(`${id}-range`);
	const hasRange = $derived(field.min !== null || field.max !== null);
	const describedBy = $derived(
		[field.description ? descId : null, hasRange ? rangeId : null]
			.filter(Boolean)
			.join(' ') || undefined
	);

	// Use a string proxy so the input can be cleared without clobbering value
	// to NaN. Sync both directions.
	let raw = $state(value === null ? '' : String(value));
	$effect(() => {
		const next = value === null ? '' : String(value);
		if (next !== raw) raw = next;
	});
	function onInput(e: Event) {
		const t = (e.target as HTMLInputElement).value;
		raw = t;
		if (t === '') {
			value = null;
			return;
		}
		const n = field.kind === 'INTEGER' ? parseInt(t, 10) : parseFloat(t);
		value = Number.isFinite(n) ? n : null;
	}
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<input
		{id}
		type="number"
		{step}
		min={field.min ?? undefined}
		max={field.max ?? undefined}
		value={raw}
		aria-describedby={describedBy}
		oninput={onInput}
	/>
	{#if field.description}
		<small class="desc" id={descId}>{field.description}</small>
	{/if}
	{#if hasRange}
		<small class="range" id={rangeId}>
			range: {field.min ?? '−∞'} … {field.max ?? '+∞'}
		</small>
	{/if}
</div>

<style>
	.row {
		display: grid;
		grid-template-columns: 13rem 1fr;
		align-items: center;
		gap: var(--space-2);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
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
	.desc,
	.range {
		grid-column: 2;
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	input[type='number'] {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.4rem 0.55rem;
		font: inherit;
		font-family: var(--font-mono);
		font-size: 0.85rem;
		max-width: 12rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input[type='number']:hover {
		border-color: var(--color-border-strong);
	}
	input[type='number']:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
</style>
