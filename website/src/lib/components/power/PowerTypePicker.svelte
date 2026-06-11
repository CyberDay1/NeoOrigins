<script lang="ts">
	// Power type dropdown. Lists every value from
	// `power.schema.json#/properties/type/enum`. The schema has duplicates
	// (e.g. `neoorigins:water_breathing` appears twice — once near `flight`,
	// once near `status_effect`); we dedupe to keep the select clean while
	// preserving the schema's declared ordering for the first occurrence.
	//
	// A filter box sits above the select: the native type-ahead is useless
	// here because every option starts with its namespace (typing "res"
	// never reaches `neoorigins:resource`), so the filter matches on any
	// substring instead. Enter picks the match when exactly one remains.

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

	let filter = $state('');

	const uniqueOptions = $derived(Array.from(new Set(options)));

	const filtered = $derived.by(() => {
		const q = filter.trim().toLowerCase();
		if (!q) return uniqueOptions;
		const hits = uniqueOptions.filter((o) => o.toLowerCase().includes(q));
		// Keep the current value selectable even when it doesn't match the
		// filter, so the select never silently jumps to a different type.
		if (value && !hits.includes(value)) hits.unshift(value);
		return hits;
	});

	function onFilterKeydown(e: KeyboardEvent) {
		if (e.key !== 'Enter') return;
		const q = filter.trim().toLowerCase();
		if (!q) return;
		const hits = uniqueOptions.filter((o) => o.toLowerCase().includes(q));
		if (hits.length === 1 && hits[0] !== value) {
			e.preventDefault();
			onChange(hits[0]);
		}
	}
</script>

<div class="wrap">
	<input
		class="filter"
		type="search"
		placeholder="Filter types… (e.g. resource)"
		aria-label="Filter power types"
		{disabled}
		bind:value={filter}
		onkeydown={onFilterKeydown}
	/>
	<select
		class="picker"
		{id}
		{value}
		{disabled}
		onchange={(e) => onChange((e.currentTarget as HTMLSelectElement).value)}
	>
		{#each filtered as opt (opt)}
			<option value={opt}>{opt}</option>
		{/each}
	</select>
	{#if filter.trim() !== ''}
		<small class="count" role="status">
			{filtered.length} match{filtered.length === 1 ? '' : 'es'}
		</small>
	{/if}
</div>

<style>
	.wrap {
		display: flex;
		flex-direction: column;
		gap: 0.3rem;
		max-width: 100%;
	}
	.filter,
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
	.filter:hover,
	.picker:hover {
		border-color: var(--color-border-strong);
	}
	.filter:focus,
	.picker:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	.filter:disabled,
	.picker:disabled {
		opacity: 0.55;
		cursor: not-allowed;
	}
	.count {
		color: var(--color-text-dim, var(--color-text));
		opacity: 0.75;
		font-size: 0.74rem;
	}
</style>
