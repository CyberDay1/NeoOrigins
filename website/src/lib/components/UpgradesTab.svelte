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
								aria-invalid={isInvalidResLoc(upg.advancement) || undefined}
								aria-describedby={isInvalidResLoc(upg.advancement) ? `upg-adv-err-${i}` : undefined}
							/>
							{#if isInvalidResLoc(upg.advancement)}
								<small class="err" id={`upg-adv-err-${i}`}>
									Must be a valid resource location, e.g. <code>mypack:wizard/tier_1</code>.
								</small>
							{/if}
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
								aria-invalid={isInvalidResLoc(upg.origin) || undefined}
								aria-describedby={isInvalidResLoc(upg.origin) ? `upg-origin-err-${i}` : undefined}
							/>
							{#if isInvalidResLoc(upg.origin)}
								<small class="err" id={`upg-origin-err-${i}`}>
									Must be a valid resource location, e.g. <code>mypack:archmage</code>.
								</small>
							{/if}
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
		gap: var(--space-4);
	}
	h2 {
		margin: 0;
		color: var(--color-text);
		font-size: 1.05rem;
		font-weight: 600;
		letter-spacing: -0.01em;
	}
	.hint {
		color: var(--color-text-muted);
		font-size: 0.85rem;
		max-width: 56rem;
		margin: 0;
	}
	.empty {
		color: var(--color-text-muted);
		font-style: italic;
		margin: 0;
		padding: var(--space-4);
		border: 1px dashed var(--color-border);
		border-radius: var(--radius-md);
		text-align: center;
	}
	.rows {
		list-style: none;
		padding: 0;
		margin: 0;
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}
	.upg-row {
		display: flex;
		gap: var(--space-3);
		align-items: flex-start;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: var(--space-3);
	}
	.upg-fields {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: var(--space-2) var(--space-3);
		flex: 1 1 auto;
		min-width: 0;
	}
	.field {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		min-width: 0;
	}
	.field:last-child {
		grid-column: 1 / -1;
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.82rem;
		font-weight: 500;
	}
	.err {
		color: var(--color-danger);
		font-size: 0.76rem;
	}
	.err code {
		background: var(--color-bg);
	}
	code {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		color: var(--color-text-muted);
		background: var(--color-bg);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.3rem;
	}
	input[type='text'] {
		background: var(--color-bg);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.45rem 0.6rem;
		font: inherit;
		font-size: 0.86rem;
		width: 100%;
		box-sizing: border-box;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input.mono {
		font-family: var(--font-mono);
	}
	input:hover {
		border-color: var(--color-border-strong);
	}
	input:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input.invalid {
		border-color: var(--color-danger);
	}
	.actions {
		display: flex;
	}
	.add {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
		border: 1px solid var(--color-accent);
		border-radius: var(--radius-md);
		padding: 0.5rem 0.95rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		transition: background 120ms ease;
	}
	.add:hover {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}
	.remove {
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.45rem 0.8rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.82rem;
		flex: 0 0 auto;
		transition: background 120ms ease, color 120ms ease, border-color 120ms ease;
	}
	.remove:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
</style>
