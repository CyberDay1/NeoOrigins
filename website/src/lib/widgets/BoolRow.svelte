<script lang="ts">
	import type { BooleanFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable(false)
	}: { field: BooleanFieldSpec; value: boolean } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const descId = $derived(`${id}-desc`);
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<input
		{id}
		type="checkbox"
		aria-describedby={field.description ? descId : undefined}
		bind:checked={value}
	/>
	{#if field.description}
		<small class="desc" id={descId}>{field.description}</small>
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
	.desc {
		grid-column: 2;
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	input[type='checkbox'] {
		accent-color: var(--color-accent);
		width: 1rem;
		height: 1rem;
		cursor: pointer;
	}
</style>
