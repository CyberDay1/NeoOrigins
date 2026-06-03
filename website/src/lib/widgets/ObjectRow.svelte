<script lang="ts">
	// Inline sub-form for a nested object with a FIXED set of sub-fields
	// (schema `type:object` with inline `properties`) — e.g. an item stack
	// (`{item, count}`), an effect instance, or the `resource` power's
	// `hud_render` block. TS port of the in-game `ObjectRow`.
	//
	// Unlike RefRow there is NO type to pick: `field.children` is the fixed
	// sub-field list, rendered inline through FieldRowAdapter → FieldRow (which
	// dispatches nested OBJECT / REF / ARRAY_REF back through the same path).
	// The bound value is the nested OBJECT (`{…}`) — not a stringified blob —
	// so it serializes straight into the wire JSON.

	import type { ObjectFieldSpec } from '$lib/schema/FormFieldSpec';
	import FieldRowAdapter from '$lib/components/power/FieldRowAdapter.svelte';

	let {
		field,
		value = $bindable()
	}: { field: ObjectFieldSpec; value: Record<string, unknown> | null } = $props();

	let collapsed = $state(false);

	const id = $derived(`obj-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);

	function childValue(name: string): unknown {
		return value ? value[name] : undefined;
	}

	function updateChild(name: string, v: unknown) {
		value = { ...(value ?? {}), [name]: v };
	}
</script>

<div class="obj">
	<div class="obj-head">
		<span class="lbl" {id}>
			{field.label}
			{#if field.required}<span class="req" aria-label="required">*</span>{/if}
			<span class="kind">[object]</span>
		</span>
		<button
			type="button"
			class="toggle"
			aria-expanded={!collapsed}
			aria-controls={`${id}-body`}
			onclick={() => (collapsed = !collapsed)}
			title={collapsed ? 'Expand' : 'Collapse'}
		>
			{collapsed ? '▸' : '▾'}
		</button>
	</div>

	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}

	{#if !collapsed}
		<div class="nested" id={`${id}-body`} role="group" aria-labelledby={id}>
			{#each field.children as f (f.path)}
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
	.obj {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
	}
	.obj-head {
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
	.nested {
		margin-left: var(--space-3);
		padding-left: var(--space-3);
		border-left: 2px solid var(--color-border);
	}
</style>
