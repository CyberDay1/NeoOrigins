<script lang="ts">
	import type { StringFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable('')
	}: { field: StringFieldSpec; value: string } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	let invalid = $derived(
		field.pattern && value !== '' && !new RegExp(field.pattern).test(value)
	);
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<input
		{id}
		type="text"
		class:invalid
		pattern={field.pattern ?? undefined}
		bind:value
	/>
	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}
	{#if field.pattern}
		<small class="pat" class:invalid>pattern: {field.pattern}</small>
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
	.pat {
		grid-column: 2;
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.pat {
		font-family: var(--font-mono);
	}
	.pat.invalid {
		color: var(--color-danger);
	}
	input[type='text'] {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.4rem 0.55rem;
		font: inherit;
		font-size: 0.86rem;
		width: 100%;
		max-width: 26rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input[type='text']:hover {
		border-color: var(--color-border-strong);
	}
	input[type='text']:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input[type='text'].invalid {
		border-color: var(--color-danger);
	}
</style>
