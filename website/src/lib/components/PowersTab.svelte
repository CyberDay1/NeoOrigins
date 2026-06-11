<script lang="ts">
	// Powers tab — schema-driven power editor. See planning/web_editor_scope.md §3.
	//
	// Responsibilities:
	//   - Fetch `power.schema.json` and `field_docs.json` once on mount and
	//     cache them in a module-level promise so the second tab switch
	//     doesn't refetch.
	//   - Render the list of powers from the `originDraft` store.
	//   - Add / remove powers and propagate id / type / per-field edits via
	//     `draft.update(...)`. Bind:value against the store is intentionally
	//     avoided — matches the IdentityTab idiom.
	//   - On type change, WIPE the `fields` object (clean slate). The
	//     PowerEditor child surfaces a "(form fields reset)" inline toast.

	import { base } from '$app/paths';
	import type { Writable } from 'svelte/store';
	import { draft as originDraftStore, powersView, type PowerDraft } from '$lib/stores/originDraft';
	import { originValidation } from '$lib/stores/originValidation';
	import { setRefSchemas, type RefSchemas } from '$lib/schema/refSchemaContext';
	import PowerEditor from './power/PowerEditor.svelte';
	import BlockCanvas from './power/block/BlockCanvas.svelte';

	// Which draft store backs the powers list. Defaults to the player Origin
	// draft so the Origin editor's `<PowersTab />` is unchanged; the Mob Origin
	// editor passes its own store. Only the `powers` field is touched here, so
	// any store whose value carries `powers: PowerDraft[]` works — the cast
	// preserves the concrete store's other fields at runtime (the `{ ...d }`
	// spread keeps them) while keeping this component store-shape-agnostic.
	let {
		powersStore = originDraftStore as unknown as Writable<{ powers: PowerDraft[] }>
	}: { powersStore?: Writable<{ powers: PowerDraft[] }> } = $props();

	// Live AJV/id validation is computed off the ORIGIN draft store
	// (`originValidation`). When this tab is mounted over a different store
	// (the Mob Origin editor), those issues describe the wrong draft — only
	// wire them through when we're actually editing the origin draft.
	let isOriginDraftStore = $derived(
		(powersStore as unknown) === (originDraftStore as unknown)
	);

	// ── module-level schema cache ──────────────────────────────────────────────
	// A single in-flight promise shared across tab mounts so we don't refetch
	// when the user toggles tabs. Lives at module scope intentionally.
	let schemaPromise: Promise<{
		schema: object;
		fieldDocs: object;
		typeOptions: string[];
		actionSchema: object;
		conditionSchema: object;
		blockConditionSchema: object;
		itemConditionSchema: object;
		itemActionSchema: object;
	}> | null = null;

	function loadSchemas() {
		if (schemaPromise) return schemaPromise;
		schemaPromise = (async () => {
			const [
				schemaRes,
				docsRes,
				actionRes,
				conditionRes,
				blockConditionRes,
				itemConditionRes,
				itemActionRes
			] = await Promise.all([
					fetch(`${base}/schemas/power.schema.json`),
					fetch(`${base}/schemas/field_docs.json`),
					fetch(`${base}/schemas/action.schema.json`),
					fetch(`${base}/schemas/condition.schema.json`),
					fetch(`${base}/schemas/block_condition.schema.json`),
					fetch(`${base}/schemas/item_condition.schema.json`),
					fetch(`${base}/schemas/item_action.schema.json`)
				]);
			if (!schemaRes.ok) throw new Error(`power.schema.json: ${schemaRes.status}`);
			if (!docsRes.ok) throw new Error(`field_docs.json: ${docsRes.status}`);
			if (!actionRes.ok) throw new Error(`action.schema.json: ${actionRes.status}`);
			if (!conditionRes.ok) throw new Error(`condition.schema.json: ${conditionRes.status}`);
			if (!blockConditionRes.ok)
				throw new Error(`block_condition.schema.json: ${blockConditionRes.status}`);
			if (!itemConditionRes.ok)
				throw new Error(`item_condition.schema.json: ${itemConditionRes.status}`);
			if (!itemActionRes.ok)
				throw new Error(`item_action.schema.json: ${itemActionRes.status}`);
			const schema = (await schemaRes.json()) as Record<string, unknown>;
			const fieldDocs = (await docsRes.json()) as object;
			const actionSchema = (await actionRes.json()) as object;
			const conditionSchema = (await conditionRes.json()) as object;
			const blockConditionSchema = (await blockConditionRes.json()) as object;
			const itemConditionSchema = (await itemConditionRes.json()) as object;
			const itemActionSchema = (await itemActionRes.json()) as object;
			const typeProp = (schema.properties as Record<string, unknown> | undefined)?.type as
				| Record<string, unknown>
				| undefined;
			const en = typeProp?.enum;
			const typeOptions = Array.isArray(en) ? en.filter((v): v is string => typeof v === 'string') : [];
			return {
				schema,
				fieldDocs,
				typeOptions,
				actionSchema,
				conditionSchema,
				blockConditionSchema,
				itemConditionSchema,
				itemActionSchema
			};
		})();
		return schemaPromise;
	}

	// Reactive holder published to the recursive RefRow / ArrayRefRow sub-forms.
	// `setContext` must run during init (before any child mounts), so we publish
	// an empty holder now and fill it when the async fetch resolves — the rows
	// only mount once `schemaState.status === 'ready'`, by which point it's set.
	const refSchemas = $state<RefSchemas>({
		action: {},
		condition: {},
		blockCondition: {},
		itemCondition: {},
		itemAction: {},
		fieldDocs: {}
	});
	setRefSchemas(refSchemas);

	let schemaState = $state<{
		status: 'loading' | 'ready' | 'error';
		schema: object | null;
		fieldDocs: object | null;
		typeOptions: string[];
		error: string | null;
	}>({ status: 'loading', schema: null, fieldDocs: null, typeOptions: [], error: null });

	$effect(() => {
		let cancelled = false;
		loadSchemas()
			.then((v) => {
				if (cancelled) return;
				// Fill the context holder so RefRow / ArrayRefRow can resolve
				// nested action/condition types.
				refSchemas.action = v.actionSchema;
				refSchemas.condition = v.conditionSchema;
				refSchemas.blockCondition = v.blockConditionSchema;
				refSchemas.itemCondition = v.itemConditionSchema;
				refSchemas.itemAction = v.itemActionSchema;
				refSchemas.fieldDocs = v.fieldDocs;
				schemaState = {
					status: 'ready',
					schema: v.schema,
					fieldDocs: v.fieldDocs,
					typeOptions: v.typeOptions,
					error: null
				};
			})
			.catch((e) => {
				if (cancelled) return;
				schemaState = {
					status: 'error',
					schema: null,
					fieldDocs: null,
					typeOptions: [],
					error: e instanceof Error ? e.message : String(e)
				};
			});
		return () => {
			cancelled = true;
		};
	});

	// ── collapsed state per power ──────────────────────────────────────────────
	// Tracked locally (UI concern only — not part of the saved draft). Keyed by
	// the power's index slot so adding/removing rows behaves predictably.
	let collapsedByIndex = $state<Record<number, boolean>>({});

	function isCollapsed(i: number, _total: number): boolean {
		const explicit = collapsedByIndex[i];
		if (typeof explicit === 'boolean') return explicit;
		// Default: ALWAYS expanded. The prior "collapse when >2" heuristic
		// hid the form on new powers and made the editor look broken — the
		// caret was the only click target and unclear. Users who want a
		// compact view can collapse explicitly per row.
		return false;
	}

	function toggleCollapsed(i: number, total: number) {
		const current = isCollapsed(i, total);
		collapsedByIndex = { ...collapsedByIndex, [i]: !current };
	}

	// ── add / remove / edit ────────────────────────────────────────────────────

	function nextPowerId(existing: PowerDraft[]): string {
		// `power_1`, `power_2`, … — find the smallest unused suffix to keep
		// ids stable across removals.
		const used = new Set(existing.map((p) => p.id));
		let n = existing.length + 1;
		while (used.has(`power_${n}`)) n += 1;
		// Walk backwards too in case the user deleted earlier ids.
		for (let i = 1; i <= existing.length + 1; i += 1) {
			if (!used.has(`power_${i}`)) return `power_${i}`;
		}
		return `power_${n}`;
	}

	// Preferred default type for a fresh power. The enum is sorted
	// alphabetically, so `typeOptions[0]` lands on a compat-namespace type
	// (`apace:night_vision`) — confusing for new users. Prefer the simple,
	// commonly-used `neoorigins:attribute_modifier`; fall back to the first
	// `neoorigins:` type in the schema, then to whatever the enum offers.
	const PREFERRED_DEFAULT_TYPE = 'neoorigins:attribute_modifier';

	function addPower() {
		const opts = schemaState.typeOptions;
		const defaultType =
			opts.find((t) => t === PREFERRED_DEFAULT_TYPE) ??
			opts.find((t) => t.startsWith('neoorigins:')) ??
			opts[0] ??
			PREFERRED_DEFAULT_TYPE;
		powersStore.update((d) => {
			const id = nextPowerId(d.powers);
			const next: PowerDraft = { id, type: defaultType, fields: {} };
			return { ...d, powers: [...d.powers, next] };
		});
	}

	function removePower(index: number) {
		powersStore.update((d) => ({
			...d,
			powers: d.powers.filter((_, i) => i !== index)
		}));
		// Re-key collapsed map so it stays sane after the splice.
		const next: Record<number, boolean> = {};
		for (const [k, v] of Object.entries(collapsedByIndex)) {
			const ki = Number(k);
			if (ki < index) next[ki] = v;
			else if (ki > index) next[ki - 1] = v;
		}
		collapsedByIndex = next;
	}

	function updatePowerId(index: number, id: string) {
		powersStore.update((d) => ({
			...d,
			powers: d.powers.map((p, i) => (i === index ? { ...p, id } : p))
		}));
	}

	function updatePowerType(index: number, type: string) {
		// WIPE fields on type change — the new branch's schema usually has a
		// disjoint property set, and silently carrying stale fields produces
		// invalid JSON downstream. The PowerEditor surfaces the inline toast.
		powersStore.update((d) => ({
			...d,
			powers: d.powers.map((p, i) => (i === index ? { ...p, type, fields: {} } : p))
		}));
	}

	function updatePowerField(index: number, fieldName: string, value: unknown) {
		powersStore.update((d) => ({
			...d,
			powers: d.powers.map((p, i) =>
				i === index ? { ...p, fields: { ...p.fields, [fieldName]: value } } : p
			)
		}));
	}
</script>

<section aria-labelledby="powers-heading" class="tab">
	<header class="head">
		<h2 id="powers-heading">Powers</h2>
		<div class="head-actions">
			<div class="viewtoggle" role="group" aria-label="Powers editing view">
				<button
					type="button"
					class:active={$powersView === 'form'}
					aria-pressed={$powersView === 'form'}
					onclick={() => powersView.set('form')}
				>
					Form
				</button>
				<button
					type="button"
					class:active={$powersView === 'blocks'}
					aria-pressed={$powersView === 'blocks'}
					onclick={() => powersView.set('blocks')}
				>
					Blocks
				</button>
			</div>
			{#if $powersView === 'form'}
				<button
					type="button"
					class="add"
					onclick={addPower}
					disabled={schemaState.status !== 'ready'}
				>
					Add Power
				</button>
			{/if}
		</div>
	</header>

	{#if schemaState.status === 'loading'}
		<p class="status">Loading power schema…</p>
	{:else if schemaState.status === 'error'}
		<p class="status err">
			Failed to load power schema: {schemaState.error}.<br />
			Confirm <code>static/schemas/power.schema.json</code> is present.
		</p>
	{:else if $powersView === 'blocks'}
		<BlockCanvas powerSchema={schemaState.schema!} {refSchemas} {powersStore} />
	{:else if $powersStore.powers.length === 0}
		<p class="empty">No powers yet. Click <strong>Add Power</strong> to add your first one.</p>
	{:else}
		<div class="list">
			{#each $powersStore.powers as power, i (i)}
				<PowerEditor
					{power}
					index={i}
					typeOptions={schemaState.typeOptions}
					schema={schemaState.schema!}
					fieldDocs={schemaState.fieldDocs!}
					issues={isOriginDraftStore ? $originValidation.powers[i] : undefined}
					collapsed={isCollapsed(i, $powersStore.powers.length)}
					onToggleCollapsed={() => toggleCollapsed(i, $powersStore.powers.length)}
					onIdChange={(v) => updatePowerId(i, v)}
					onTypeChange={(v) => updatePowerType(i, v)}
					onFieldChange={(name, v) => updatePowerField(i, name, v)}
					onRemove={() => removePower(i)}
				/>
			{/each}
		</div>
	{/if}
</section>

<style>
	.tab {
		display: flex;
		flex-direction: column;
		gap: var(--space-4);
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: var(--space-3);
	}
	.head-actions {
		display: flex;
		align-items: center;
		gap: var(--space-2);
		flex-wrap: wrap;
	}
	.viewtoggle {
		display: inline-flex;
		gap: 2px;
		padding: 3px;
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
	}
	.viewtoggle button {
		padding: 0.35rem 0.8rem;
		background: transparent;
		color: var(--color-text-muted);
		border: none;
		border-radius: var(--radius-sm);
		cursor: pointer;
		font: inherit;
		font-size: 0.82rem;
		font-weight: 500;
		transition: background 120ms ease, color 120ms ease;
	}
	.viewtoggle button:hover {
		color: var(--color-text);
	}
	.viewtoggle button.active {
		color: var(--color-text);
		background: var(--color-surface);
		box-shadow: var(--shadow-sm);
	}
	h2 {
		margin: 0;
		color: var(--color-text);
		font-size: 1.05rem;
		font-weight: 600;
		letter-spacing: -0.01em;
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
		transition: background 120ms ease, border-color 120ms ease;
	}
	.add:hover:not(:disabled) {
		background: var(--color-accent-hover);
		border-color: var(--color-accent-hover);
	}
	.add:disabled {
		opacity: 0.55;
		cursor: not-allowed;
	}
	.status {
		color: var(--color-text-muted);
		font-style: italic;
		margin: 0;
	}
	.status.err {
		color: var(--color-danger);
		font-style: normal;
	}
	.status code {
		font-family: var(--font-mono);
		font-size: 0.85em;
		color: var(--color-text);
		background: var(--color-bg-subtle);
		border: 1px solid var(--color-border);
		border-radius: var(--radius-sm);
		padding: 0.05rem 0.3rem;
	}
	.empty {
		color: var(--color-text-muted);
		font-style: italic;
		margin: 0;
		padding: var(--space-5);
		border: 1px dashed var(--color-border);
		border-radius: var(--radius-md);
		text-align: center;
	}
	.list {
		display: flex;
		flex-direction: column;
		gap: var(--space-3);
	}
</style>
