<script lang="ts">
	// Add/remove list of scalar STRING elements — an `array` whose items are
	// `type:"string"` (no `$ref`), e.g. the `biomes` list on a location
	// condition. Each element is a plain text input; the optional `pattern`
	// regex is surfaced as a hint (matching StringRow). The bound value is a
	// `string[]`. Entries are free-text resource-locations (never a closed
	// enum), so modded/datapack biome ids work by construction.

	import type { ArrayStringFieldSpec } from '$lib/schema/FormFieldSpec';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import { suggestionsFor } from '$lib/data/suggestionKind';
	import { targetVersion } from '$lib/stores/originDraft';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	let {
		field,
		value = $bindable()
	}: { field: ArrayStringFieldSpec; value: string[] | null } = $props();

	const id = $derived(`arr-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);
	const patId = $derived(`${id}-pat`);
	const items = $derived(Array.isArray(value) ? value : []);

	// Vanilla typeahead shared across every element input (e.g. an `entity_types`
	// or `biomes` list). Each element uses SuggestInput's custom dropdown; with no
	// matching list it degrades to a plain text input.
	let suggestions = $derived(suggestionsFor(field.name, $vanilla));
	$effect(() => ensureVanilla($targetVersion));

	function addElem() {
		value = [...items, ''];
	}

	function setElem(i: number, v: string) {
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
			<span class="kind">[string[]]</span>
		</span>
		<button type="button" class="add" onclick={addElem}>+ Add</button>
	</div>

	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}
	{#if field.pattern}
		<small class="hint" id={patId}>Pattern: <code>{field.pattern}</code></small>
	{/if}

	{#if items.length === 0}
		<small class="empty">None yet.</small>
	{:else}
		<div class="nested" role="group" aria-labelledby={id}>
			{#each items as item, i (i)}
				<div class="elem">
					<SuggestInput
						value={item}
						{suggestions}
						invalid={!!(field.pattern && item !== '' && !new RegExp(field.pattern).test(item))}
						ariaDescribedby={field.pattern ? patId : undefined}
						mono={false}
						ariaLabel={`${field.label} #${i + 1}`}
						oninput={(v) => setElem(i, v)}
					/>
					<button
						type="button"
						class="remove"
						onclick={() => removeElem(i)}
						aria-label={`Remove #${i + 1}`}
						title="Remove"
					>
						×
					</button>
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
	.hint {
		color: var(--color-text-muted);
		font-size: 0.72rem;
		font-family: var(--font-mono);
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
		align-items: center;
		gap: var(--space-2);
	}
	.elem :global(.sg-combo) {
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
	}
	.remove:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
</style>
