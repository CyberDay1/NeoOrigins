<script lang="ts">
	// Scratch/Snap!-style visual editor for the powers array, built on Blockly
	// (Zelos renderer = rounded, interlocking blocks). Block definitions are
	// code-generated from the SAME FieldSpec registry the form editor uses
	// (`blockly/blockRegistry`), so every power/action/condition type is a block
	// whose inputs mirror its schema. Edits flow workspace → `draft.powers` via
	// the serializer (`blockly/blockState`); the workspace is loaded once from
	// the store on mount and is the source of truth while this view is shown.

	import { onMount } from 'svelte';
	import { get } from 'svelte/store';
	import { draft } from '$lib/stores/originDraft';
	import type { RefSchemas } from '$lib/schema/refSchemaContext';
	import { buildBlockRegistry, type BlockRegistry } from '$lib/blockly/blockRegistry';
	import { draftToState, stateToDraft, powerBlockId } from '$lib/blockly/blockState';

	let { powerSchema, refSchemas }: { powerSchema: object; refSchemas: RefSchemas } = $props();

	let host: HTMLDivElement;
	let status = $state<'init' | 'ready' | 'error'>('init');
	let errorMsg = $state('');
	// Powers whose type isn't in the schema (custom packs) can't be drawn as
	// blocks. We preserve them untouched on save and warn rather than drop them.
	let unsupported = $state<string[]>([]);

	onMount(() => {
		let workspace: import('blockly').WorkspaceSvg | null = null;
		let disposed = false;
		let saveTimer: ReturnType<typeof setTimeout> | null = null;
		let loading = false;

		(async () => {
			try {
				const Blockly = await import('blockly');
				if (disposed) return;

				const reg: BlockRegistry = buildBlockRegistry(powerSchema, refSchemas);
				// Definitions are static per session — register once globally.
				Blockly.common.defineBlocksWithJsonArray(reg.defs);

				workspace = Blockly.inject(host, {
					toolbox: reg.toolbox as import('blockly').utils.toolbox.ToolboxDefinition,
					renderer: 'zelos',
					theme: Blockly.Themes.Classic,
					trashcan: true,
					zoom: { controls: true, wheel: true, startScale: 0.85, minScale: 0.4, maxScale: 1.5 },
					move: { scrollbars: true, drag: true, wheel: false },
					grid: { spacing: 24, length: 2, colour: 'rgba(255,255,255,0.05)', snap: false }
				});

				const initialPowers = get(draft).powers;
				// Off-schema power types can't be rendered — set them aside so the
				// canvas neither shows nor clobbers them, and re-attach on save.
				const preserved = initialPowers.filter((p) => !reg.blockTypeForId.has(p.type));
				unsupported = preserved.map((p) => p.id || p.type);

				// Renderable powers can still carry fields the schema doesn't model
				// (legacy-alias types whose runtime fields have no schema branch).
				// Snapshot each power's original fields keyed by its deterministic
				// block id so the serializer can overlay modeled edits onto them
				// rather than rebuilding from scratch and dropping the rest. Index
				// must match draftToState's enumeration of the same array.
				const preserveByBlockId = new Map<string, Record<string, unknown>>();
				initialPowers.forEach((p, i) => {
					if (reg.blockTypeForId.has(p.type)) {
						preserveByBlockId.set(powerBlockId(i), p.fields ?? {});
					}
				});

				// Initial load from the current draft.
				loading = true;
				Blockly.serialization.workspaces.load(
					draftToState(reg, get(draft).powers) as object,
					workspace
				);
				loading = false;
				status = 'ready';

				const pushToStore = () => {
					if (!workspace) return;
					const ws = Blockly.serialization.workspaces.save(workspace) as Parameters<
						typeof stateToDraft
					>[1];
					const powers = stateToDraft(reg, ws, preserveByBlockId);
					draft.update((d) => ({ ...d, powers: [...powers, ...preserved] }));
				};

				workspace.addChangeListener((event: import('blockly').Events.Abstract) => {
					if (loading || event.isUiEvent) return;
					if (saveTimer) clearTimeout(saveTimer);
					saveTimer = setTimeout(pushToStore, 250);
				});
			} catch (e) {
				errorMsg = e instanceof Error ? e.message : String(e);
				status = 'error';
			}
		})();

		return () => {
			disposed = true;
			if (saveTimer) clearTimeout(saveTimer);
			workspace?.dispose();
		};
	});
</script>

<div class="canvas-wrap">
	{#if status === 'error'}
		<p class="err">Could not start the block editor: {errorMsg}</p>
	{/if}
	{#if unsupported.length > 0}
		<p class="warn">
			{unsupported.length}
			{unsupported.length === 1 ? 'power uses' : 'powers use'} a type not in the schema and can't be
			shown as blocks ({unsupported.join(', ')}). They're preserved unchanged — edit them in the Form
			or JSON Preview tab.
		</p>
	{/if}
	<div class="canvas" bind:this={host}></div>
	{#if status === 'ready'}
		<p class="hint">
			Drag blocks from the palette. Powers are standalone stacks; conditions plug into
			hexagon slots; actions snap into the C-shaped mouths. Edits sync to the JSON Preview tab.
		</p>
	{/if}
</div>

<style>
	.canvas-wrap {
		display: flex;
		flex-direction: column;
		gap: var(--space-2);
	}
	.canvas {
		width: 100%;
		height: 70vh;
		min-height: 460px;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		overflow: hidden;
		background: var(--color-bg);
	}
	.hint {
		margin: 0;
		color: var(--color-text-muted);
		font-size: 0.78rem;
	}
	.err {
		margin: 0;
		color: var(--color-danger);
		font-size: 0.85rem;
	}
	.warn {
		margin: 0;
		padding: var(--space-2) var(--space-3);
		background: var(--color-warning-subtle);
		border: 1px solid color-mix(in srgb, var(--color-warning) 40%, var(--color-border));
		border-radius: var(--radius-md);
		color: var(--color-text);
		font-size: 0.8rem;
	}
	/* Blockly injects fixed-position widgets (flyout, dropdowns) — make sure our
	 * dark surface doesn't bleed into their text. Scoped tweaks only. */
	:global(.blocklyToolboxDiv) {
		background: var(--color-surface);
	}
</style>
