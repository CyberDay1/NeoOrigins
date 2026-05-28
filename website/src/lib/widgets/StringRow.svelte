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
	.pat {
		grid-column: 2;
		color: #999;
		font-size: 0.78rem;
	}
	.pat.invalid {
		color: #e25d4a;
	}
	input[type='text'] {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.3rem 0.5rem;
		font: inherit;
		width: 100%;
		max-width: 24rem;
	}
	input[type='text']:focus {
		outline: none;
		border-color: #4a90e2;
	}
	input[type='text'].invalid {
		border-color: #e25d4a;
	}
</style>
