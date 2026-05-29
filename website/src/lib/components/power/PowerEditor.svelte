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
	<header
		class="card-head"
		role="button"
		tabindex="0"
		aria-expanded={!collapsed}
		aria-controls={bodyId}
		onclick={onToggleCollapsed}
		onkeydown={(e) => {
			if (e.key === 'Enter' || e.key === ' ') {
				e.preventDefault();
				onToggleCollapsed();
			}
		}}
		title={collapsed ? 'Click to expand' : 'Click to collapse'}
	>
		<span class="caret" aria-hidden="true">
			{collapsed ? '▸' : '▾'}
		</span>
		<h3 id={headingId} class="card-title">
			{power.id || '(unnamed power)'} <span class="ttype">{power.type}</span>
		</h3>
		<button
			type="button"
			class="remove"
			onclick={(e) => {
				e.stopPropagation();
				onRemove();
			}}
			aria-label="Remove power"
		>
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
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		overflow: hidden;
		transition: border-color 120ms ease;
	}
	.card:hover {
		border-color: var(--color-border-strong);
	}
	.card-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		padding: 0.5rem 0.7rem;
		background: var(--color-surface);
		border-bottom: 1px solid var(--color-border);
		cursor: pointer;
		user-select: none;
		transition: background 120ms ease;
	}
	.card-head:hover {
		background: var(--color-surface-hover);
	}
	.card-head:focus-visible {
		outline: 2px solid var(--color-accent);
		outline-offset: -2px;
	}
	.caret {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 1rem;
		color: var(--color-text-muted);
		font-size: 0.9rem;
		transition: color 120ms ease;
	}
	.card-head:hover .caret {
		color: var(--color-text);
	}
	.card-title {
		flex: 1;
		margin: 0;
		color: var(--color-text);
		font-size: 0.92rem;
		font-weight: 600;
		display: flex;
		align-items: baseline;
		gap: var(--space-2);
		min-width: 0;
	}
	.ttype {
		font-family: var(--font-mono);
		font-size: 0.76rem;
		font-weight: 400;
		color: var(--color-text-subtle);
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.1rem 0.4rem;
		overflow-wrap: anywhere;
	}
	.remove {
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.3rem 0.65rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.78rem;
		font-weight: 500;
		transition: background 120ms ease, color 120ms ease, border-color 120ms ease;
	}
	.remove:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
	.card-body {
		padding: var(--space-3) var(--space-4);
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
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
	.toast {
		color: var(--color-success);
		font-size: 0.78rem;
		font-style: italic;
	}
	.warn {
		margin: 0;
		padding: var(--space-2) var(--space-3);
		background: var(--color-warning-subtle);
		border: 1px solid color-mix(in srgb, var(--color-warning) 40%, var(--color-border));
		border-radius: var(--radius-md);
		color: var(--color-text);
		font-size: 0.82rem;
	}
	.warn-detail {
		display: block;
		color: var(--color-text-muted);
		font-size: 0.72rem;
		margin-top: 0.25rem;
		font-family: var(--font-mono);
	}
	.note {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.82rem;
		font-style: italic;
	}
	input[type='text'] {
		background: var(--color-bg);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.45rem 0.6rem;
		font: inherit;
		font-size: 0.86rem;
		max-width: 26rem;
		transition: border-color 120ms ease, background 120ms ease;
	}
	input[type='text']:hover {
		border-color: var(--color-border-strong);
	}
	input[type='text']:focus {
		border-color: var(--color-accent);
	}
	.fields {
		margin-top: var(--space-1);
		padding-top: var(--space-2);
		border-top: 1px solid var(--color-border);
	}
</style>
