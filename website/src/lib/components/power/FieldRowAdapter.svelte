<script lang="ts">
	// Bridge between `FieldRow`'s `bind:value` contract and the
	// store-update callback idiom used by the rest of the editor.
	//
	// FieldRow exposes a `$bindable` `value` prop. To keep the store as the
	// single source of truth we mirror the incoming prop into a local
	// `$state` variable, then `$effect` on changes to that mirror and
	// forward to `onUpdate`. When the incoming prop changes (e.g. type
	// switch resets `power.fields[name]` to undefined), we re-sync the
	// mirror without re-firing `onUpdate`.

	import { untrack } from 'svelte';
	import type { FormFieldSpec } from '$lib/schema/FormFieldSpec';
	import { mirrorSeedFor } from '$lib/schema/fieldSeed';
	import FieldRow from '$lib/widgets/FieldRow.svelte';

	let {
		field,
		value,
		onUpdate
	}: {
		field: FormFieldSpec;
		value: unknown;
		onUpdate: (v: unknown) => void;
	} = $props();

	// Unset fields arrive as `undefined`, which Svelte 5 rejects for
	// `bind:value` into a leaf row that declares a `$bindable` fallback. This
	// is the single chokepoint that substitutes a kind-appropriate stand-in —
	// see `mirrorSeedFor` for why BOOLEAN is the one kind that must carry its
	// schema default.
	const coerce = (v: unknown): unknown => (v === undefined ? mirrorSeedFor(field) : v);

	// Local mirror that FieldRow can `bind:value` against — never `undefined`.
	// Initialised inside `untrack` so Svelte doesn't capture a reactive snapshot
	// at module init; the $effect below keeps it in sync.
	let mirror = $state<unknown>(untrack(() => coerce(value)));
	// `lastRaw` tracks the raw incoming prop to detect genuine external changes;
	// `lastSynced` tracks the coerced mirror value so a user edit can be told
	// apart from an external sync (and so coercing an unset field never looks
	// like an edit that should be written back to the store).
	let lastRaw = $state<unknown>(untrack(() => value));
	let lastSynced = $state<unknown>(untrack(() => coerce(value)));

	// External → local sync. Only re-write the mirror when the prop genuinely
	// differs from the last raw snapshot; otherwise we'd thrash and clobber
	// in-progress keystrokes.
	$effect(() => {
		if (value !== untrack(() => lastRaw)) {
			lastRaw = value;
			const next = coerce(value);
			lastSynced = next;
			mirror = next;
		}
	});

	// Local → external sync. Skip the initial run by tracking a `dispatched`
	// flag — the first $effect call is the mount snapshot.
	let dispatched = false;
	$effect(() => {
		const current = mirror;
		if (!dispatched) {
			dispatched = true;
			return;
		}
		if (current !== untrack(() => lastSynced)) {
			lastSynced = current;
			onUpdate(current);
		}
	});
</script>

<FieldRow {field} bind:value={mirror} />
