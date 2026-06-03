<script lang="ts">
	import {
		draft,
		fullId,
		KNOWN_LAYERS,
		NAMESPACE_PATTERN,
		PATH_PATTERN,
		targetVersion,
		TARGET_VERSIONS,
		type TargetMcVersion
	} from '$lib/stores/originDraft';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	// Load the vanilla item list for the icon field's typeahead.
	$effect(() => ensureVanilla($targetVersion));

	const CLASS_LAYER_ID = 'neoorigins:class';

	// Two-field id model, mirroring the in-game Java editor
	// (`OriginDraft.idPath` + a separately-managed `CUSTOM_NAMESPACE`).
	// `namespace` follows the Minecraft namespace rule; `path` additionally
	// allows `/` for subfolders. The full schema regex is stricter and will
	// be enforced at JSON export time.

	const IMPACTS = ['none', 'low', 'medium', 'high'] as const;
	type Impact = (typeof IMPACTS)[number];
	const IMPACT_LABELS: Record<Impact, string> = {
		none: 'None',
		low: 'Low',
		medium: 'Medium',
		high: 'High'
	};

	let namespaceInvalid = $derived(
		$draft.namespace !== '' && !NAMESPACE_PATTERN.test($draft.namespace)
	);
	let pathInvalid = $derived($draft.path !== '' && !PATH_PATTERN.test($draft.path));
	let previewFullId = $derived(fullId($draft));

	function setNamespace(v: string) {
		draft.update((d) => ({ ...d, namespace: v }));
	}
	function setPath(v: string) {
		draft.update((d) => ({ ...d, path: v }));
	}
	function setName(v: string) {
		draft.update((d) => ({ ...d, name: v }));
	}
	function setDescription(v: string) {
		draft.update((d) => ({ ...d, description: v }));
	}
	function setIcon(v: string) {
		draft.update((d) => ({ ...d, icon: v }));
	}
	function setImpact(v: Impact) {
		draft.update((d) => ({ ...d, impact: v }));
	}
	function setOrder(v: number) {
		draft.update((d) => ({ ...d, order: Number.isFinite(v) ? v : 0 }));
	}
	function setUnchoosable(v: boolean) {
		draft.update((d) => ({ ...d, unchoosable: v }));
	}
	function setHidden(v: boolean) {
		draft.update((d) => ({ ...d, hidden: v }));
	}
	function setLayerId(v: string) {
		draft.update((d) => ({ ...d, layerId: v }));
	}
	function setTargetVersion(v: TargetMcVersion) {
		targetVersion.set(v);
	}
</script>

<section aria-labelledby="identity-heading" class="tab">
	<h2 id="identity-heading">Identity</h2>

	<div class="row">
		<span class="lbl">Target Minecraft version</span>
		<div class="seg" role="radiogroup" aria-label="Target Minecraft version">
			{#each TARGET_VERSIONS as v (v.id)}
				<button
					type="button"
					role="radio"
					aria-checked={$targetVersion === v.id}
					class="seg-btn"
					class:active={$targetVersion === v.id}
					onclick={() => setTargetVersion(v.id)}
				>
					{v.label}
					<small class="seg-pf">pack_format {v.packFormat}</small>
				</button>
			{/each}
		</div>
		<small class="hint">
			Stamps <code>pack.mcmeta</code> at export. The editor does NOT translate
			power types between versions — pick the line you're authoring for and
			stick to power types that exist there.
		</small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-layer">Layer</label>
		<select
			id="origin-layer"
			value={$draft.layerId}
			onchange={(e) => setLayerId((e.currentTarget as HTMLSelectElement).value)}
		>
			{#each KNOWN_LAYERS as l (l.id)}
				<option value={l.id}>{l.label} ({l.id})</option>
			{/each}
		</select>
		{#if $draft.layerId === CLASS_LAYER_ID}
			<small class="accent">This origin will be a CLASS (neoorigins:class layer).</small>
		{:else}
			<small class="hint">Appears as a normal origin in the chosen picker.</small>
		{/if}
	</div>

	<div class="row">
		<span class="lbl">Id</span>
		<div class="id-fields">
			<input
				id="origin-namespace"
				type="text"
				class="mono ns"
				class:invalid={namespaceInvalid}
				value={$draft.namespace}
				oninput={(e) => setNamespace((e.currentTarget as HTMLInputElement).value)}
				placeholder="neoorigins"
				autocomplete="off"
				spellcheck="false"
				aria-label="Namespace"
				aria-invalid={namespaceInvalid || undefined}
				aria-describedby={`origin-id-hint${namespaceInvalid ? ' origin-namespace-err' : ''}`}
			/>
			<span class="colon" aria-hidden="true">:</span>
			<input
				id="origin-path"
				type="text"
				class="mono path"
				class:invalid={pathInvalid}
				value={$draft.path}
				oninput={(e) => setPath((e.currentTarget as HTMLInputElement).value)}
				placeholder="wizard"
				autocomplete="off"
				spellcheck="false"
				aria-label="Path"
				aria-invalid={pathInvalid || undefined}
				aria-describedby={`origin-id-hint${pathInvalid ? ' origin-path-err' : ''}`}
			/>
		</div>
		<small class="hint" id="origin-id-hint">
			<strong>Namespace</strong> defaults to <code>neoorigins</code>;
			<strong>path</strong> is the origin's id within that namespace, e.g.
			<code>wizard</code>.
		</small>
		{#if namespaceInvalid}
			<small class="err" id="origin-namespace-err">namespace must match <code>{NAMESPACE_PATTERN.source}</code></small>
		{/if}
		{#if pathInvalid}
			<small class="err" id="origin-path-err">path must match <code>{PATH_PATTERN.source}</code></small>
		{/if}
		<small class="preview">Full id: <code>{previewFullId}</code></small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-name">Name</label>
		<input
			id="origin-name"
			type="text"
			value={$draft.name}
			oninput={(e) => setName((e.currentTarget as HTMLInputElement).value)}
		/>
		<small class="hint">Display name</small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-description">Description</label>
		<textarea
			id="origin-description"
			rows="4"
			value={$draft.description}
			oninput={(e) => setDescription((e.currentTarget as HTMLTextAreaElement).value)}
		></textarea>
	</div>

	<div class="row">
		<label class="lbl" for="origin-icon">Icon</label>
		<SuggestInput
			id="origin-icon"
			value={$draft.icon}
			suggestions={$vanilla.items}
			ariaLabel="Icon item id"
			placeholder="minecraft:player_head"
			oninput={(v) => setIcon(v)}
		/>
		<small class="hint">
			Item id shown next to the origin, e.g. <code>minecraft:player_head</code>.
		</small>
	</div>

	<div class="row">
		<label class="lbl" for="origin-impact">Impact</label>
		<select
			id="origin-impact"
			value={$draft.impact}
			onchange={(e) => setImpact((e.currentTarget as HTMLSelectElement).value as Impact)}
		>
			{#each IMPACTS as i (i)}
				<option value={i}>{IMPACT_LABELS[i]}</option>
			{/each}
		</select>
	</div>

	<div class="row">
		<label class="lbl" for="origin-order">Order</label>
		<input
			id="origin-order"
			type="number"
			step="1"
			value={$draft.order}
			oninput={(e) => setOrder(parseInt((e.currentTarget as HTMLInputElement).value, 10))}
		/>
	</div>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.unchoosable}
				onchange={(e) => setUnchoosable((e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Unchoosable</span>
		</label>
		<small class="hint">Hidden from origin selection screen</small>
	</div>

	<div class="row">
		<label class="check">
			<input
				type="checkbox"
				checked={$draft.hidden}
				onchange={(e) => setHidden((e.currentTarget as HTMLInputElement).checked)}
			/>
			<span>Hidden</span>
		</label>
		<small class="hint">Excluded from listings (developer/testing)</small>
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
	.row {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
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
	.err {
		color: var(--color-danger);
		font-size: 0.78rem;
	}
	.preview {
		color: var(--color-text-muted);
		font-size: 0.78rem;
	}
	.accent {
		color: var(--color-accent);
		font-size: 0.78rem;
	}
	.id-fields {
		display: flex;
		align-items: center;
		gap: var(--space-1);
		max-width: 36rem;
	}
	.id-fields .colon {
		color: var(--color-text-subtle);
		font-family: var(--font-mono);
		font-size: 0.95rem;
	}
	.id-fields input.ns {
		flex: 0 0 11rem;
		min-width: 6rem;
	}
	.id-fields input.path {
		flex: 1 1 auto;
		min-width: 6rem;
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
	input[type='text'],
	input[type='number'],
	textarea,
	select {
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
	textarea {
		resize: vertical;
		min-height: 5rem;
		line-height: 1.5;
	}
	input.mono {
		font-family: var(--font-mono);
	}
	input:hover,
	textarea:hover,
	select:hover {
		border-color: var(--color-border-strong);
	}
	input:focus,
	textarea:focus,
	select:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input.invalid {
		border-color: var(--color-danger);
	}
	input[type='checkbox'] {
		accent-color: var(--color-accent);
		width: 1rem;
		height: 1rem;
		cursor: pointer;
	}

	/* Segmented control — pill style. */
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
		padding: 0.45rem 0.85rem;
		font: inherit;
		font-size: 0.85rem;
		font-weight: 500;
		cursor: pointer;
		display: inline-flex;
		flex-direction: column;
		align-items: flex-start;
		gap: 0.15rem;
		line-height: 1.1;
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
	.seg-btn .seg-pf {
		color: var(--color-text-subtle);
		opacity: 0.85;
		font-size: 0.7rem;
		font-family: var(--font-mono);
		font-weight: 400;
	}
	.seg-btn.active .seg-pf {
		color: var(--color-accent);
		opacity: 1;
	}
</style>
