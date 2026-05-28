<script lang="ts">
	import type { IntegerFieldSpec, NumberFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable(null)
	}: { field: IntegerFieldSpec | NumberFieldSpec; value: number | null } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const step = $derived(field.kind === 'INTEGER' ? '1' : 'any');

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
		oninput={onInput}
	/>
	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}
	{#if field.min !== null || field.max !== null}
		<small class="range">
			range: {field.min ?? '−∞'} … {field.max ?? '+∞'}
		</small>
	{/if}
</div>

<style>
	.row {
		display: grid;
		grid-template-columns: 12rem 1fr;
		align-items: center;
		gap: 0.5rem;
		padding: 0.4rem 0;
		border-bottom: 1px solid #2a2a2a;
	}
	.lbl {
		color: #e6e6e6;
		font-size: 0.9rem;
	}
	.req {
		color: #4a90e2;
		margin-left: 0.25rem;
	}
	.desc,
	.range {
		grid-column: 2;
		color: #999;
		font-size: 0.78rem;
	}
	input[type='number'] {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.3rem 0.5rem;
		font: inherit;
		max-width: 12rem;
	}
	input[type='number']:focus {
		outline: none;
		border-color: #4a90e2;
	}
</style>
