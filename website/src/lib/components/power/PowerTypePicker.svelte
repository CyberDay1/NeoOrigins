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
	//
	// Options are grouped into <optgroup>s by namespace — see NS_ORDER below.

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

	// The enum is dominated by legacy compat ids: `neoorigins:` natives are ~150 of
	// ~557, the rest are the origins:/apace:/apoli:/apugli: types the compat layer
	// accepts so that imported packs validate. A flat list of that length buries the
	// natives, so group by namespace with `neoorigins:` first and the Apoli-family
	// spellings (which are just aliases of the `origins:` entries above them) last.
	const NS_ORDER = ['neoorigins', 'origins', 'apace', 'apoli', 'apugli'];
	const NS_LABEL: Record<string, string> = {
		neoorigins: 'NeoOrigins (native)',
		origins: 'Origins / Apoli import',
		apace: 'Apace import',
		apoli: 'Apoli namespace (alias of Origins / Apoli import)',
		apugli: 'Apugli namespace (alias of Origins / Apoli import)'
	};

	const groups = $derived.by(() => {
		const byNs = new Map<string, string[]>();
		for (const opt of filtered) {
			const ns = opt.includes(':') ? opt.slice(0, opt.indexOf(':')) : '';
			if (!byNs.has(ns)) byNs.set(ns, []);
			byNs.get(ns)!.push(opt);
		}
		const rank = (ns: string) => {
			const i = NS_ORDER.indexOf(ns);
			return i === -1 ? NS_ORDER.length : i;
		};
		return [...byNs.entries()]
			.sort((a, b) => rank(a[0]) - rank(b[0]) || a[0].localeCompare(b[0]))
			.map(([ns, opts]) => ({ ns, label: NS_LABEL[ns] ?? ns, opts }));
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
		{#each groups as group (group.ns)}
			<optgroup label={group.label}>
				{#each group.opts as opt (opt)}
					<option value={opt}>{opt}</option>
				{/each}
			</optgroup>
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
