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
	import { draft, type PowerDraft } from '$lib/stores/originDraft';
	import PowerEditor from './power/PowerEditor.svelte';

	// ── module-level schema cache ──────────────────────────────────────────────
	// A single in-flight promise shared across tab mounts so we don't refetch
	// when the user toggles tabs. Lives at module scope intentionally.
	let schemaPromise: Promise<{ schema: object; fieldDocs: object; typeOptions: string[] }> | null =
		null;

	function loadSchemas() {
		if (schemaPromise) return schemaPromise;
		schemaPromise = (async () => {
			const [schemaRes, docsRes] = await Promise.all([
				fetch(`${base}/schemas/power.schema.json`),
				fetch(`${base}/schemas/field_docs.json`)
			]);
			if (!schemaRes.ok) throw new Error(`power.schema.json: ${schemaRes.status}`);
			if (!docsRes.ok) throw new Error(`field_docs.json: ${docsRes.status}`);
			const schema = (await schemaRes.json()) as Record<string, unknown>;
			const fieldDocs = (await docsRes.json()) as object;
			const typeProp = (schema.properties as Record<string, unknown> | undefined)?.type as
				| Record<string, unknown>
				| undefined;
			const en = typeProp?.enum;
			const typeOptions = Array.isArray(en) ? en.filter((v): v is string => typeof v === 'string') : [];
			return { schema, fieldDocs, typeOptions };
		})();
		return schemaPromise;
	}

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

	function isCollapsed(i: number, total: number): boolean {
		const explicit = collapsedByIndex[i];
		if (typeof explicit === 'boolean') return explicit;
		// Default: collapsed when there are more than 2 powers.
		return total > 2;
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

	function addPower() {
		const defaultType = schemaState.typeOptions[0] ?? 'neoorigins:attribute_modifier';
		draft.update((d) => {
			const id = nextPowerId(d.powers);
			const next: PowerDraft = { id, type: defaultType, fields: {} };
			return { ...d, powers: [...d.powers, next] };
		});
	}

	function removePower(index: number) {
		draft.update((d) => ({
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
		draft.update((d) => ({
			...d,
			powers: d.powers.map((p, i) => (i === index ? { ...p, id } : p))
		}));
	}

	function updatePowerType(index: number, type: string) {
		// WIPE fields on type change — the new branch's schema usually has a
		// disjoint property set, and silently carrying stale fields produces
		// invalid JSON downstream. The PowerEditor surfaces the inline toast.
		draft.update((d) => ({
			...d,
			powers: d.powers.map((p, i) => (i === index ? { ...p, type, fields: {} } : p))
		}));
	}

	function updatePowerField(index: number, fieldName: string, value: unknown) {
		draft.update((d) => ({
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
		<button
			type="button"
			class="add"
			onclick={addPower}
			disabled={schemaState.status !== 'ready'}
		>
			Add Power
		</button>
	</header>

	{#if schemaState.status === 'loading'}
		<p class="status">Loading power schema…</p>
	{:else if schemaState.status === 'error'}
		<p class="status err">
			Failed to load power schema: {schemaState.error}.<br />
			Confirm <code>static/schemas/power.schema.json</code> is present.
		</p>
	{:else if $draft.powers.length === 0}
		<p class="empty">No powers yet. Click <strong>Add Power</strong> to add your first one.</p>
	{:else}
		<div class="list">
			{#each $draft.powers as power, i (i)}
				<PowerEditor
					{power}
					index={i}
					typeOptions={schemaState.typeOptions}
					schema={schemaState.schema!}
					fieldDocs={schemaState.fieldDocs!}
					collapsed={isCollapsed(i, $draft.powers.length)}
					onToggleCollapsed={() => toggleCollapsed(i, $draft.powers.length)}
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
		gap: 1rem;
	}
	.head {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
	}
	h2 {
		margin: 0;
		color: #e6e6e6;
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
	.add:hover:not(:disabled) {
		background: #4a90e2;
		color: #fff;
	}
	.add:disabled {
		opacity: 0.6;
		cursor: not-allowed;
	}
	.status {
		color: #999;
		font-style: italic;
		margin: 0;
	}
	.status.err {
		color: #e25d4a;
		font-style: normal;
	}
	.status code {
		font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
		font-size: 0.85em;
		color: #c8c8c8;
	}
	.empty {
		color: #999;
		font-style: italic;
		margin: 0;
		padding: 1rem;
		border: 1px dashed #333;
		border-radius: 4px;
		text-align: center;
	}
	.list {
		display: flex;
		flex-direction: column;
		gap: 0.8rem;
	}
</style>
