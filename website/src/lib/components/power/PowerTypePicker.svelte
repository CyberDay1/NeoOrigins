<script lang="ts">
	// Power type dropdown. Lists every value from
	// `power.schema.json#/properties/type/enum`. The schema has duplicates
	// (e.g. `neoorigins:water_breathing` appears twice — once near `flight`,
	// once near `status_effect`); we dedupe to keep the select clean while
	// preserving the schema's declared ordering for the first occurrence.

	let {
		value,
		options,
		disabled = false,
		id = undefined,
		onChange
	}: {
		value: string;
		options: string[];
		disabled?: boolean;
		id?: string;
		onChange: (next: string) => void;
	} = $props();

	const uniqueOptions = $derived(Array.from(new Set(options)));
</script>

<select
	class="picker"
	{id}
	{value}
	{disabled}
	onchange={(e) => onChange((e.currentTarget as HTMLSelectElement).value)}
>
	{#each uniqueOptions as opt (opt)}
		<option value={opt}>{opt}</option>
	{/each}
</select>

<style>
	.picker {
		background: var(--color-bg);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.45rem 0.6rem;
		font: inherit;
		font-family: var(--font-mono);
		font-size: 0.84rem;
		min-width: 24rem;
		max-width: 100%;
		transition: border-color 120ms ease, background 120ms ease;
	}
	.picker:hover {
		border-color: var(--color-border-strong);
	}
	.picker:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	.picker:disabled {
		opacity: 0.55;
		cursor: not-allowed;
	}
</style>
