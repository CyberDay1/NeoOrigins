<script lang="ts">
	// Single-power card. Owns the id input, type picker, the schema-driven
	// FieldRow list, the remove button, and the collapse toggle.
	//
	// Reactivity model: we don't `bind:value` against the store. The parent
	// PowersTab passes immutable props plus callback fns; this component
	// dispatches each user edit via those callbacks, which call
	// `draft.update(...)` at the store level. This keeps the
	// classic-`writable` idiom that IdentityTab and the rest of the editor
	// use. The FieldRowAdapter is the bridge that converts FieldRow's
	// `bind:value` contract into an `onUpdate` callback.

	import { untrack } from 'svelte';
	import type { PowerDraft } from '$lib/stores/originDraft';
	import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
	import { parsePowerSchema } from '$lib/schema/SchemaFormModel';
	import PowerTypePicker from './PowerTypePicker.svelte';
	import FieldRowAdapter from './FieldRowAdapter.svelte';

	let {
		power,
		index,
		typeOptions,
		schema,
		fieldDocs,
		collapsed,
		onToggleCollapsed,
		onIdChange,
		onTypeChange,
		onFieldChange,
		onRemove
	}: {
		power: PowerDraft;
		index: number;
		typeOptions: string[];
		schema: object;
		fieldDocs: object;
		collapsed: boolean;
		onToggleCollapsed: () => void;
		onIdChange: (next: string) => void;
		onTypeChange: (next: string) => void;
		onFieldChange: (fieldName: string, next: unknown) => void;
		onRemove: () => void;
	} = $props();

	// Parse the schema-driven form spec for the current power type.
	// `parsePowerSchema` throws when the type is not in the enum universe;
	// for safety we wrap in a try and surface the error inline. Past memory
	// notes that some compat types (e.g. `apace:*`, `apoli:*`) may be in
	// the enum but lack a structured `$comment` branch — that case yields
	// only the common root fields and no error.
	let formSpec = $derived.by<{ fields: FormFieldSpec[]; error: string | null }>(() => {
		try {
			return { fields: parsePowerSchema(schema, fieldDocs, power.type), error: null };
		} catch (e) {
			const msg = e instanceof Error ? e.message : String(e);
			return { fields: [], error: msg };
		}
	});

	// "Form fields reset" toast: 2-second inline note after a type change.
	let resetToastVisible = $state(false);
	let resetToastTimer: ReturnType<typeof setTimeout> | null = null;
	let lastSeenType = untrack(() => power.type);

	$effect(() => {
		if (power.type !== lastSeenType) {
			lastSeenType = power.type;
			resetToastVisible = true;
			if (resetToastTimer) clearTimeout(resetToastTimer);
			resetToastTimer = setTimeout(() => {
				resetToastVisible = false;
				resetToastTimer = null;
			}, 2000);
		}
	});

	const headingId = $derived(`power-card-${index}`);
	const bodyId = $derived(`power-card-body-${index}`);
</script>

<article class="card" aria-labelledby={headingId}>
	<header class="card-head">
		<button
			type="button"
			class="caret"
			aria-expanded={!collapsed}
			aria-controls={bodyId}
			onclick={onToggleCollapsed}
			title={collapsed ? 'Expand' : 'Collapse'}
		>
			{collapsed ? '▸' : '▾'}
		</button>
		<h3 id={headingId} class="card-title">
			{power.id || '(unnamed power)'} <span class="ttype">{power.type}</span>
		</h3>
		<button type="button" class="remove" onclick={onRemove} aria-label="Remove power">
			Remove
		</button>
	</header>

	{#if !collapsed}
		<div id={bodyId} class="card-body">
			<div class="row">
				<label class="lbl" for={`power-id-${index}`}>Power id</label>
				<input
					id={`power-id-${index}`}
					type="text"
					value={power.id}
					oninput={(e) => onIdChange((e.currentTarget as HTMLInputElement).value)}
					placeholder="power_1"
					autocomplete="off"
					spellcheck="false"
				/>
				<small class="hint">Local id within this origin.</small>
			</div>

			<div class="row">
				<label class="lbl" for={`power-type-${index}`}>Type</label>
				<PowerTypePicker
					value={power.type}
					options={typeOptions}
					onChange={onTypeChange}
				/>
				{#if resetToastVisible}
					<small class="toast">(form fields reset)</small>
				{/if}
			</div>

			{#if formSpec.error}
				<p class="warn">
					Power type not recognised by the schema; only raw-JSON editing is
					available. <span class="warn-detail">{formSpec.error}</span>
				</p>
			{/if}

			{#if formSpec.fields.length > 0}
				<div class="fields">
					{#each formSpec.fields as field (field.path)}
						<FieldRowAdapter
							{field}
							value={power.fields[field.name]}
							onUpdate={(v) => onFieldChange(field.name, v)}
						/>
					{/each}
				</div>
			{:else if !formSpec.error}
				<p class="note">No structured form fields for this power type — edit the JSON directly in the JSON Preview tab.</p>
			{/if}
		</div>
	{/if}
</article>

<style>
	.card {
		background: #1a1a1a;
		border: 1px solid #2a2a2a;
		border-radius: 4px;
		overflow: hidden;
	}
	.card-head {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.4rem 0.6rem;
		background: #1f1f1f;
		border-bottom: 1px solid #2a2a2a;
	}
	.caret {
		background: transparent;
		border: none;
		color: #b8b8b8;
		font-size: 0.9rem;
		cursor: pointer;
		padding: 0 0.25rem;
	}
	.caret:hover {
		color: #fff;
	}
	.card-title {
		flex: 1;
		margin: 0;
		color: #e6e6e6;
		font-size: 0.95rem;
		font-weight: 500;
		display: flex;
		align-items: baseline;
		gap: 0.5rem;
	}
	.ttype {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.78rem;
		color: #7a7a7a;
	}
	.remove {
		background: #222;
		color: #b8b8b8;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.25rem 0.6rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.8rem;
	}
	.remove:hover {
		border-color: #e25d4a;
		color: #e25d4a;
	}
	.card-body {
		padding: 0.6rem 0.8rem 0.9rem;
		display: flex;
		flex-direction: column;
		gap: 0.6rem;
	}
	.row {
		display: flex;
		flex-direction: column;
		gap: 0.25rem;
	}
	.lbl {
		color: #e6e6e6;
		font-size: 0.9rem;
	}
	.hint {
		color: #999;
		font-size: 0.78rem;
	}
	.toast {
		color: #4ae278;
		font-size: 0.78rem;
		font-style: italic;
	}
	.warn {
		margin: 0;
		padding: 0.4rem 0.55rem;
		background: #2a1f1a;
		border: 1px solid #5a3a2a;
		border-radius: 3px;
		color: #e6c6a8;
		font-size: 0.82rem;
	}
	.warn-detail {
		display: block;
		color: #a88a78;
		font-size: 0.72rem;
		margin-top: 0.2rem;
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	}
	.note {
		margin: 0;
		color: #999;
		font-size: 0.82rem;
		font-style: italic;
	}
	input[type='text'] {
		background: #222;
		color: #e6e6e6;
		border: 1px solid #333;
		border-radius: 3px;
		padding: 0.4rem 0.55rem;
		font: inherit;
		max-width: 24rem;
	}
	input[type='text']:focus {
		outline: none;
		border-color: #4a90e2;
	}
	.fields {
		margin-top: 0.4rem;
		padding-top: 0.4rem;
		border-top: 1px solid #2a2a2a;
	}
</style>
