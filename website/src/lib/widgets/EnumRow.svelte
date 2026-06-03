<script lang="ts">
	import type { EnumFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable('')
	}: { field: EnumFieldSpec; value: string } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const descId = $derived(`${id}-desc`);
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<select {id} aria-describedby={field.description ? descId : undefined} bind:value>
		{#if !field.required}
			<option value="">(none)</option>
		{/if}
		{#each field.options as opt}
			<option value={opt}>{opt}</option>
		{/each}
	</select>
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
	select {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.4rem 0.55rem;
		font: inherit;
		font-size: 0.85rem;
		max-width: 20rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	select:hover {
		border-color: var(--color-border-strong);
	}
	select:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
</style>
