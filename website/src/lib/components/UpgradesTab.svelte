<script lang="ts">
	// Upgrades tab — edit the optional `upgrades[]` progression chain.
	//
	// Schema shape (from `docs/schema/origin.schema.json`, shared between
	// the origin and class layers — `examples/class_tier_up/` is the
	// canonical class case, but normal origins are allowed upgrades too):
	//
	//   upgrades: [
	//     {
	//       advancement: "<namespace>:<path>",  // required
	//       origin:      "<namespace>:<path>",  // required — next origin
	//       announcement: "string"              // optional chat line
	//     }
	//   ]
	//
	// `draft.upgrades` starts UNDEFINED — we only allocate the array on
	// the first "Add upgrade" click so the serializer can cleanly omit
	// the field when no entries exist.

	import { draft, RESOURCE_LOCATION_PATTERN } from '$lib/stores/originDraft';

	function addUpgrade() {
		draft.update((d) => {
			const list = d.upgrades ? [...d.upgrades] : [];
			list.push({ advancement: '', origin: '', announcement: '' });
			return { ...d, upgrades: list };
		});
	}

	function removeUpgrade(i: number) {
		draft.update((d) => {
			if (!d.upgrades) return d;
			const list = d.upgrades.filter((_, idx) => idx !== i);
			// Drop back to undefined when emptied — keeps the serializer
			// path identical to the "user never added one" case.
			return { ...d, upgrades: list.length > 0 ? list : undefined };
		});
	}

	function setField(i: number, key: 'advancement' | 'origin' | 'announcement', v: string) {
		draft.update((d) => {
			if (!d.upgrades) return d;
			const list = d.upgrades.map((u, idx) =>
				idx === i ? { ...u, [key]: v } : u
			);
			return { ...d, upgrades: list };
		});
	}

	function isInvalidResLoc(v: string): boolean {
		return v !== '' && !RESOURCE_LOCATION_PATTERN.test(v);
	}
</script>

<section aria-labelledby="upgrades-heading" class="tab">
	<h2 id="upgrades-heading">Upgrades</h2>
	<p class="hint">
		Optional progression chain. When the player earns the listed advancement
		they're upgraded into the named origin. Shared between origin and class
		layers — used by <code>examples/class_tier_up/</code> but allowed on
		normal origins too.
	</p>

	{#if !$draft.upgrades || $draft.upgrades.length === 0}
		<p class="empty">No upgrades. Click below to add one.</p>
	{:else}
		<ul class="rows">
			{#each $draft.upgrades as upg, i (i)}
				<li class="upg-row">
					<div class="upg-fields">
						<div class="field">
							<label class="lbl" for={`upg-adv-${i}`}>Advancement</label>
							<input
								id={`upg-adv-${i}`}
								type="text"
								class="mono"
								class:invalid={isInvalidResLoc(upg.advancement)}
								value={upg.advancement}
								oninput={(e) =>
									setField(i, 'advancement', (e.currentTarget as HTMLInputElement).value)}
								placeholder="mypack:wizard/tier_1"
								autocomplete="off"
								spellcheck="false"
							/>
						</div>
						<div class="field">
							<label class="lbl" for={`upg-origin-${i}`}>Next origin</label>
							<input
								id={`upg-origin-${i}`}
								type="text"
								class="mono"
								class:invalid={isInvalidResLoc(upg.origin)}
								value={upg.origin}
								oninput={(e) =>
									setField(i, 'origin', (e.currentTarget as HTMLInputElement).value)}
								placeholder="mypack:archmage"
								autocomplete="off"
								spellcheck="false"
							/>
						</div>
						<div class="field">
							<label class="lbl" for={`upg-anno-${i}`}>Announcement (optional)</label>
							<input
								id={`upg-anno-${i}`}
								type="text"
								value={upg.announcement ?? ''}
								oninput={(e) =>
									setField(
										i,
										'announcement',
										(e.currentTarget as HTMLInputElement).value
									)}
								placeholder="%s has ascended to Archmage!"
							/>
						</div>
					</div>
					<button type="button" class="remove" onclick={() => removeUpgrade(i)}>
						Remove
					</button>
				</li>
			{/each}
		</ul>
	{/if}

	<div class="actions">
		<button type="button" class="add" onclick={addUpgrade}>+ Add upgrade</button>
	</div>
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: 1rem;
	}
	h2 {
		margin: 0 0 0.25rem;
		color: #e6e6e6;
	}
	.hint {
		color: #999;
		font-size: 0.85rem;
		max-width: 48rem;
	}
	.empty {
		color: #999;
		font-style: italic;
		margin: 0;
	}
	.rows {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: 0.75rem;
	}
	.upg-row {
		display: flex;
		gap: 0.75rem;
		align-items: flex-start;
		background: #1a1a1a;
		border: 1px solid #2a2a2a;
		border-radius: 4px;
		padding: 0.75rem;
	}
	.upg-fields {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 0.6rem 0.75rem;
		flex: 1 1 auto;
	}
	.field {
		display: flex;
		flex-direction: column;
		gap: 0.2rem;
	}
	.field:last-child {
		grid-column: 1 / -1;
	}
	.lbl {
		color: #e6e6e6;
		font-size: 0.85rem;
	}
	code {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.78rem;
		color: #b8b8b8;
	}
	input[type='text'] {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.55rem;
		font: inherit;
		width: 100%;
		box-sizing: border-box;
	}
	input.mono {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	}
	input:focus {
		outline: none;
		border-color: #4a90e2;
	}
	input.invalid {
		border-color: #e25d4a;
	}
	.actions {
		display: flex;
	}
	.add {
		background: #1a1a1a;
		color: #e6e6e6;
		border: 1px solid #4a90e2;
		border-radius: 3px;
		padding: 0.4rem 0.9rem;
		cursor: pointer;
		font: inherit;
	}
	.add:hover {
		background: #4a90e2;
		color: #fff;
	}
	.remove {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.7rem;
		cursor: pointer;
		font: inherit;
		flex: 0 0 auto;
	}
	.remove:hover {
		border-color: #e25d4a;
		color: #e25d4a;
	}
</style>
