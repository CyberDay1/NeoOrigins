<script lang="ts">
	import type { StringFieldSpec } from '$lib/schema/FormFieldSpec';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import { suggestionsFor } from '$lib/data/suggestionKind';
	import { targetVersion } from '$lib/stores/originDraft';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	let {
		field,
		value = $bindable('')
	}: { field: StringFieldSpec; value: string } = $props();

	const id = $derived(`f-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	let invalid = $derived(
		!!(field.pattern && value !== '' && !new RegExp(field.pattern).test(value))
	);
	const descId = $derived(`${id}-desc`);
	const patId = $derived(`${id}-pat`);
	const describedBy = $derived(
		[field.description ? descId : null, field.pattern ? patId : null]
			.filter(Boolean)
			.join(' ') || undefined
	);

	// Vanilla typeahead: resolve a suggestion list from the field name (e.g.
	// `item` → items, `biome` → biomes). SuggestInput shows a custom dropdown;
	// with no matching list it degrades to a plain text input.
	let suggestions = $derived(suggestionsFor(field.name, $vanilla));
	$effect(() => ensureVanilla($targetVersion));
</script>

<div class="row">
	<label class="lbl" for={id}>
		{field.label}
		{#if field.required}<span class="req" aria-label="required">*</span>{/if}
	</label>
	<SuggestInput
		{id}
		{value}
		{suggestions}
		{invalid}
		ariaDescribedby={describedBy}
		mono={false}
		oninput={(v) => (value = v)}
	/>
	{#if field.description}
		<small class="desc" id={descId}>{field.description}</small>
	{/if}
	{#if field.pattern}
		<small class="pat" id={patId} class:invalid>pattern: {field.pattern}</small>
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
</style>
