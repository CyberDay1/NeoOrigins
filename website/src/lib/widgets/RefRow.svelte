<script lang="ts">
	// Recursive sub-form for a cross-document action/condition `$ref` (D4).
	//
	// Renders a type picker over the referenced schema's `type.enum`; once a
	// type is chosen, `parseRefSchema` yields that branch's own FormFieldSpec
	// list, rendered inline through FieldRowAdapter → FieldRow — which dispatches
	// nested REF / ARRAY_REF back here, giving the full recursive editor the
	// in-game Java creator ships. The bound value is the nested action/condition
	// OBJECT (`{type, …}`) or `null` when unset, so it serializes straight into
	// the wire JSON.

	import type { RefFieldSpec, FormFieldSpec } from '$lib/schema/FormFieldSpec';
	import { parseRefSchema, refTypeOptions } from '$lib/schema/SchemaFormModel';
	import { getRefSchemas, docFor } from '$lib/schema/refSchemaContext';
	import FieldRowAdapter from '$lib/components/power/FieldRowAdapter.svelte';

	let {
		field,
		value = $bindable()
	}: { field: RefFieldSpec; value: Record<string, unknown> | null } = $props();

	const schemas = getRefSchemas();
	const doc = $derived(schemas ? docFor(schemas, field.refDoc) : null);
	const typeOptions = $derived(doc ? refTypeOptions(doc) : []);

	const currentType = $derived(
		value && typeof value === 'object' && typeof value['type'] === 'string'
			? (value['type'] as string)
			: ''
	);

	// Resolve the chosen type's nested fields. Mirrors PowerEditor's guarded
	// parse: an unknown type id throws, surfaced inline rather than crashing.
	const nested = $derived.by<{ fields: FormFieldSpec[]; error: string | null }>(() => {
		if (!doc || !schemas || !currentType) return { fields: [], error: null };
		try {
			return { fields: parseRefSchema(field.refDoc, doc, schemas.fieldDocs, currentType), error: null };
		} catch (e) {
			return { fields: [], error: e instanceof Error ? e.message : String(e) };
		}
	});

	let collapsed = $state(false);

	const id = $derived(`ref-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);

	function onTypeChange(next: string) {
		// Wipe sibling fields on type change — the new branch's property set is
		// usually disjoint (same rule as the power type picker).
		value = next === '' ? null : { type: next };
	}

	function childValue(name: string): unknown {
		return value ? value[name] : undefined;
	}

	function updateChild(name: string, v: unknown) {
		value = { ...(value ?? { type: currentType }), [name]: v };
	}
</script>

<div class="ref">
	<div class="ref-head">
		<label class="lbl" for={id}>
			{field.label}
			{#if field.required}<span class="req" aria-label="required">*</span>{/if}
			<span class="kind">[{field.refDoc}]</span>
		</label>
		<select
			{id}
			value={currentType}
			onchange={(e) => onTypeChange((e.currentTarget as HTMLSelectElement).value)}
		>
			<option value="">(none)</option>
			{#each typeOptions as opt (opt)}
				<option value={opt}>{opt}</option>
			{/each}
		</select>
		{#if currentType && nested.fields.length > 0}
			<button
				type="button"
				class="toggle"
				aria-expanded={!collapsed}
				onclick={() => (collapsed = !collapsed)}
				title={collapsed ? 'Expand' : 'Collapse'}
			>
				{collapsed ? '▸' : '▾'}
			</button>
		{/if}
	</div>

	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}

	{#if !schemas}
		<small class="err">Action/condition schema not loaded — cannot edit inline.</small>
	{:else if nested.error}
		<small class="err">{nested.error}</small>
	{:else if currentType && nested.fields.length > 0 && !collapsed}
		<div class="nested">
			{#each nested.fields as f (f.path)}
				<FieldRowAdapter
					field={f}
					value={childValue(f.name)}
					onUpdate={(v) => updateChild(f.name, v)}
				/>
			{/each}
		</div>
	{/if}
</div>

<style>
	.ref {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
	}
	.ref-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		flex-wrap: wrap;
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
		min-width: 11rem;
	}
	.req {
		color: var(--color-accent);
		margin-left: 0.2rem;
	}
	.kind {
		color: var(--color-text-subtle);
		font-size: 0.7rem;
		font-weight: normal;
		font-family: var(--font-mono);
		margin-left: 0.3rem;
	}
	select {
		background: var(--color-bg);
		color: var(--color-text);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		padding: 0.4rem 0.5rem;
		font: inherit;
		font-size: 0.84rem;
		min-width: 14rem;
		transition: border-color 120ms ease;
	}
	select:hover {
		border-color: var(--color-border-strong);
	}
	select:focus {
		border-color: var(--color-accent);
	}
	.toggle {
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.2rem 0.45rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.8rem;
	}
	.toggle:hover {
		color: var(--color-text);
		border-color: var(--color-border-strong);
	}
	.desc {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.err {
		color: var(--color-danger);
		font-size: 0.78rem;
	}
	.nested {
		margin-left: var(--space-3);
		padding-left: var(--space-3);
		border-left: 2px solid var(--color-border);
	}
</style>
