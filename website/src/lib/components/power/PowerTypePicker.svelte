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
		onChange
	}: {
		value: string;
		options: string[];
		disabled?: boolean;
		onChange: (next: string) => void;
	} = $props();

	const uniqueOptions = $derived(Array.from(new Set(options)));
</script>

<select
	class="picker"
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
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.35rem 0.5rem;
		font: inherit;
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.85rem;
		min-width: 22rem;
	}
	.picker:focus {
		outline: none;
		border-color: #4a90e2;
	}
	.picker:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}
</style>
