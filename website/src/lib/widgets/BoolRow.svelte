<script lang="ts">
	import type { BooleanFieldSpec } from '$lib/schema/FormFieldSpec';

	let {
		field,
		value = $bindable(false)
	}: { field: BooleanFieldSpec; value: boolean } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<input {id} type="checkbox" bind:checked={value} />
	{#if field.description}
		<small class="desc">{field.description}</small>
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
	.desc {
		grid-column: 2;
		color: #999;
		font-size: 0.78rem;
	}
	input[type='checkbox'] {
		accent-color: #4a90e2;
		width: 1rem;
		height: 1rem;
		cursor: pointer;
	}
</style>
