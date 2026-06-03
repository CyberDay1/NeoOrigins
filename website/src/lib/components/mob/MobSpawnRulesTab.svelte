<script lang="ts">
	import {
		draft,
		TIME_OF_DAY,
		TRISTATE,
		SPAWN_REASONS,
		targetVersion,
		type MobOriginDraft
	} from '$lib/stores/mobOriginDraft';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	$effect(() => ensureVanilla($targetVersion));

	// Generic field setter — keeps each onchange terse without a setter per key.
	function set<K extends keyof MobOriginDraft>(key: K, value: MobOriginDraft[K]) {
		draft.update((d) => ({ ...d, [key]: value }));
	}

	function num(e: Event): number {
		return Number((e.currentTarget as HTMLInputElement).value);
	}
	function str(e: Event): string {
		return (e.currentTarget as HTMLInputElement).value;
	}

	function toggleReason(reason: string) {
		draft.update((d) => {
			const has = d.spawnReasons.includes(reason);
			return {
				...d,
				spawnReasons: has
					? d.spawnReasons.filter((r) => r !== reason)
					: [...d.spawnReasons, reason]
			};
		});
	}

	// Biomes is a CSV in-game; model it as a comma-joined text field that splits
	// on save, mirroring MobSpawnRulesTab.pushToDraft.
	let biomesCsv = $derived($draft.locationBiomes.join(', '));
	function setBiomesCsv(v: string) {
		const list = v
			.split(',')
			.map((s) => s.trim())
			.filter((s) => s !== '');
		draft.update((d) => ({ ...d, locationBiomes: list }));
	}

	let enabled = $derived($draft.spawnRulesEnabled);
</script>

<section aria-labelledby="mob-spawn-heading" class="tab">
	<h2 id="mob-spawn-heading">Spawn Rules</h2>
	<p class="lede">When (and how often) this origin rolls onto a freshly-spawned mob.</p>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.spawnRulesEnabled}
				onchange={(e) => set('spawnRulesEnabled', (e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Enable spawn rules</span>
		</label>
		<small class="hint">
			When off, the <code>spawn_rules</code> block is omitted entirely — the origin can still be applied
			by command but never rolls naturally.
		</small>
	</div>

	<fieldset class="group" disabled={!enabled} aria-disabled={!enabled}>
		<legend>Roll</legend>

		<div class="grid2">
			<div class="row">
				<label class="lbl" for="spawn-weight">Weight</label>
				<input
					id="spawn-weight"
					type="number"
					step="0.05"
					min="0"
					value={$draft.weight}
					oninput={(e) => set('weight', num(e))}
				/>
				<small class="hint">Relative chance vs other origins competing for the same mob.</small>
			</div>

			<div class="row">
				<span class="lbl" id="spawn-tod-lbl">Time of day</span>
				<div class="seg" role="radiogroup" aria-labelledby="spawn-tod-lbl">
					{#each TIME_OF_DAY as opt (opt)}
						<button
							type="button"
							role="radio"
							aria-checked={$draft.timeOfDay === opt}
							class="seg-btn"
							class:active={$draft.timeOfDay === opt}
							onclick={() => set('timeOfDay', opt)}
						>
							{opt}
						</button>
					{/each}
				</div>
			</div>
		</div>

		<div class="row">
			<label class="check">
				<input
					type="checkbox"
					checked={$draft.yRangeEnabled}
					onchange={(e) => set('yRangeEnabled', (e.currentTarget as HTMLInputElement).checked)}
				/>
				<span>Restrict Y range</span>
			</label>
			{#if $draft.yRangeEnabled}
				<div class="range-fields">
					<label class="sub" for="spawn-ymin">min</label>
					<input
						id="spawn-ymin"
						type="number"
						value={$draft.yRangeMin}
						oninput={(e) => set('yRangeMin', num(e))}
					/>
					<label class="sub" for="spawn-ymax">max</label>
					<input
						id="spawn-ymax"
						type="number"
						value={$draft.yRangeMax}
						oninput={(e) => set('yRangeMax', num(e))}
					/>
				</div>
			{/if}
		</div>

		<div class="row">
			<label class="check">
				<input
					type="checkbox"
					checked={$draft.lightRangeEnabled}
					onchange={(e) => set('lightRangeEnabled', (e.currentTarget as HTMLInputElement).checked)}
				/>
				<span>Restrict light level</span>
			</label>
			{#if $draft.lightRangeEnabled}
				<div class="range-fields">
					<label class="sub" for="spawn-lmin">min</label>
					<input
						id="spawn-lmin"
						type="number"
						min="0"
						max="15"
						value={$draft.lightRangeMin}
						oninput={(e) => set('lightRangeMin', num(e))}
					/>
					<label class="sub" for="spawn-lmax">max</label>
					<input
						id="spawn-lmax"
						type="number"
						min="0"
						max="15"
						value={$draft.lightRangeMax}
						oninput={(e) => set('lightRangeMax', num(e))}
					/>
				</div>
			{/if}
		</div>

		<div class="row">
			<span class="lbl" id="spawn-reasons-lbl">Spawn reasons</span>
			<small class="hint">Empty = any reason. Pick the spawn causes this origin may roll on.</small>
			<div class="reasons" role="group" aria-labelledby="spawn-reasons-lbl">
				{#each SPAWN_REASONS as reason (reason)}
					{@const on = $draft.spawnReasons.includes(reason)}
					<button
						type="button"
						class="chip"
						class:on
						aria-pressed={on}
						onclick={() => toggleReason(reason)}
					>
						<span class="chip-box" aria-hidden="true">{on ? '\u2713' : ''}</span>
						{reason}
					</button>
				{/each}
			</div>
		</div>

		<div class="grid2">
			<div class="row">
				<label class="lbl" for="spawn-mutex">Mutex group</label>
				<input
					id="spawn-mutex"
					type="text"
					class="mono"
					value={$draft.mutexGroup}
					oninput={(e) => set('mutexGroup', str(e))}
					placeholder="(optional)"
					autocomplete="off"
					spellcheck="false"
				/>
				<small class="hint">Origins sharing a group are mutually exclusive on one mob.</small>
			</div>

			<div class="row">
				<label class="check">
					<input
						type="checkbox"
						checked={$draft.replace}
						onchange={(e) => set('replace', (e.currentTarget as HTMLInputElement).checked)}
					/>
					<span>Replace existing</span>
				</label>
				<small class="hint">Override an origin already on the mob rather than skip.</small>
			</div>
		</div>
	</fieldset>

	<fieldset class="group" disabled={!enabled} aria-disabled={!enabled}>
		<legend>Location filter (optional)</legend>

		<div class="row">
			<label class="lbl" for="loc-dimension">Dimension</label>
			<SuggestInput
				id="loc-dimension"
				value={$draft.locationDimension}
				suggestions={$vanilla.dimensions}
				placeholder="minecraft:overworld"
				ariaLabel="Dimension"
				oninput={(v) => set('locationDimension', v)}
			/>
		</div>

		<div class="grid2">
			<div class="row">
				<label class="lbl" for="loc-biome">Biome</label>
				<SuggestInput
					id="loc-biome"
					value={$draft.locationBiome}
					suggestions={$vanilla.biomes}
					placeholder="minecraft:plains"
					ariaLabel="Biome"
					oninput={(v) => set('locationBiome', v)}
				/>
			</div>
			<div class="row">
				<label class="lbl" for="loc-biome-tag">Biome tag</label>
				<SuggestInput
					id="loc-biome-tag"
					value={$draft.locationBiomeTag}
					suggestions={$vanilla.biomeTags}
					placeholder="minecraft:is_forest"
					ariaLabel="Biome tag"
					oninput={(v) => set('locationBiomeTag', v)}
				/>
			</div>
		</div>

		<div class="row">
			<label class="lbl" for="loc-biomes">Biomes (multiple)</label>
			<input
				id="loc-biomes"
				type="text"
				class="mono"
				value={biomesCsv}
				oninput={(e) => setBiomesCsv(str(e))}
				placeholder="minecraft:plains, minecraft:forest"
				autocomplete="off"
				spellcheck="false"
			/>
			<small class="hint">Comma-separated list of exact biome ids.</small>
		</div>

		<div class="grid2">
			<div class="row">
				<label class="lbl" for="loc-structure">Structure</label>
				<SuggestInput
					id="loc-structure"
					value={$draft.locationStructure}
					suggestions={$vanilla.structures}
					placeholder="minecraft:village_plains"
					ariaLabel="Structure"
					oninput={(v) => set('locationStructure', v)}
				/>
			</div>
			<div class="row">
				<label class="lbl" for="loc-structure-tag">Structure tag</label>
				<SuggestInput
					id="loc-structure-tag"
					value={$draft.locationStructureTag}
					suggestions={$vanilla.structureTags}
					placeholder="minecraft:village"
					ariaLabel="Structure tag"
					oninput={(v) => set('locationStructureTag', v)}
				/>
			</div>
		</div>

		<div class="grid2">
			<div class="row">
				<label class="check">
					<input
						type="checkbox"
						checked={$draft.locationAllowWaterSurface}
						onchange={(e) =>
							set('locationAllowWaterSurface', (e.currentTarget as HTMLInputElement).checked)}
					/>
					<span>Allow water surface</span>
				</label>
			</div>
			<div class="row">
				<label class="check">
					<input
						type="checkbox"
						checked={$draft.locationAllowOceanFloor}
						onchange={(e) =>
							set('locationAllowOceanFloor', (e.currentTarget as HTMLInputElement).checked)}
					/>
					<span>Allow ocean floor</span>
				</label>
			</div>
		</div>

		<div class="row">
			<label class="check">
				<input
					type="checkbox"
					checked={$draft.locationMinYEnabled}
					onchange={(e) =>
						set('locationMinYEnabled', (e.currentTarget as HTMLInputElement).checked)}
				/>
				<span>Restrict minimum Y</span>
			</label>
			{#if $draft.locationMinYEnabled}
				<div class="range-fields">
					<label class="sub" for="loc-miny">min y</label>
					<input
						id="loc-miny"
						type="number"
						value={$draft.locationMinY}
						oninput={(e) => set('locationMinY', num(e))}
					/>
				</div>
			{/if}
		</div>

		<div class="row">
			<label class="check">
				<input
					type="checkbox"
					checked={$draft.locationMaxYEnabled}
					onchange={(e) =>
						set('locationMaxYEnabled', (e.currentTarget as HTMLInputElement).checked)}
				/>
				<span>Restrict maximum Y</span>
			</label>
			{#if $draft.locationMaxYEnabled}
				<div class="range-fields">
					<label class="sub" for="loc-maxy">max y</label>
					<input
						id="loc-maxy"
						type="number"
						value={$draft.locationMaxY}
						oninput={(e) => set('locationMaxY', num(e))}
					/>
				</div>
			{/if}
		</div>

		<div class="row">
			<span class="lbl" id="loc-sky-lbl">Can see sky</span>
			<div class="seg" role="radiogroup" aria-labelledby="loc-sky-lbl">
				{#each TRISTATE as opt (opt)}
					<button
						type="button"
						role="radio"
						aria-checked={$draft.locationCanSeeSky === opt}
						class="seg-btn"
						class:active={$draft.locationCanSeeSky === opt}
						onclick={() => set('locationCanSeeSky', opt)}
					>
						{opt}
					</button>
				{/each}
			</div>
			<small class="hint"><code>any</code> leaves the check unset.</small>
		</div>
	</fieldset>
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}
	h2 {
		margin: 0;
		color: var(--color-text);
		font-size: 1.05rem;
		font-weight: 600;
		letter-spacing: -0.01em;
	}
	.lede {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.85rem;
	}
	.row {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}
	.grid2 {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
		gap: var(--space-4);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
	}
	.sub {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.check {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		color: var(--color-text);
		font-size: 0.88rem;
		cursor: pointer;
	}
	.hint {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	code {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		color: var(--color-text-muted);
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.3rem;
	}
	.group {
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: var(--space-3);
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		margin: 0;
	}
	.group:disabled {
		opacity: 0.55;
	}
	.group legend {
		padding: 0 var(--space-2);
		color: var(--color-text);
		font-size: 0.8rem;
		font-weight: 600;
	}
	.range-fields {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		flex-wrap: wrap;
	}
	.range-fields input {
		width: 6rem;
	}
	input[type='text'],
	input[type='number'] {
		background: var(--color-bg-subtle);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.5rem 0.65rem;
		font: inherit;
		font-size: 0.88rem;
		max-width: 36rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input.mono {
		font-family: var(--font-mono);
	}
	input:hover:not(:disabled) {
		border-color: var(--color-border-strong);
	}
	input:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input[type='checkbox'] {
		accent-color: var(--color-accent);
		width: 1rem;
		height: 1rem;
		cursor: pointer;
	}

	.reasons {
		display: grid;
		grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
		gap: var(--space-2);
		margin-top: var(--space-1);
	}
	.chip {
		display: inline-flex;
		align-items: center;
		gap: var(--space-2);
		background: var(--color-bg-subtle);
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.35rem 0.55rem;
		font: inherit;
		font-size: 0.8rem;
		font-family: var(--font-mono);
		cursor: pointer;
		text-align: left;
		transition: border-color 120ms ease, background 120ms ease, color 120ms ease;
	}
	.chip:hover {
		border-color: var(--color-border-strong);
		color: var(--color-text);
	}
	.chip.on {
		background: var(--color-surface);
		color: var(--color-text);
		border-color: var(--color-accent);
	}
	.chip-box {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1rem;
		height: 1rem;
		border: 1px solid var(--color-border-strong);
		border-radius: var(--radius-sm);
		font-size: 0.72rem;
		color: var(--color-accent);
	}
	.chip.on .chip-box {
		border-color: var(--color-accent);
	}

	.seg {
		display: inline-flex;
		gap: 2px;
		padding: 3px;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		align-self: flex-start;
		max-width: 100%;
		overflow-x: auto;
	}
	.seg-btn {
		background: transparent;
		color: var(--color-text-muted);
		border: none;
		border-radius: var(--radius-sm);
		padding: 0.4rem 0.85rem;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		white-space: nowrap;
		transition: background 120ms ease, color 120ms ease;
	}
	.seg-btn:hover {
		color: var(--color-text);
		background: var(--color-surface-hover);
	}
	.seg-btn.active {
		background: var(--color-surface);
		color: var(--color-text);
		box-shadow: var(--shadow-sm);
	}
</style>
