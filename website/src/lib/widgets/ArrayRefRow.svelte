<script lang="ts">
	// Add/remove list of action/condition sub-forms (D4) — an `array` whose
	// items `$ref` a sibling action/condition schema (e.g. the `actions` list on
	// `neoorigins:and`). Each element is rendered through FieldRowAdapter with a
	// synthesized per-index REF spec, so FieldRow → RefRow handles the recursive
	// editing and the bind↔callback bridge; every edit bubbles up as a NEW array
	// reference (the adapter only re-fires on reference change). The bound value
	// is an array of nested action/condition OBJECTs.
	//
	// `field.scalarOrArray` fields (the Apoli "one or many" idiom) bind a BARE
	// object when there is exactly one picked entry, so a round-trip of an
	// existing power JSON — which writes the scalar form — comes back byte-equal.
	// `asRefList` widens whatever arrives into the list this row edits;
	// `fromRefList` narrows it again on every mutation.

	import type { ArrayRefFieldSpec, RefFieldSpec } from '$lib/schema/FormFieldSpec';
	import { asRefList, fromRefList } from '$lib/schema/FormFieldSpec';
	import FieldRowAdapter from '$lib/components/power/FieldRowAdapter.svelte';

	let {
		field,
		value = $bindable()
	}: { field: ArrayRefFieldSpec; value: unknown } = $props();

	const items = $derived(asRefList(value));

	// Remembered authoring shape, sampled once from however the value first
	// arrived: unset (a brand-new field) and bare-object both count as scalar,
	// an array does not. Re-saving a field the author wrote as an array must not
	// rewrite it to the scalar form, which is why this is sticky rather than
	// re-derived from `value` — once we narrow to a bare object, re-deriving
	// would read back "scalar" and the original shape would be lost.
	let scalarShaped = !Array.isArray(value);

	const id = $derived(`arr-${field.path.replace(/[^a-zA-Z0-9_-]/g, '-')}`);

	function elemField(i: number): RefFieldSpec {
		return {
			path: `${field.path}/${i}`,
			name: String(i),
			label: `#${i + 1}`,
			description: '',
			required: false,
			kind: 'REF',
			refDoc: field.refDoc
		};
	}

	function commit(next: unknown[]) {
		value = fromRefList(field, next, scalarShaped);
	}

	function addElem() {
		commit([...items, null]);
	}

	function setElem(i: number, v: unknown) {
		commit(items.map((el, j) => (j === i ? v : el)));
	}

	function removeElem(i: number) {
		commit(items.filter((_, j) => j !== i));
	}
</script>

<div class="arr">
	<div class="arr-head">
		<span class="lbl" {id}>
			{field.label}
			{#if field.required}<span class="req" aria-label="required">*</span>{/if}
			<span class="kind">[{field.refDoc}[]]</span>
		</span>
		<button type="button" class="add" onclick={addElem}>+ Add</button>
	</div>

	{#if field.description}
		<small class="desc">{field.description}</small>
	{/if}

	{#if items.length === 0}
		<small class="empty">None yet.</small>
	{:else}
		<div class="nested" role="group" aria-labelledby={id}>
			{#each items as item, i (i)}
				<div class="elem">
					<button
						type="button"
						class="remove"
						onclick={() => removeElem(i)}
						aria-label={`Remove #${i + 1}`}
						title="Remove"
					>
						×
					</button>
					<div class="elem-body">
						<FieldRowAdapter
							field={elemField(i)}
							value={item}
							onUpdate={(v) => setElem(i, v)}
						/>
					</div>
				</div>
			{/each}
		</div>
	{/if}
</div>

<style>
	.arr {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		padding: 0.5rem 0;
		border-bottom: 1px solid var(--color-border);
	}
	.arr-head {
		display: flex;
		align-items: center;
		gap: var(--space-2);
	}
	.lbl {
		color: var(--color-text);
		font-size: 0.85rem;
		font-weight: 500;
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
	.add {
		background: var(--color-accent);
		color: var(--color-accent-contrast);
		border: 1px solid var(--color-accent);
		border-radius: var(--radius-sm);
		padding: 0.25rem 0.6rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.78rem;
		font-weight: 500;
	}
	.add:hover {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}
	.desc {
		color: var(--color-text-subtle);
		font-size: 0.78rem;
	}
	.empty {
		color: var(--color-text-muted);
		font-style: italic;
		font-size: 0.78rem;
	}
	.nested {
		display: flex;
		flex-direction: column;
		gap: var(--space-1);
		margin-left: var(--space-3);
		padding-left: var(--space-3);
		border-left: 2px solid var(--color-border);
	}
	.elem {
		display: flex;
		align-items: flex-start;
		gap: var(--space-2);
	}
	.elem-body {
		flex: 1;
		min-width: 0;
	}
	.remove {
		background: transparent;
		color: var(--color-text-muted);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.15rem 0.4rem;
		cursor: pointer;
		font: inherit;
		font-size: 0.9rem;
		line-height: 1;
		margin-top: 0.5rem;
	}
	.remove:hover {
		background: var(--color-danger-subtle);
		border-color: var(--color-danger);
		color: var(--color-danger);
	}
</style>
