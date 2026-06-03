<script lang="ts">
	import {
		draft,
		fullId,
		NAMESPACE_PATTERN,
		PATH_PATTERN,
		targetVersion,
		TARGET_VERSIONS,
		type TargetMcVersion
	} from '$lib/stores/mobOriginDraft';
	import { vanilla, ensureVanilla } from '$lib/data/vanilla';
	import SuggestInput from '$lib/widgets/SuggestInput.svelte';

	$effect(() => ensureVanilla($targetVersion));

	let namespaceInvalid = $derived(
		$draft.namespace !== '' && !NAMESPACE_PATTERN.test($draft.namespace)
	);
	let pathInvalid = $derived($draft.path !== '' && !PATH_PATTERN.test($draft.path));
	let previewFullId = $derived(fullId($draft));

	// Mutual exclusion mirrors the in-game creator: set exactly one of
	// entity type / entity tag. `entity_types` (multi) is import/JSON-only.
	let hasType = $derived($draft.targetEntityType.trim() !== '');
	let hasTag = $derived($draft.targetEntityTag.trim() !== '');
	let hasMulti = $derived($draft.targetEntityTypes.length > 0);
	let targetUnset = $derived(!hasType && !hasTag && !hasMulti);

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
	function setTargetType(v: string) {
		// Setting a type clears the tag (and any multi list) — exactly one form.
		draft.update((d) => ({ ...d, targetEntityType: v, targetEntityTag: v ? '' : d.targetEntityTag }));
	}
	function setTargetTag(v: string) {
		draft.update((d) => ({ ...d, targetEntityTag: v, targetEntityType: v ? '' : d.targetEntityType }));
	}
	function clearMulti() {
		draft.update((d) => ({ ...d, targetEntityTypes: [] }));
	}
	function setTargetVersion(v: TargetMcVersion) {
		targetVersion.set(v);
	}
</script>

<section aria-labelledby="mob-identity-heading" class="tab">
	<h2 id="mob-identity-heading">Identity</h2>

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
		<small class="hint">Stamps <code>pack.mcmeta</code> at export.</small>
	</div>

	<div class="row">
		<span class="lbl">Id</span>
		<div class="id-fields">
			<input
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
				aria-describedby={`mob-id-hint${namespaceInvalid ? ' mob-namespace-err' : ''}`}
			/>
			<span class="colon" aria-hidden="true">:</span>
			<input
				type="text"
				class="mono path"
				class:invalid={pathInvalid}
				value={$draft.path}
				oninput={(e) => setPath((e.currentTarget as HTMLInputElement).value)}
				placeholder="wraith"
				autocomplete="off"
				spellcheck="false"
				aria-label="Path"
				aria-invalid={pathInvalid || undefined}
				aria-describedby={`mob-id-hint${pathInvalid ? ' mob-path-err' : ''}`}
			/>
		</div>
		<small class="hint" id="mob-id-hint">
			Written to <code>data/&lt;namespace&gt;/origins/mob_origins/&lt;path&gt;.json</code>.
		</small>
		{#if namespaceInvalid}
			<small class="err" id="mob-namespace-err">namespace must match <code>{NAMESPACE_PATTERN.source}</code></small>
		{/if}
		{#if pathInvalid}
			<small class="err" id="mob-path-err">path must match <code>{PATH_PATTERN.source}</code></small>
		{/if}
		<small class="preview">Full id: <code>{previewFullId}</code></small>
	</div>

	<div class="row">
		<label class="lbl" for="mob-name">Name</label>
		<input
			id="mob-name"
			type="text"
			value={$draft.name}
			oninput={(e) => setName((e.currentTarget as HTMLInputElement).value)}
		/>
		<small class="hint">Shown in the mob-origin browser (never in-world)</small>
	</div>

	<div class="row">
		<label class="lbl" for="mob-description">Description</label>
		<textarea
			id="mob-description"
			rows="3"
			value={$draft.description}
			oninput={(e) => setDescription((e.currentTarget as HTMLTextAreaElement).value)}
		></textarea>
	</div>

	<div class="row">
		<label class="lbl" for="mob-icon">Icon</label>
		<SuggestInput
			id="mob-icon"
			value={$draft.icon}
			suggestions={$vanilla.items}
			placeholder="minecraft:zombie_spawn_egg"
			ariaLabel="Icon item id"
			oninput={setIcon}
		/>
		<small class="hint">Item id used as the browser icon</small>
	</div>

	<fieldset class="target">
		<legend>Target — set exactly one</legend>

		{#if hasMulti}
			<div class="row">
				<span class="lbl">Entity types (multi)</span>
				<small class="multi">
					<code>{$draft.targetEntityTypes.join(', ')}</code>
				</small>
				<button type="button" class="link" onclick={clearMulti}>Clear list</button>
				<small class="hint">
					A multi-type target (from import). Editing here is single-type only — clear
					the list to switch to a single entity or tag.
				</small>
			</div>
		{:else}
			<div class="row">
				<label class="lbl" for="mob-target-type">Entity type</label>
				<SuggestInput
					id="mob-target-type"
					value={$draft.targetEntityType}
					suggestions={$vanilla.entities}
					disabled={hasTag}
					placeholder="minecraft:zombie"
					ariaLabel="Entity type"
					oninput={setTargetType}
				/>
				<small class="hint">A single exact entity type.</small>
			</div>

			<div class="row">
				<label class="lbl" for="mob-target-tag">Entity tag</label>
				<SuggestInput
					id="mob-target-tag"
					value={$draft.targetEntityTag}
					suggestions={$vanilla.entityTags}
					disabled={hasType}
					placeholder="minecraft:undead"
					ariaLabel="Entity tag"
					oninput={setTargetTag}
				/>
				<small class="hint">OR an entity-type tag (leave entity type blank).</small>
			</div>
		{/if}

		{#if targetUnset}
			<small class="err">A target is required — set an entity type or tag.</small>
		{/if}
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
	.multi {
		color: var(--color-text-muted);
		font-size: 0.8rem;
	}
	.link {
		align-self: flex-start;
		background: none;
		border: none;
		padding: 0;
		color: var(--color-accent);
		font: inherit;
		font-size: 0.78rem;
		cursor: pointer;
		text-decoration: underline;
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
	.target {
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: var(--space-3);
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
		margin: 0;
	}
	.target legend {
		padding: 0 var(--space-2);
		color: var(--color-text);
		font-size: 0.8rem;
		font-weight: 600;
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
	textarea {
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
		min-height: 4rem;
		line-height: 1.5;
	}
	input.mono {
		font-family: var(--font-mono);
	}
	input:hover:not(:disabled),
	textarea:hover {
		border-color: var(--color-border-strong);
	}
	input:focus,
	textarea:focus {
		border-color: var(--color-accent);
		background: var(--color-surface);
	}
	input:disabled {
		opacity: 0.5;
		cursor: not-allowed;
	}
	input.invalid {
		border-color: var(--color-danger);
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
